package app.cmsl.classical.core.model

/**
 * 取得したカタログを差し替える前の検査（SPEC §7）。`catalog/validate_catalog.py` のうち、
 * アプリが壊れないために必要なもの（版、ID の重複、参照切れ、URL のドメイン、件数の整合）だけを見る。
 * 「その他」の比率など、公開側の品質の検査はしない。
 */
object CatalogValidator {
    private val AUDIO_URL = Regex("""^https://classical-sound\.up\.seesaa\.net/\S+\.mp3$""")
    private val ARTICLE_URL = Regex("""^https://classical-sound\.seesaa\.net/article/[0-9]+\.html$""")

    fun validate(catalog: Catalog): List<String> = buildList {
        if (catalog.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            add("schemaVersion ${catalog.schemaVersion} は対応範囲外")
            return@buildList
        }
        if (catalog.tracks.isEmpty()) add("曲が 0 件")
        addDuplicates("genre", catalog.genres.map { it.id })
        addDuplicates("composer", catalog.composers.map { it.id })
        addDuplicates("work", catalog.works.map { it.id })
        addDuplicates("track", catalog.tracks.map { it.id })

        addReferenceErrors(catalog)
        addCountErrors(catalog)
    }

    private fun MutableList<String>.addReferenceErrors(catalog: Catalog) {
        val genreIds = catalog.genres.mapTo(HashSet()) { it.id }
        val composerIds = catalog.composers.mapTo(HashSet()) { it.id }
        val workIds = catalog.works.mapTo(HashSet()) { it.id }
        catalog.works.forEach { w ->
            if (w.composerId !in composerIds) add("work ${w.id}: composerId ${w.composerId} がない")
            if (w.genreId !in genreIds) add("work ${w.id}: genreId ${w.genreId} がない")
        }
        catalog.tracks.forEach { t ->
            if (t.composerId !in composerIds) add("track ${t.id}: composerId ${t.composerId} がない")
            if (t.workId !in workIds) add("track ${t.id}: workId ${t.workId} がない")
            if (t.genreId !in genreIds) add("track ${t.id}: genreId ${t.genreId} がない")
            if (!AUDIO_URL.matches(t.audioUrl)) add("track ${t.id}: audioUrl のドメインが CMSL ではない")
            if (!ARTICLE_URL.matches(t.articleUrl)) add("track ${t.id}: articleUrl の形式が違う")
        }
    }

    private fun MutableList<String>.addCountErrors(catalog: Catalog) {
        val byGenre = catalog.tracks.groupingBy { it.genreId }.eachCount()
        catalog.genres.filter { it.trackCount != (byGenre[it.id] ?: 0) }
            .forEach { add("genre ${it.id}: trackCount ${it.trackCount} が実数 ${byGenre[it.id] ?: 0} と違う") }
        val byComposer = catalog.tracks.groupingBy { it.composerId }.eachCount()
        catalog.composers.filter { it.trackCount != (byComposer[it.id] ?: 0) }
            .forEach { add("composer ${it.id}: trackCount ${it.trackCount} が実数 ${byComposer[it.id] ?: 0} と違う") }
    }

    private fun MutableList<String>.addDuplicates(kind: String, ids: List<String>) {
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            .forEach { add("$kind の id が重複: $it") }
    }
}
