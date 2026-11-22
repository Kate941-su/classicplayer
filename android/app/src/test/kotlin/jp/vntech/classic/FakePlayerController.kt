package jp.vntech.classic

import jp.vntech.classic.core.playback.PlayQueue
import jp.vntech.classic.core.playback.PlayerController
import jp.vntech.classic.core.playback.PlayerState
import jp.vntech.classic.core.playback.next
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 再生の要求を記録するだけのプレイヤー。 */
class FakePlayerController : PlayerController {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state
    val played = mutableListOf<PlayQueue>()

    override fun play(queue: PlayQueue) {
        played += queue
        _state.value = PlayerState(queue = queue.trackIds, currentIndex = queue.startIndex, isPlaying = true)
    }

    override fun togglePlayPause() {
        _state.value = _state.value.copy(isPlaying = !_state.value.isPlaying)
    }

    override fun skipNext() = Unit

    override fun skipPrevious() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun setShuffle(enabled: Boolean) {
        _state.value = _state.value.copy(shuffle = enabled)
    }

    override fun cycleRepeatMode() {
        _state.value = _state.value.copy(repeatMode = _state.value.repeatMode.next())
    }

    override fun playNext(trackId: String) = Unit

    override fun addToQueue(trackId: String) = Unit

    override fun moveQueueItem(from: Int, to: Int) = Unit

    override fun removeQueueItem(index: Int) = Unit

    override fun skipToQueueItem(index: Int) = Unit

    override fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
    }

    override var volume: Float = 1f
        private set

    override fun setVolume(volume: Float) {
        this.volume = volume
    }

    var stopAtEndOfTrack = false
        private set

    override fun setStopAtEndOfTrack(enabled: Boolean) {
        stopAtEndOfTrack = enabled
    }

    override val stoppedAtEndOfTrack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
