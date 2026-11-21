package app.cmsl.classical.ui.library

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cmsl.classical.R
import app.cmsl.classical.core.data.DownloadState
import app.cmsl.classical.core.model.CatalogIndex
import app.cmsl.classical.core.model.Track
import app.cmsl.classical.core.model.displayName
import app.cmsl.classical.core.model.displayTitle
import app.cmsl.classical.core.model.shortTitle
import app.cmsl.classical.core.playback.QueueBuilder
import app.cmsl.classical.di.AppContainer
import app.cmsl.classical.ui.TrackActions
import app.cmsl.classical.ui.components.AboutTrackDialog
import app.cmsl.classical.ui.components.CmslTopBar
import app.cmsl.classical.ui.components.EmptyMessage
import app.cmsl.classical.ui.components.Loading
import app.cmsl.classical.ui.components.TrackRow
import app.cmsl.classical.ui.components.TrackRowActions
import app.cmsl.classical.ui.containerViewModel
import app.cmsl.classical.ui.stateInWhileSubscribed
import app.cmsl.classical.ui.theme.LocalEnglish
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class FavoriteSort(val label: Int) { ByAdded(R.string.sort_by_added), ByComposer(R.string.sort_by_composer) }

/** カタログから消えた曲は track が null（「現在利用できません」としてグレー表示する。SPEC §6）。 */
data class LibraryEntry(val trackId: String, val track: Track?)

data class LibraryUiState(
    val index: CatalogIndex,
    val favorites: List<LibraryEntry>,
    val history: List<Track>,
    val sort: FavoriteSort,
)

/** ライブラリの「ダウンロード」（SPEC §4.7・§5.1）。 */
data class DownloadsUiState(val tracks: List<Track>, val downloads: DownloadState, val wifiOnly: Boolean)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    val actions = TrackActions(container, viewModelScope)
    private val sort = MutableStateFlow(FavoriteSort.ByAdded)

    val state = combine(
        container.catalog,
        container.database.favorites().observeAll(),
        container.database.history().observeRecent(),
        sort,
    ) { s, favorites, history, sort ->
        val index = s?.index ?: return@combine null
        val entries = favorites.map { LibraryEntry(it.trackId, index.track(it.trackId)) }
        val sorted = when (sort) {
            FavoriteSort.ByAdded -> entries
            FavoriteSort.ByComposer -> entries.sortedWith(
                compareBy<LibraryEntry, String?>(
                    nullsLast()
                ) { e -> e.track?.let { index.composer(it.composerId)?.name } }
                    .thenBy(nullsLast(), { e -> e.track?.let { index.work(it.workId)?.title } }),
            )
        }
        LibraryUiState(index, sorted, history.mapNotNull { index.track(it.trackId) }, sort)
    }.stateInWhileSubscribed(viewModelScope, null)

    val downloads = combine(
        container.catalog,
        container.downloads.state,
        container.downloadSettings.wifiOnly,
    ) { s, downloads, wifiOnly ->
        val index = s?.index ?: return@combine null
        DownloadsUiState(downloadedTracks(index, downloads), downloads, wifiOnly)
    }.stateInWhileSubscribed(viewModelScope, null)

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch { container.downloadSettings.setWifiOnly(value) }
    }

    fun removeAllDownloads() = container.downloads.removeAll()

    fun playDownload(track: Track) = playFrom(downloads.value?.tracks.orEmpty(), track)

    fun setSort(value: FavoriteSort) {
        sort.value = value
    }

    fun removeFavorite(trackId: String) {
        viewModelScope.launch { container.database.favorites().remove(trackId) }
    }

    fun playFavorite(track: Track) = playFrom(state.value?.favorites?.mapNotNull { it.track }.orEmpty(), track)

    fun playHistory(track: Track) = playFrom(state.value?.history.orEmpty(), track)

    private fun playFrom(list: List<Track>, track: Track) {
        container.player.play(QueueBuilder.fromList(list, list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val vm = containerViewModel { LibraryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by vm.actions.favoriteIds.collectAsStateWithLifecycle()
    val english = LocalEnglish.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var aboutId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRemoveAll by rememberSaveable { mutableStateOf(false) }
    val downloadsState by vm.downloads.collectAsStateWithLifecycle()
    Scaffold(topBar = { CmslTopBar(stringResource(R.string.tab_library)) }) { padding ->
        val s = state ?: return@Scaffold Loading()
        val downloads by vm.actions.downloads.collectAsStateWithLifecycle()
        val rowActions = vm.actions.rowActions(
            downloads,
            onPlay = when (tab) {
                0 -> vm::playFavorite
                1 -> vm::playHistory
                else -> vm::playDownload
            },
            onAbout = { aboutId = it.id },
        )
        LazyColumn(contentPadding = padding) {
            item {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text(stringResource(R.string.library_favorites)) }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text(stringResource(R.string.library_history)) }
                    )
                    Tab(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        text = { Text(stringResource(R.string.library_downloads)) }
                    )
                }
            }
            if (tab == 0) {
                item {
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        FavoriteSort.entries.forEachIndexed { i, sort ->
                            SegmentedButton(
                                selected = s.sort == sort,
                                onClick = { vm.setSort(sort) },
                                shape = SegmentedButtonDefaults.itemShape(i, FavoriteSort.entries.size),
                            ) {
                                Text(
                                    stringResource(
                                        sort.label
                                    )
                                )
                            }
                        }
                    }
                }
                if (s.favorites.isEmpty()) item { EmptyMessage(stringResource(R.string.library_favorites_empty)) }
                items(s.favorites, key = { "f:${it.trackId}" }) { e ->
                    val t = e.track
                    if (t == null) {
                        UnavailableRow(e.trackId) { vm.removeFavorite(e.trackId) }
                    } else {
                        TrackRow(
                            track = t,
                            title = t.displayTitle(english),
                            supporting = s.index.composer(t.composerId)?.displayName(english),
                            isFavorite = true,
                            actions = rowActions,
                            showArtwork = true,
                        )
                    }
                }
            } else if (tab == 2) {
                downloadsSection(downloadsState, s.index, english, favorites, rowActions, vm::setWifiOnly) {
                    confirmRemoveAll = true
                }
            } else {
                if (s.history.isEmpty()) item { EmptyMessage(stringResource(R.string.library_history_empty)) }
                items(s.history, key = { "h:${it.id}" }) { t ->
                    TrackRow(
                        track = t,
                        title = t.displayTitle(english),
                        supporting = s.index.composer(t.composerId)?.displayName(english),
                        isFavorite = t.id in favorites,
                        actions = rowActions,
                        showArtwork = true,
                    )
                }
            }
        }
        if (confirmRemoveAll) {
            RemoveAllDialog(
                onConfirm = {
                    confirmRemoveAll = false
                    vm.removeAllDownloads()
                },
                onDismiss = { confirmRemoveAll = false },
            )
        }
        aboutId?.let { id ->
            s.index.track(
                id
            )?.let { AboutTrackDialog(it, s.index.catalog.license) { aboutId = null } }
        }
    }
}

