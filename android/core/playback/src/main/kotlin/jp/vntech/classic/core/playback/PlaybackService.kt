package jp.vntech.classic.core.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * バックグラウンド再生（SPEC §5）。ロック画面と通知は MediaSession の標準の通知が出す。
 * - 割り込み: ExoPlayer の音声フォーカス処理（handleAudioFocus）に任せる
 * - イヤホンを外したとき: setHandleAudioBecomingNoisy で一時停止
 * - 次の曲はプレイリストとして先読みされる（同じ作品の楽章の途切れを小さくする）
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** サービスの終了後も保存を終わらせるためのスコープ。 */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var env: PlaybackEnvironment
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val errorPolicy = ErrorSkipPolicy()
    private var saveJob: Job? = null
    private var tickJob: Job? = null
    private var startedMediaId: String? = null
    private var artworkJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        env = (application as PlaybackEnvironment.Provider).playbackEnvironment
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(MediaCache.dataSourceFactory(this)))
            .build()
        player.addListener(PlayerListener())
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .apply { launchIntent()?.let(::setSessionActivity) }
            .build()
        scope.launch { env.trackChanges.collect(::refreshQueueItems) }
    }

    /**
     * ダウンロードが終わった・消えた曲を、キューの中で新しい URI に差し替える（SPEC §5.1）。
     * 再生中の曲は途切れさせないため、そのままにする（次に再生するときから使う）。
     */
    private suspend fun refreshQueueItems(trackId: String) {
        val fresh = env.resolve(listOf(trackId)).firstOrNull() ?: return
        val stale = (0 until player.mediaItemCount).filter { i ->
            val item = player.getMediaItemAt(i)
            i != player.currentMediaItemIndex &&
                item.mediaId == fresh.id &&
                item.localConfiguration?.uri?.toString() != fresh.audioUrl
        }
        stale.forEach { i ->
            val metadata = player.getMediaItemAt(i).mediaMetadata
            player.replaceMediaItem(i, fresh.toMediaItem().buildUpon().setMediaMetadata(metadata).build())
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveNow()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        saveNow()
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun launchIntent(): PendingIntent? = packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun saveNow() {
        if (player.mediaItemCount == 0) return
        val snapshot = player.snapshot()
        saveScope.launch { env.saveQueue(snapshot) }
    }

    /** 状態が変わるたびに書かず、少し待ってまとめて書く。 */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveNow()
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                scheduleSave()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            startedMediaId = null
            if (player.isPlaying) recordStarted()
            updateArtwork()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            tickJob?.cancel()
            if (isPlaying) {
                recordStarted()
                // 再生中も位置をときどき保存する（強制終了されても大きく戻らないように）
                tickJob = scope.launch {
                    while (isActive) {
                        delay(SAVE_INTERVAL_MS)
                        saveNow()
                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // 「この曲の終わりまで」で止まった。1 回きりなので解除する（▶ で次の曲から）
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) player.pauseAtEndOfMediaItems = false
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) errorPolicy.onReady()
        }

        override fun onPlayerError(error: PlaybackException) {
            when (errorPolicy.onError(hasNext = player.hasNextMediaItem())) {
                ErrorSkipPolicy.Decision.Skip -> {
                    toast(R.string.playback_error_skipped)
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
                ErrorSkipPolicy.Decision.Stop -> {
                    toast(R.string.playback_error_stopped)
                    player.pause()
                }
            }
        }

        private fun recordStarted() {
            val id = player.currentMediaItem?.mediaId ?: return
            if (id == startedMediaId) return
            startedMediaId = id
            saveScope.launch { env.onTrackStarted(id) }
        }
    }

    /**
     * 今の曲のアートワークを、取得した肖像画に差し替える（ロック画面・通知。SPEC §4.10）。
     * 同じ URI の MediaItem への置き換えなので、再生は途切れない。同じ画像なら何もしない（置き換えの繰り返しを防ぐ）。
     */
    private fun updateArtwork() {
        val id = player.currentMediaItem?.mediaId ?: return
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val data = env.artwork(id) ?: return@launch
            val index = player.currentMediaItemIndex
            val item = player.currentMediaItem ?: return@launch
            if (item.mediaId != id || item.mediaMetadata.artworkData?.contentEquals(data) == true) return@launch
            val metadata = item.mediaMetadata.buildUpon()
                .setArtworkData(data, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()
            player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(metadata).build())
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommands.STOP_AT_END_OF_TRACK)
                    .build(),
            )
            .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != SessionCommands.STOP_AT_END_OF_TRACK.customAction) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            player.pauseAtEndOfMediaItems = args.getBoolean(SessionCommands.KEY_ENABLED)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = scope.future {
            env.resolve(mediaItems.map { it.mediaId }).map { it.toMediaItem() }
        }

        /** 通知やシステムの「再生を再開」から、保存しておいたキューで再開する。 */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
            val saved = env.loadQueue()
            val available = saved?.let { s -> env.resolve(s.trackIds).associateBy { it.id } }.orEmpty()
            val snapshot = saved?.restorable(available::containsKey)
                ?: throw UnsupportedOperationException("復元するキューがない")
            player.shuffleModeEnabled = snapshot.shuffle
            player.repeatMode = snapshot.repeatMode.toPlayer()
            MediaItemsWithStartPosition(
                snapshot.trackIds.map { available.getValue(it).toMediaItem() },
                snapshot.index,
                snapshot.positionMs,
            )
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 1_000L
        const val SAVE_INTERVAL_MS = 15_000L
    }
}
