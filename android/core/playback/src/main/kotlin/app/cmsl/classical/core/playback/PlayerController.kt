package app.cmsl.classical.core.playback

import app.cmsl.classical.core.model.RepeatMode
import kotlinx.coroutines.flow.StateFlow

/** 画面から再生を操作する窓口。実装は [MediaControllerPlayer]（テストではフェイクを使う）。 */
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
