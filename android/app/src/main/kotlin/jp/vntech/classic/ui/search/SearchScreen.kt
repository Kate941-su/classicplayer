package jp.vntech.classic.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import jp.vntech.classic.R
import jp.vntech.classic.core.model.CatalogIndex
import jp.vntech.classic.core.model.Genre
import jp.vntech.classic.core.model.Track
import jp.vntech.classic.core.model.displayName
import jp.vntech.classic.core.model.displayTitle
import jp.vntech.classic.core.playback.QueueBuilder
import jp.vntech.classic.core.search.SearchResults
import jp.vntech.classic.di.AppContainer
import jp.vntech.classic.ui.TrackActions
import jp.vntech.classic.ui.components.AboutTrackDialog
import jp.vntech.classic.ui.components.CmslTopBar
import jp.vntech.classic.ui.components.EmptyMessage
import jp.vntech.classic.ui.components.SectionHeader
import jp.vntech.classic.ui.components.TrackRow
import jp.vntech.classic.ui.containerViewModel
import jp.vntech.classic.ui.stateInWhileSubscribed
import jp.vntech.classic.ui.theme.LocalEnglish
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class SearchUiState(
    val index: CatalogIndex? = null,
    val query: String = "",
    val results: SearchResults = SearchResults.EMPTY,
    val recent: List<String> = emptyList(),
    val genres: List<Genre> = emptyList(),
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    val actions = TrackActions(container, viewModelScope)
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    /** 入力のたびに 150ms 待ってから検索する（SPEC §4.6）。 */
    private val results = combine(_query.debounce(DEBOUNCE_MS), container.searchIndex) { q, index ->
        index?.search(q) ?: SearchResults.EMPTY
    }

    val state = combine(results, _query, container.recentSearches.recent, container.catalog) { r, q, recent, catalog ->
        SearchUiState(
            index = catalog?.index,
            query = q,
            results = if (q.isBlank()) SearchResults.EMPTY else r,
            recent = recent,
            genres = catalog?.index?.genres.orEmpty(),
        )
    }.stateInWhileSubscribed(viewModelScope, SearchUiState())

    fun setQuery(value: String) {
        _query.value = value
    }

    /** 結果を開いたとき・確定したときに「最近の検索」へ入れる。 */
    fun remember() {
        val q = _query.value
        viewModelScope.launch { container.recentSearches.add(q) }
    }

    fun forget(query: String) {
        viewModelScope.launch { container.recentSearches.remove(query) }
    }

    /** 曲はその作品の中で、その曲から最後まで。 */
    fun play(track: Track) {
        remember()
        val tracks = container.catalog.value?.index?.tracksOfWork(track.workId) ?: return
        container.player.play(QueueBuilder.fromWork(tracks, track.id))
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onComposer: (String) -> Unit, onWork: (String) -> Unit, onGenre: (String) -> Unit) {
    val vm = containerViewModel { SearchViewModel(it) }
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by vm.actions.favoriteIds.collectAsStateWithLifecycle()
    val english = LocalEnglish.current
    val keyboard = LocalSoftwareKeyboardController.current
    var aboutId by rememberSaveable { mutableStateOf<String?>(null) }
    val downloads by vm.actions.downloads.collectAsStateWithLifecycle()
    val rowActions = vm.actions.rowActions(downloads, onPlay = vm::play, onAbout = { aboutId = it.id })
    Scaffold(topBar = { CmslTopBar(stringResource(R.string.tab_search)) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        vm.remember()
                        keyboard?.hide()
                    }),
                )
            }
            if (query.isBlank()) {
                if (state.recent.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.search_recent)) }
                    items(state.recent, key = { "recent:$it" }) { q ->
                        ListItem(
                            modifier = Modifier.clickable(role = Role.Button) { vm.setQuery(q) },
                            leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                            headlineContent = { Text(q) },
                            trailingContent = {
                                IconButton(onClick = { vm.forget(q) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.action_remove_recent, q)
                                    )
                                }
                            },
                        )
                    }
                }
                item { SectionHeader(stringResource(R.string.home_genres)) }
                item {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.genres.forEach { g ->
                            AssistChip(onClick = { onGenre(g.id) }, label = { Text(g.displayName(english)) })
                        }
                    }
                }
            } else {
                val r = state.results
                if (r.isEmpty) item { EmptyMessage(stringResource(R.string.search_no_results)) }
                if (r.composers.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.search_section_composers)) }
                    items(r.composers, key = { "c:${it.id}" }) { c ->
                        ListItem(
                            modifier = Modifier.clickable(role = Role.Button) {
                                vm.remember()
                                onComposer(c.id)
                            },
                            headlineContent = { Text(c.displayName(english)) },
                        )
                    }
                }
                if (r.works.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.search_section_works)) }
                    items(r.works, key = { "w:${it.id}" }) { w ->
                        ListItem(
                            modifier = Modifier.clickable(role = Role.Button) {
                                vm.remember()
                                onWork(w.id)
                            },
                            headlineContent = { Text(w.title) },
                            supportingContent = {
                                Text(
                                    state.index?.composer(w.composerId)?.displayName(english).orEmpty()
                                )
                            },
                        )
                    }
                }
                if (r.tracks.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.search_section_tracks)) }
                    items(r.tracks, key = { "t:${it.id}" }) { t ->
                        TrackRow(
                            track = t,
                            title = t.displayTitle(english),
                            supporting = null,
                            isFavorite = t.id in favorites,
                            actions = rowActions,
                            showArtwork = true,
                        )
                    }
                }
            }
        }
        aboutId?.let { id ->
            state.index?.let { index ->
                index.track(id)?.let { AboutTrackDialog(it, index.catalog.license) { aboutId = null } }
            }
        }
    }
}
