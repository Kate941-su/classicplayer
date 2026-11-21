package app.cmsl.classical.ui.work

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cmsl.classical.R
import app.cmsl.classical.core.model.CatalogIndex
import app.cmsl.classical.core.model.Composer
import app.cmsl.classical.core.model.Track
import app.cmsl.classical.core.model.Work
import app.cmsl.classical.core.model.displayName
import app.cmsl.classical.core.model.shortTitle
import app.cmsl.classical.core.playback.QueueBuilder
import app.cmsl.classical.di.AppContainer
import app.cmsl.classical.ui.TrackActions
import app.cmsl.classical.ui.components.AboutTrackDialog
import app.cmsl.classical.ui.components.Artwork
import app.cmsl.classical.ui.components.CmslTopBar
import app.cmsl.classical.ui.components.Loading
import app.cmsl.classical.ui.components.TrackRow
import app.cmsl.classical.ui.components.TrackRowActions
import app.cmsl.classical.ui.containerViewModel
import app.cmsl.classical.ui.stateInWhileSubscribed
import app.cmsl.classical.ui.theme.LocalEnglish
import kotlinx.coroutines.flow.combine

data class WorkUiState(
    val index: CatalogIndex,
    val work: Work,
    val composer: Composer?,
    val tracks: List<Track>,
    val currentTrackId: String?,
)

class WorkViewModel(private val container: AppContainer, private val workId: String) : ViewModel() {
    val actions = TrackActions(container, viewModelScope)

    val state = combine(container.catalog, container.player.state) { s, player ->
        val index = s?.index ?: return@combine null
        val work = index.work(workId) ?: return@combine null
        WorkUiState(index, work, index.composer(work.composerId), index.tracksOfWork(workId), player.currentTrackId)
    }.stateInWhileSubscribed(viewModelScope, null)

    /** 作品を通して再生。 */
    fun playWork() = play(null)

    /** その曲から作品の最後まで（SPEC §4.5）。 */
    fun playFrom(track: Track) = play(track.id)

    private fun play(startTrackId: String?) {
        val tracks = container.catalog.value?.index?.tracksOfWork(workId) ?: return
        container.player.play(QueueBuilder.fromWork(tracks, startTrackId))
    }
}

@Composable
fun WorkScreen(workId: String, onBack: () -> Unit) {
    val vm = containerViewModel { WorkViewModel(it, workId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by vm.actions.favoriteIds.collectAsStateWithLifecycle()
    val english = LocalEnglish.current
    var aboutId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(topBar = { CmslTopBar(state?.work?.title.orEmpty(), onBack = onBack) }) { padding ->
        val s = state ?: return@Scaffold Loading()
        val rowActions = TrackRowActions(
            onPlay = vm::playFrom,
            onToggleFavorite = vm.actions::toggleFavorite,
            onPlayNext = vm.actions::playNext,
            onAddToQueue = vm.actions::addToQueue,
            onAbout = { aboutId = it.id },
        )
        LazyColumn(contentPadding = padding, modifier = Modifier.testTag("work")) {
            item { WorkHeader(s, english, vm::playWork) }
            items(s.tracks, key = { it.id }) { t ->
                TrackRow(
                    track = t,
                    title = t.shortTitle(english),
                    supporting = null,
                    isFavorite = t.id in favorites,
                    actions = rowActions,
                    showTags = true,
                    isCurrent = t.id == s.currentTrackId,
                )
            }
        }
        aboutId?.let { id ->
            s.index.track(id)?.let { AboutTrackDialog(it, s.index.catalog.license) { aboutId = null } }
        }
    }
}

@Composable
private fun WorkHeader(state: WorkUiState, english: Boolean, onPlayWork: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(state.work.composerId, state.work.genreId, Modifier.size(96.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(state.work.title, style = MaterialTheme.typography.titleLarge)
                state.composer?.let {
                    Text(
                        it.displayName(english),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Button(
            onClick = onPlayWork,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_play_work))
        }
    }
}
