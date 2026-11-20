package app.cmsl.classical.core.search

import app.cmsl.classical.core.model.CatalogIndex
import app.cmsl.classical.core.model.Composer
import app.cmsl.classical.core.model.Track
import app.cmsl.classical.core.model.Work

/**
 * 端末内のカタログを検索する（SPEC §4.6）。照合と並び順は `docs/search-normalization.md`。
 * キーの正規化はカタログの差し替え時に 1 回だけ行う。
 */
class SearchIndex(private val catalog: CatalogIndex) {
    private class Entry<T>(val item: T, val primary: List<String>, val secondary: List<String>, val order: Int)

    private val composers: List<Entry<Composer>> = catalog.catalog.composers
        .sortedWith(compareByDescending<Composer> { it.trackCount }.thenBy { it.name })
        .mapIndexed { i, c -> Entry(c, keys(c.name, c.nameEn), emptyList(), i) }

    private val works: List<Entry<Work>> = catalog.catalog.works
        .sortedWith(compareBy<Work> { it.title }.thenBy { it.id })
        .mapIndexed { i, w -> Entry(w, keys(w.title), composerKeys(w.composerId), i) }

    private val tracks: List<Entry<Track>> = works.flatMap { catalog.tracksOfWork(it.item.id) }
        .mapIndexed { i, t ->
            Entry(t, keys(t.title, t.titleEn), keys(catalog.work(t.workId)?.title) + composerKeys(t.composerId), i)
        }

    fun search(query: String): SearchResults {
        val terms = SearchNormalizer.terms(query)
        if (terms.isEmpty()) return SearchResults.EMPTY
        return SearchResults(
            composers = match(composers, terms, MAX_COMPOSERS),
            works = match(works, terms, MAX_WORKS),
            tracks = match(tracks, terms, MAX_TRACKS),
        )
    }

    private fun <T> match(entries: List<Entry<T>>, terms: List<String>, limit: Int): List<T> = entries
        .mapNotNull { e -> rank(e, terms)?.let { it to e } }
        .sortedWith(compareBy<Pair<Int, Entry<T>>> { it.first }.thenBy { it.second.order })
        .take(limit)
        .map { it.second.item }

    /** 0: すべての語が主キーの先頭、1: 主キーのどれか、2: 副キーを含めて一致。一致しなければ null。 */
    private fun rank(e: Entry<*>, terms: List<String>): Int? = when {
        terms.all { t -> e.primary.any { it.startsWith(t) } } -> 0
        terms.all { t -> e.primary.any { it.contains(t) } } -> 1
        terms.all { t -> e.primary.any { it.contains(t) } || e.secondary.any { it.contains(t) } } -> 2
        else -> null
    }

    private fun composerKeys(composerId: String): List<String> =
        catalog.composer(composerId)?.let { keys(it.name, it.nameEn) }.orEmpty()

    private fun keys(vararg texts: String?): List<String> =
        texts.filterNotNull().map(SearchNormalizer::normalize).filter { it.isNotEmpty() }

    companion object {
        const val MAX_COMPOSERS = 20
        const val MAX_WORKS = 50
        const val MAX_TRACKS = 100
    }
}

data class SearchResults(
    val composers: List<Composer>,
    val works: List<Work>,
    val tracks: List<Track>,
) {
    val isEmpty: Boolean get() = composers.isEmpty() && works.isEmpty() && tracks.isEmpty()

    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), emptyList())
    }
}
