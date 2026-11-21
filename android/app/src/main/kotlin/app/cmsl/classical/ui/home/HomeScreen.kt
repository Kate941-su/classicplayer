package app.cmsl.classical.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cmsl.classical.R
import app.cmsl.classical.core.model.CatalogIndex
import app.cmsl.classical.core.model.Genre
import app.cmsl.classical.core.model.Track
import app.cmsl.classical.core.model.displayName
import app.cmsl.classical.core.model.shortTitle
import app.cmsl.classical.core.model.workTitle
import app.cmsl.classical.core.playback.QueueBuilder
import app.cmsl.classical.di.AppContainer
import app.cmsl.classical.ui.components.Artwork
import app.cmsl.classical.ui.components.CmslTopBar
import app.cmsl.classical.ui.components.GeneratedArtwork
import app.cmsl.classical.ui.components.Loading
import app.cmsl.classical.ui.components.SectionHeader
import app.cmsl.classical.ui.containerViewModel
import app.cmsl.classical.ui.stateInWhileSubscribed
import app.cmsl.classical.ui.theme.LocalEnglish
import kotlinx.coroutines.flow.map

data class HomeUiState(val index: CatalogIndex, val newest: List<Track>, val genres: List<Genre>)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val state = container.catalog
        .map { s -> s?.let { HomeUiState(it.index, it.index.newest(NEWEST), it.index.genres) } }
        .stateInWhileSubscribed(viewModelScope, null)

    /** おまかせ再生: 全曲からシャッフル（SPEC §4.2）。 */
    fun shuffleAll() {
        val tracks = container.catalog.value?.index?.catalog?.tracks ?: return
        container.player.play(QueueBuilder.shuffled(tracks))
    }

    private companion object {
        const val NEWEST = 10
    }
}

@Composable
fun HomeScreen(onGenre: (String) -> Unit, onWork: (String) -> Unit) {
    val vm = containerViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { CmslTopBar(stringResource(R.string.app_name)) }) { padding ->
        val s = state ?: return@Scaffold Loading()
        HomeContent(s, padding, onGenre, onWork, vm::shuffleAll)
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    padding: PaddingValues,
    onGenre: (String) -> Unit,
    onWork: (String) -> Unit,
    onShuffleAll: () -> Unit,
) {
    val english = LocalEnglish.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("home"),
    ) {
        item(span = { GridItemSpan(2) }) {
            Button(onClick = onShuffleAll, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_shuffle_all))
            }
        }
        if (state.newest.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    SectionHeader(stringResource(R.string.home_newest), Modifier.padding(start = 0.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.newest, key = { it.id }) { t ->
                            val composer = state.index.composer(t.composerId)?.displayName(english).orEmpty()
                            NewestCard(
                                title = t.workTitle(english, state.index.work(t.workId)),
                                // 楽章名だけでは何の曲か分からないので、作品名の下に「作曲家 · 楽章」
                                subtitle = listOfNotNull(composer, t.movement?.let { t.shortTitle(english) })
                                    .filter { it.isNotEmpty() }
                                    .joinToString(" · "),
                                track = t,
                            ) { onWork(t.workId) }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) { SectionHeader(stringResource(R.string.home_genres)) }
        items(state.genres, key = { it.id }) { g -> GenreCard(g, english) { onGenre(g.id) } }
    }
}

@Composable
private fun NewestCard(title: String, subtitle: String, track: Track, onClick: () -> Unit) {
    Column(
        Modifier
            .width(140.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Artwork(track.composerId, track.genreId, Modifier.size(140.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun GenreCard(genre: Genre, english: Boolean, onClick: () -> Unit) {
    val name = genre.displayName(english)
    val count = pluralStringResource(R.plurals.track_count, genre.trackCount, genre.trackCount)
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$name, $count" },
    ) {
        GeneratedArtwork(
            composerId = genre.id,
            genreId = genre.id,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f),
            cornerRadius = 0
        )
        Column(Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
