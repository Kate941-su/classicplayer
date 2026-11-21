package app.cmsl.classical.ui.composer

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import app.cmsl.classical.core.model.displayTitle
import app.cmsl.classical.core.playback.QueueBuilder
import app.cmsl.classical.di.AppContainer
import app.cmsl.classical.ui.TrackActions
import app.cmsl.classical.ui.components.AboutTrackDialog
import app.cmsl.classical.ui.components.Artwork
import app.cmsl.classical.ui.components.CmslTopBar
import app.cmsl.classical.ui.components.Loading
import app.cmsl.classical.ui.components.PortraitCredit
import app.cmsl.classical.ui.components.TrackRow
import app.cmsl.classical.ui.components.TrackRowActions
import app.cmsl.classical.ui.containerViewModel
import app.cmsl.classical.ui.stateInWhileSubscribed
import app.cmsl.classical.ui.theme.LocalEnglish
import kotlinx.coroutines.flow.map

/** 作品の行。曲が 1 つだけの作品は、曲の行として直接出す（SPEC §4.4）。 */
sealed interface ComposerEntry {
    val key: String

    data class WorkEntry(val work: Work, val trackCount: Int) : ComposerEntry {
        override val key: String get() = "w:${work.id}"
    }

    data class SingleTrack(val track: Track) : ComposerEntry {
        override val key: String get() = "t:${track.id}"
    }
}

data class ComposerUiState(val index: CatalogIndex, val composer: Composer, val entries: List<ComposerEntry>)

class ComposerViewModel(
    private val container: AppContainer,
    private val composerId: String,
    private val genreId: String?,
) : ViewModel() {
    val actions = TrackActions(container, viewModelScope)

    val state = container.catalog.map { s ->
        val index = s?.index ?: return@map null
        val composer = index.composer(composerId) ?: return@map null
        val entries = index.worksOf(composerId, genreId).map { w ->
            val tracks = index.tracksOfWork(w.id)
            if (tracks.size == 1) {
                ComposerEntry.SingleTrack(
                    tracks.single()
                )
            } else {
                ComposerEntry.WorkEntry(w, tracks.size)
            }
        }
        ComposerUiState(index, composer, entries)
    }.stateInWhileSubscribed(viewModelScope, null)

    /** すべて再生（作品順、楽章順）。 */
    fun playAll() {
        val tracks = container.catalog.value?.index?.tracksOf(composerId, genreId) ?: return
        container.player.play(QueueBuilder.fromList(tracks))
    }

    fun playTrack(track: Track) {
        val tracks = container.catalog.value?.index?.tracksOf(composerId, genreId) ?: return
        container.player.play(QueueBuilder.fromList(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)))
    }
}

/** 肖像画（なければ生成アートワーク）と、肖像画のクレジット（SPEC §4.4）。 */
@Composable
private fun ComposerHeader(composer: Composer, genreId: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(composer.id, genreId.orEmpty(), Modifier.size(120.dp), cornerRadius = 12)
        Spacer(Modifier.width(16.dp))
        composer.portrait?.let { PortraitCredit(it, Modifier.weight(1f)) }
    }
}

@Composable
fun ComposerScreen(composerId: String, genreId: String?, onWork: (String) -> Unit, onBack: () -> Unit) {
    val vm = containerViewModel { ComposerViewModel(it, composerId, genreId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by vm.actions.favoriteIds.collectAsStateWithLifecycle()
    val english = LocalEnglish.current
    var aboutId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(topBar = { CmslTopBar(state?.composer?.displayName(english).orEmpty(), onBack = onBack) }) { padding ->
        val s = state ?: return@Scaffold Loading()
        val rowActions = TrackRowActions(
            onPlay = vm::playTrack,
            onToggleFavorite = vm.actions::toggleFavorite,
            onPlayNext = vm.actions::playNext,
            onAddToQueue = vm.actions::addToQueue,
            onAbout = { aboutId = it.id },
        )
        LazyColumn(contentPadding = padding) {
            item {
                // ジャンルを指定せずに開いたときは、その作曲家の最初の曲のジャンルの絵にする
                ComposerHeader(s.composer, genreId ?: s.index.tracksOf(s.composer.id, null).firstOrNull()?.genreId)
            }
            item {
                Button(
                    onClick = vm::playAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_play_all))
                }
            }
            items(
                s.entries,
                key = { it.key }
            ) { e ->
                when (e) {
                    is ComposerEntry.WorkEntry -> ListItem(
                        modifier = Modifier.clickable(role = Role.Button) { onWork(e.work.id) },
                        headlineContent = { Text(e.work.title) },
                        supportingContent = {
                            Text(
                                pluralStringResource(R.plurals.movement_count, e.trackCount, e.trackCount)
                            )
                        },
                    )
                    is ComposerEntry.SingleTrack -> TrackRow(
                        track = e.track,
                        title = s.index.work(e.track.workId)?.title ?: e.track.displayTitle(english),
                        supporting = null,
                        isFavorite = e.track.id in favorites,
                        actions = rowActions,
                    )
                }
            }
        }
        aboutId?.let { id ->
            s.index.track(id)?.let { AboutTrackDialog(it, s.index.catalog.license) { aboutId = null } }
        }
    }
}
