package jp.vntech.classic.core.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    /** 再生の操作を記録するフェイク。 */
    private class FakePlayer : PlayerController {
        override val state = MutableStateFlow(PlayerState(queue = listOf("1", "2"), currentIndex = 0, isPlaying = true))
        val volumes = mutableListOf<Float>()
        override var volume = 1f
            private set
        var stopAtEnd = false
        override val stoppedAtEndOfTrack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override fun pause() {
            state.value = state.value.copy(isPlaying = false)
        }

        override fun setVolume(volume: Float) {
            this.volume = volume
            volumes += volume
        }

        override fun setStopAtEndOfTrack(enabled: Boolean) {
            stopAtEnd = enabled
        }

        override fun play(queue: PlayQueue) = Unit
        override fun togglePlayPause() = Unit
        override fun skipNext() = Unit
        override fun skipPrevious() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setShuffle(enabled: Boolean) = Unit
        override fun cycleRepeatMode() = Unit
        override fun playNext(trackId: String) = Unit
        override fun addToQueue(trackId: String) = Unit
        override fun moveQueueItem(from: Int, to: Int) = Unit
        override fun removeQueueItem(index: Int) = Unit
        override fun skipToQueueItem(index: Int) = Unit
    }

    private val player = FakePlayer()

    /** backgroundScope の処理は advanceUntilIdle では進まないので、十分に時間を進める。 */
    private fun TestScope.finishAll() {
        advanceTimeBy(2 * 60 * MINUTE)
        runCurrent()
    }

    private fun TestScope.timer() = SleepTimer(player, backgroundScope, clock = { testScheduler.currentTime })

    @Test
    fun `時間になったら音量を下げてから一時停止し、音量を戻す`() = runTest {
        val timer = timer()
        timer.start(15)
        assertEquals(15 * MINUTE, timer.remainingMs())

        advanceTimeBy(10 * MINUTE)
        assertEquals(5 * MINUTE, timer.remainingMs())
        assertTrue(player.state.value.isPlaying)

        advanceTimeBy(5 * MINUTE + SleepTimer.FADE_MS / 2)
        runCurrent()
        // 音量を下げている途中
        assertTrue(player.state.value.isPlaying)
        assertTrue(player.volume < 1f && player.volume > 0f)

        finishAll()
        assertFalse(player.state.value.isPlaying)
        assertEquals(1f, player.volume)
        assertEquals(0f, player.volumes.dropLast(1).last())
        assertTrue(player.volumes.dropLast(1).zipWithNext().all { (a, b) -> b < a })
        assertEquals(SleepMode.Off, timer.mode.value)
        assertNull(timer.remainingMs())
    }

    @Test
    fun `時間になったときに止まっていれば何もしない`() = runTest {
        val timer = timer()
        timer.start(30)
        player.pause()
        finishAll()
        assertEquals(emptyList<Float>(), player.volumes.filter { it != 1f })
        assertEquals(SleepMode.Off, timer.mode.value)
    }

    @Test
    fun `取り消すと再生を続け、音量を下げている途中なら戻す`() = runTest {
        val timer = timer()
        timer.start(15)
        timer.cancel()
        finishAll()
        assertTrue(player.state.value.isPlaying)
        assertEquals(SleepMode.Off, timer.mode.value)

        timer.startAfter(MINUTE)
        advanceTimeBy(MINUTE + SleepTimer.FADE_MS / 2)
        runCurrent()
        timer.cancel()
        runCurrent()
        assertTrue(player.state.value.isPlaying)
        assertEquals(1f, player.volume)
    }

    @Test
    fun `設定し直すと前のタイマーは働かない`() = runTest {
        val timer = timer()
        timer.start(15)
        advanceTimeBy(10 * MINUTE)
        timer.start(30)
        advanceTimeBy(20 * MINUTE)
        assertTrue(player.state.value.isPlaying)
        assertEquals(10 * MINUTE, timer.remainingMs())
        assertTrue(timer.mode.value is SleepMode.Until)
    }

    @Test
    fun `この曲の終わりまで 曲の終わりで止まったら解除する`() = runTest {
        val timer = timer()
        timer.stopAtEndOfTrack()
        runCurrent()
        assertTrue(player.stopAtEnd)
        assertEquals(SleepMode.EndOfTrack, timer.mode.value)
        assertNull(timer.remainingMs())

        player.stoppedAtEndOfTrack.emit(Unit)
        runCurrent()
        assertEquals(SleepMode.Off, timer.mode.value)
    }

    @Test
    fun `この曲の終わりまでを取り消す・時間に切り替えると、曲の終わりで止めない`() = runTest {
        val timer = timer()
        timer.stopAtEndOfTrack()
        timer.cancel()
        assertFalse(player.stopAtEnd)

        timer.stopAtEndOfTrack()
        timer.start(45)
        assertFalse(player.stopAtEnd)
        assertEquals(45 * MINUTE, timer.remainingMs())
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
