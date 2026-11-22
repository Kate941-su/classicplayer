package jp.vntech.classic.ui.genre

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import jp.vntech.classic.R
import jp.vntech.classic.core.model.ComposerCount
import jp.vntech.classic.core.model.Genre
import jp.vntech.classic.core.model.displayName
import jp.vntech.classic.core.playback.QueueBuilder
import jp.vntech.classic.di.AppContainer
import jp.vntech.classic.ui.components.Artwork
import jp.vntech.classic.ui.components.CmslTopBar
import jp.vntech.classic.ui.components.Loading
import jp.vntech.classic.ui.containerViewModel
import jp.vntech.classic.ui.stateInWhileSubscribed
import jp.vntech.classic.ui.theme.LocalEnglish
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

enum class ComposerSort { ByCount, ByName }

data class GenreUiState(val genre: Genre, val composers: List<ComposerCount>, val sort: ComposerSort)

class GenreViewModel(private val container: AppContainer, private val genreId: String) : ViewModel() {
    private val sort = MutableStateFlow(ComposerSort.ByCount)

    val state = combine(container.catalog, sort) { s, sort ->
        val index = s?.index ?: return@combine null
        val genre = index.genre(genreId) ?: return@combine null
        val composers = index.composersOfGenre(genreId).let { list ->
            when (sort) {
                ComposerSort.ByCount -> list.sortedWith(
                    compareByDescending<ComposerCount> {
                        it.trackCount
                    }.thenBy { it.composer.name }
                )
                ComposerSort.ByName -> list.sortedBy { it.composer.name }
            }
        }
        GenreUiState(genre, composers, sort)
    }.stateInWhileSubscribed(viewModelScope, null)

    fun setSort(value: ComposerSort) {
        sort.value = value
    }

    fun shuffleGenre() {
        val tracks = container.catalog.value?.index?.tracksOfGenre(genreId) ?: return
        container.player.play(QueueBuilder.shuffled(tracks))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreScreen(genreId: String, onComposer: (String) -> Unit, onBack: () -> Unit) {
    val vm = containerViewModel { GenreViewModel(it, genreId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val english = LocalEnglish.current
    Scaffold(
        topBar = {
            CmslTopBar(state?.genre?.displayName(english).orEmpty(), onBack = onBack) {
                IconButton(onClick = vm::shuffleGenre) {
                    Icon(Icons.Filled.Shuffle, contentDescription = stringResource(R.string.action_shuffle_genre))
                }
            }
        },
    ) { padding ->
        val s = state ?: return@Scaffold Loading()
        LazyColumn(contentPadding = padding) {
            item {
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    ComposerSort.entries.forEachIndexed { i, sort ->
                        SegmentedButton(
                            selected = s.sort == sort,
                            onClick = { vm.setSort(sort) },
                            shape = SegmentedButtonDefaults.itemShape(i, ComposerSort.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    if (sort == ComposerSort.ByCount) R.string.sort_by_count else R.string.sort_by_name
                                )
                            )
                        }
                    }
                }
            }
            items(s.composers, key = { it.composer.id }) { c ->
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { onComposer(c.composer.id) },
                    leadingContent = { Artwork(c.composer.id, genreId, Modifier.size(48.dp)) },
                    headlineContent = { Text(c.composer.displayName(english)) },
                    trailingContent = {
                        Row { Text(pluralStringResource(R.plurals.track_count, c.trackCount, c.trackCount)) }
                    },
                )
            }
        }
    }
}
