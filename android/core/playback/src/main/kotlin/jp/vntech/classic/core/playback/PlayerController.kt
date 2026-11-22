package jp.vntech.classic.core.playback

import jp.vntech.classic.core.model.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 画面から再生を操作する窓口。実装は [MediaControllerPlayer]（テストではフェイクを使う）。 */
@Suppress("TooManyFunctions") // 画面から使う再生の操作をまとめた窓口
interface PlayerController {
    val state: StateFlow<PlayerState>

    fun play(queue: PlayQueue)

    fun togglePlayPause()

    fun skipNext()

    fun skipPrevious()

    fun seekTo(positionMs: Long)

    fun setShuffle(enabled: Boolean)

    fun cycleRepeatMode()

    /** 今の曲の次に入れる。 */
    fun playNext(trackId: String)

    /** キューの最後に入れる。 */
    fun addToQueue(trackId: String)

    fun moveQueueItem(from: Int, to: Int)

    fun removeQueueItem(index: Int)

    fun skipToQueueItem(index: Int)

    /** 一時停止（再生中でなければ何もしない）。 */
    fun pause()

    /** プレイヤーの音量（0〜1。端末の音量とは別）。スリープタイマーで下げるときだけ使う。 */
    val volume: Float

    fun setVolume(volume: Float)

    /** 今の曲が終わったら一時停止し、次の曲に進まない（スリープタイマーの「この曲の終わりまで」。SPEC §5.2）。 */
    fun setStopAtEndOfTrack(enabled: Boolean)

    /** [setStopAtEndOfTrack] で曲の終わりに止まった（1 回止まると解除される）。 */
    val stoppedAtEndOfTrack: Flow<Unit>
}

data class PlayerState(
    val queue: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    /** 不明なら 0。 */
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
) {
    val currentTrackId: String? get() = queue.getOrNull(currentIndex)
}
