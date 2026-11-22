package jp.vntech.classic.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.vntech.classic.core.model.Composer
import jp.vntech.classic.core.model.Genre
import jp.vntech.classic.core.model.License
import jp.vntech.classic.core.model.Track
import jp.vntech.classic.core.model.Work
import jp.vntech.classic.core.playback.PlayerState
import jp.vntech.classic.core.playback.SleepMode
import jp.vntech.classic.di.AppContainer
import jp.vntech.classic.ui.TrackActions
import jp.vntech.classic.ui.stateInWhileSubscribed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

data class NowPlaying(val track: Track, val work: Work?, val composer: Composer?, val genre: Genre?)

/** スリープタイマーの表示（SPEC §5.2）。残り時間は時間で止めるときだけ。 */
data class SleepUiState(val mode: SleepMode = SleepMode.Off, val remainingMs: Long? = null)

/** メニューの選択肢。分か、オフ・この曲の終わりまで。 */
sealed interface SleepChoice {
    data object Off : SleepChoice

    data class Minutes(val minutes: Int) : SleepChoice

    data object EndOfTrack : SleepChoice
}

data class PlayerUiState(
    val player: PlayerState = PlayerState(),
    val now: NowPlaying? = null,
    /** キューの曲（カタログから消えた曲は null）。 */
    val queue: List<Track?> = emptyList(),
    val isFavorite: Boolean = false,
    val license: License? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(private val container: AppContainer) : ViewModel() {
    val actions = TrackActions(container, viewModelScope)
    val player = container.player

    val state = combine(container.player.state, container.catalog, container.favoriteIds) { p, catalog, favorites ->
        val index = catalog?.index ?: return@combine PlayerUiState(player = p)
        val now = p.currentTrackId?.let(index::track)?.let { t ->
            NowPlaying(t, index.work(t.workId), index.composer(t.composerId), index.genre(t.genreId))
        }
        PlayerUiState(
            player = p,
            now = now,
            queue = p.queue.map(index::track),
            isFavorite = now != null && now.track.id in favorites,
            license = index.catalog.license,
        )
    }.stateInWhileSubscribed(viewModelScope, PlayerUiState())

    private val sleepTimer = container.sleepTimer

    /** 時間で止めるときは 1 秒ごとに残り時間を出す。 */
    val sleep: StateFlow<SleepUiState> = sleepTimer.mode
        .flatMapLatest { mode ->
            if (mode is SleepMode.Until) {
                flow {
                    while (true) {
                        emit(SleepUiState(mode, sleepTimer.remainingMs()))
                        delay(SLEEP_TICK_MS)
                    }
                }
            } else {
                flowOf(SleepUiState(mode))
            }
        }
        .stateInWhileSubscribed(viewModelScope, SleepUiState())

    fun setSleep(choice: SleepChoice) = when (choice) {
        SleepChoice.Off -> sleepTimer.cancel()
        SleepChoice.EndOfTrack -> sleepTimer.stopAtEndOfTrack()
        is SleepChoice.Minutes -> sleepTimer.start(choice.minutes)
    }

    fun toggleFavorite() {
        state.value.now?.track?.let(actions::toggleFavorite)
    }
}

/** 「1:05」「12:34」「1:02:03」。 */
fun formatTime(ms: Long): String {
    val total = (ms / MS_PER_SECOND).coerceAtLeast(0)
    val h = total / SECONDS_PER_HOUR
    val m = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val s = total % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private const val MS_PER_SECOND = 1000L
private const val SLEEP_TICK_MS = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
