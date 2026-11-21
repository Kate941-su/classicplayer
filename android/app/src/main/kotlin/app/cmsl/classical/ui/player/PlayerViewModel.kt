package app.cmsl.classical.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cmsl.classical.core.model.Composer
import app.cmsl.classical.core.model.Genre
import app.cmsl.classical.core.model.License
import app.cmsl.classical.core.model.Track
import app.cmsl.classical.core.model.Work
import app.cmsl.classical.core.playback.PlayerState
import app.cmsl.classical.di.AppContainer
import app.cmsl.classical.ui.TrackActions
import app.cmsl.classical.ui.stateInWhileSubscribed
import kotlinx.coroutines.flow.combine

data class NowPlaying(val track: Track, val work: Work?, val composer: Composer?, val genre: Genre?)

data class PlayerUiState(
    val player: PlayerState = PlayerState(),
    val now: NowPlaying? = null,
    /** キューの曲（カタログから消えた曲は null）。 */
    val queue: List<Track?> = emptyList(),
    val isFavorite: Boolean = false,
    val license: License? = null,
)

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
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