@Composable
private fun UnavailableRow(trackId: String, onRemove: () -> Unit) {
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    ListItem(
        headlineContent = { Text(stringResource(R.string.track_unavailable), color = disabled) },
        supportingContent = { Text(trackId, color = disabled) },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = stringResource(R.string.action_unfavorite),
                    tint = disabled,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    )
}

/** 作曲家順 → 作品順 → 楽章順。カタログにない曲は出さない（カタログの更新で消える）。 */
private fun downloadedTracks(index: CatalogIndex, downloads: DownloadState): List<Track> {
    val tracks = downloads.downloaded.keys.mapNotNull(index::track)
    return tracks.sortedWith(
        compareBy<Track> { index.composer(it.composerId)?.name.orEmpty() }
            .thenBy { index.work(it.workId)?.title.orEmpty() }
            .thenBy { t -> index.tracksOfWork(t.workId).indexOfFirst { it.id == t.id } },
    )
}

@Suppress("LongParameterList")
private fun LazyListScope.downloadsSection(
    state: DownloadsUiState?,
    index: CatalogIndex,
    english: Boolean,
    favorites: Set<String>,
    rowActions: TrackRowActions,
    onWifiOnly: (Boolean) -> Unit,
    onRemoveAll: () -> Unit,
) {
    val s = state ?: return
    val d = s.downloads
    item(key = "wifi") {
        ListItem(
            headlineContent = { Text(stringResource(R.string.downloads_wifi_only)) },
            trailingContent = { Switch(checked = s.wifiOnly, onCheckedChange = null) },
            modifier = Modifier.toggleable(value = s.wifiOnly, role = Role.Switch, onValueChange = onWifiOnly),
        )
    }
    d.active?.let { active ->
        item(key = "active") { ActiveDownload(index.track(active)?.displayTitle(english) ?: active, d) }
    }
    if (d.lastFailure != null) {
        item(key = "failed") {
            Text(
                stringResource(R.string.downloads_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    if (s.tracks.isEmpty()) {
        item(key = "empty") { EmptyMessage(stringResource(R.string.library_downloads_empty)) }
        return
    }
    item(key = "total") {
        val context = LocalContext.current
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.downloads_total, Formatter.formatShortFileSize(context, d.totalBytes)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemoveAll) { Text(stringResource(R.string.action_remove_all_downloads)) }
        }
    }
    items(s.tracks, key = { "d:${it.id}" }) { t ->
        // 同じ作品の楽章が並ぶので、楽章名と「作曲家 · 作品名」で見分ける
        TrackRow(
            track = t,
            title = t.shortTitle(english),
            supporting = listOfNotNull(
                index.composer(t.composerId)?.displayName(english),
                index.work(t.workId)?.title,
            ).joinToString(" · "),
            isFavorite = t.id in favorites,
            actions = rowActions,
            showArtwork = true,
        )
    }
}

/** 取得中の曲と進み具合、待っている曲の数。 */
@Composable
private fun ActiveDownload(title: String, d: DownloadState) {
    val waiting = d.queued.size
    ListItem(
        overlineContent = {
            Text(
                stringResource(
                    if (d.waitingForNetwork) R.string.downloads_waiting_for_wifi else R.string.downloads_in_progress
                )
            )
        },
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                LinearProgressIndicator(
                    progress = { d.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
                if (waiting > 0) Text(pluralStringResource(R.plurals.downloads_waiting, waiting, waiting))
            }
        },
    )
}

@Composable
private fun RemoveAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.downloads_remove_all_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_remove_all_downloads), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
