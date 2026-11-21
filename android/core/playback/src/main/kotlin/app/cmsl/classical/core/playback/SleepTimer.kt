package app.cmsl.classical.core.playback

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

sealed interface SleepMode {
    data object Off : SleepMode

    /** [endAtMs]（[SleepTimer] の時計）に止める。 */
    data class Until(val endAtMs: Long) : SleepMode

    data object EndOfTrack : SleepMode
}

/**
 * スリープタイマー（SPEC §5.2）。時間になったら、再生中なら約 10 秒かけて音量を下げてから一時停止し、音量を戻す。
 * 止まっていれば何もしない。一時停止していても時間は進む。「この曲の終わりまで」は今の曲が終わったところで止め、
 * 次の曲に進まない。保存はしない（アプリを終了すると消える）。
 */
class SleepTimer(
    private val player: PlayerController,
    private val scope: CoroutineScope,
    private val fadeMs: Long = FADE_MS,
    /** 端末の時刻の変更に影響されない時計（スリープ中も進む）。 */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val mutableMode = MutableStateFlow<SleepMode>(SleepMode.Off)
    val mode: StateFlow<SleepMode> = mutableMode.asStateFlow()

    private var job: Job? = null

    init {
        scope.launch {
            player.stoppedAtEndOfTrack.collect {
                if (mutableMode.value == SleepMode.EndOfTrack) mutableMode.value = SleepMode.Off
            }
        }
    }

    /** 残り時間（時間で止めるときだけ）。 */
    fun remainingMs(now: Long = clock()): Long? =
        (mode.value as? SleepMode.Until)?.let { (it.endAtMs - now).coerceAtLeast(0) }

    fun start(minutes: Int) = startAfter(minutes * MS_PER_MINUTE)

    fun startAfter(durationMs: Long) {
        cancel()
        mutableMode.value = SleepMode.Until(clock() + durationMs)
        job = scope.launch {
            delay(durationMs)
            fire()
        }
    }

    fun stopAtEndOfTrack() {
        cancel()
        mutableMode.value = SleepMode.EndOfTrack
        player.setStopAtEndOfTrack(true)
    }

    fun cancel() {
        job?.cancel()
        job = null
        if (mutableMode.value == SleepMode.EndOfTrack) player.setStopAtEndOfTrack(false)
        mutableMode.value = SleepMode.Off
    }

    /** 時間になった: 再生中なら音量を下げてから一時停止し、音量を戻す。取り消されたら音量だけ戻す。 */
    private suspend fun fire() {
        val original = player.volume
        val self = currentCoroutineContext().job
        try {
            if (!player.state.value.isPlaying) return
            for (step in 1..FADE_STEPS) {
                delay(fadeMs / FADE_STEPS)
                player.setVolume(original * (FADE_STEPS - step) / FADE_STEPS)
            }
            player.pause()
        } finally {
            player.setVolume(original)
            // 取り消し・設定し直しのときは、cancel() が状態を変えている（新しいタイマーを消さない）
            if (job === self) {
                job = null
                mutableMode.value = SleepMode.Off
            }
        }
    }

    companion object {
        /** メニューの選択肢（分）。 */
        val CHOICES = listOf(15, 30, 45, 60, 90)
        const val FADE_MS = 10_000L
        private const val FADE_STEPS = 20
        private const val MS_PER_MINUTE = 60_000L
    }
}
