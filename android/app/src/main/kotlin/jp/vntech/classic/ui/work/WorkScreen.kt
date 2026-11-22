package jp.vntech.classic.ui.work

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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import jp.vntech.classic.R
import jp.vntech.classic.core.data.DownloadState
import jp.vntech.classic.core.model.CatalogIndex
import jp.vntech.classic.core.model.Composer
import jp.vntech.classic.core.model.Track
import jp.vntech.classic.core.model.Work
import jp.vntech.classic.core.model.displayName
import jp.vntech.classic.core.model.shortTitle
import jp.vntech.classic.core.playback.QueueBuilder
import jp.vntech.classic.di.AppContainer
import jp.vntech.classic.ui.TrackActions
import jp.vntech.classic.ui.components.AboutTrackDialog
import jp.vntech.classic.ui.components.Artwork
import jp.vntech.classic.ui.components.CmslTopBar
import jp.vntech.classic.ui.components.Loading
import jp.vntech.classic.ui.components.TrackRow
import jp.vntech.classic.ui.containerViewModel
import jp.vntech.classic.ui.stateInWhileSubscribed
import jp.vntech.classic.ui.theme.LocalEnglish
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

    /** 作品のまだダウンロードしていない曲を取得する（SPEC §5.1）。 */
    fun downloadWork() {
        container.catalog.value?.index?.tracksOfWork(workId)?.let(actions::download)
    }

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
        val downloads by vm.actions.downloads.collectAsStateWithLifecycle()
        val rowActions = vm.actions.rowActions(downloads, onPlay = vm::playFrom, onAbout = { aboutId = it.id })
        LazyColumn(contentPadding = padding, modifier = Modifier.testTag("work")) {
            item { WorkHeader(s, english, downloads, vm::playWork, vm::downloadWork) }
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
private fun WorkHeader(
    state: WorkUiState,
    english: Boolean,
    downloads: DownloadState,
    onPlayWork: () -> Unit,
    onDownloadWork: () -> Unit,
) {
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
        val missing = state.tracks.filterNot { downloads.isDownloaded(it.id) }
        val done = missing.isEmpty()
        OutlinedButton(
            onClick = onDownloadWork,
            // 済み・すべて取得待ちのときは押せない
            enabled = !done && !missing.all { downloads.isPending(it.id) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag("downloadWork"),
        ) {
            Icon(if (done) Icons.Filled.DownloadDone else Icons.Filled.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (done) R.string.downloads_work_done else R.string.action_download_work))
        }
    }
}
