package jp.vntech.classic.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [PlaybackService] の MediaSession に MediaController でつなぐ。Activity の onStart / onStop で
 * [connect] / [disconnect] を呼ぶ。つながる前の操作は、つながってから実行する。
 *
 * 最初につないだとき、プレイヤーが空なら保存しておいたキューと位置を復元する（自動では再生しない。SPEC §5）。
 */
@OptIn(UnstableApi::class)
class MediaControllerPlayer(
    context: Context,
    private val env: PlaybackEnvironment,
) : PlayerController {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private var restored = false
    private var tickJob: Job? = null

    private val endOfTrack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val stoppedAtEndOfTrack: Flow<Unit> = endOfTrack.asSharedFlow()

    /** Activity が表示されている（onStart〜onStop）。 */
    private var started = false

    /** 「この曲の終わりまで」の間は、画面を消してもつないだままにする（止まったことを受け取るため）。 */
    private var stopAtEnd = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && stopAtEnd) {
                stopAtEnd = false
                endOfTrack.tryEmit(Unit)
                if (!started) release()
            }
        }
    }

    /** Activity の onStart で呼ぶ。 */
    fun connect() {
        started = true
        open()
    }

    /** Activity の onStop で呼ぶ。 */
    fun disconnect() {
        started = false
        if (!stopAtEnd) release()
    }

    private fun open() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            if (future !== f || f.isCancelled) return@addListener
            val c = f.get()
            controller = c
            c.addListener(listener)
            restoreIfEmpty(c)
            while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
            publish()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun release() {
        tickJob?.cancel()
        controller?.removeListener(listener)
        future?.let(MediaController::releaseFuture)
        future = null
        controller = null
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pending.addLast(action).also { open() }
    }

    private fun restoreIfEmpty(c: MediaController) {
        if (restored) return
        restored = true
        if (c.mediaItemCount > 0) return
        scope.launch {
            val saved = env.loadQueue() ?: return@launch
            val available = env.resolve(saved.trackIds).mapTo(HashSet()) { it.id }
            val snapshot = saved.restorable(available::contains) ?: return@launch
            if (c.mediaItemCount > 0 || controller !== c) return@launch
            c.setMediaItems(snapshot.trackIds.map(::mediaItemOf), snapshot.index, snapshot.positionMs)
            c.shuffleModeEnabled = snapshot.shuffle
            c.repeatMode = snapshot.repeatMode.toPlayer()
        }
    }

    private fun publish() {
        val c = controller ?: return
        _state.value = PlayerState(
            queue = c.queueIds(),
            currentIndex = if (c.mediaItemCount == 0) -1 else c.currentMediaItemIndex,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = repeatModeOf(c.repeatMode),
        )
        if (c.isPlaying && tickJob?.isActive != true) {
            tickJob = scope.launch {
                while (isActive && controller?.isPlaying == true) {
                    delay(POSITION_TICK_MS)
                    publish()
                }
            }
        }
    }

    override fun play(queue: PlayQueue) {
        if (queue.trackIds.isEmpty()) return
        withController { c ->
            // 作品順などの並びを崩さないよう、シャッフルを切ってから入れる
            c.shuffleModeEnabled = false
            c.setMediaItems(queue.trackIds.map(::mediaItemOf), queue.startIndex, 0)
            c.prepare()
            c.play()
        }
    }

    override fun togglePlayPause() = withController { Util.handlePlayPauseButtonAction(it) }

    override fun skipNext() = withController { it.seekToNext() }

    override fun skipPrevious() = withController { it.seekToPrevious() }

    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    override fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    override fun cycleRepeatMode() = withController { it.repeatMode = repeatModeOf(it.repeatMode).next().toPlayer() }

    override fun playNext(trackId: String) = withController { c ->
        if (c.mediaItemCount == 0) {
            play(PlayQueue(listOf(trackId)))
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, mediaItemOf(trackId))
        }
    }

    override fun addToQueue(trackId: String) = withController { c ->
        if (c.mediaItemCount == 0) play(PlayQueue(listOf(trackId))) else c.addMediaItem(mediaItemOf(trackId))
    }

    override fun moveQueueItem(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    override fun removeQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    override fun skipToQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    override fun pause() = withController { it.pause() }

    override val volume: Float get() = controller?.volume ?: 1f

    override fun setVolume(volume: Float) = withController { it.volume = volume.coerceIn(0f, 1f) }

    override fun setStopAtEndOfTrack(enabled: Boolean) {
        stopAtEnd = enabled
        withController { c ->
            c.sendCustomCommand(SessionCommands.STOP_AT_END_OF_TRACK, bundleOf(SessionCommands.KEY_ENABLED to enabled))
        }
        if (!enabled && !started) release()
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
    }
}
