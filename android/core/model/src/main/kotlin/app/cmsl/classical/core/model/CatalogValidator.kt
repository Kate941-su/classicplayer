package app.cmsl.classical.core.model

/**
 * 取得したカタログを差し替える前の検査（SPEC §7）。`catalog/validate_catalog.py` のうち、
 * アプリが壊れないために必要なもの（版、ID の重複、参照切れ、URL のドメイン、件数の整合、置き換え先）だけを見る。
 * 「その他」の比率など、公開側の品質の検査はしない。
 */
object CatalogValidator {
    // CMSL（https）と、制作者の旧サーバー（http のみ。res/xml/network_security_config.xml で許可しているもの）
    private val AUDIO_URL = Regex(
        """^(https://classical-sound\.up\.seesaa\.net|""" +
            """http://(classical-music\.aki\.gs|andotowa\.sakura\.ne\.jp|classical-music\.sakura\.ne\.jp))/\S+\.mp3$"""
    )
    private val ARTICLE_URL = Regex("""^https://classical-sound\.seesaa\.net/article/[0-9]+\.html$""")
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val COMMONS_URL = Regex("^https://commons\\.wikimedia\\.org/\\S+$")

    /** 音源の取得先として許している URL か（ダウンロードもこれだけを取る。SPEC §1.2・§5.1）。 */
    fun isAllowedAudioUrl(url: String): Boolean = AUDIO_URL.matches(url)

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
        addPortraitErrors(catalog)
        addCountErrors(catalog)
        val trackIds = catalog.tracks.mapTo(HashSet()) { it.id }
        catalog.replacedTracks.forEach { (old, new) ->
            if (new !in trackIds) add("replacedTracks $old: 置き換え先 $new がない")
        }
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
            if (!AUDIO_URL.matches(t.audioUrl)) add("track ${t.id}: audioUrl のドメインが CMSL（と旧サーバー）ではない")
            if (!ARTICLE_URL.matches(t.articleUrl)) add("track ${t.id}: articleUrl の形式が違う")
        }
    }

    /** 肖像画は `portraits/{composer.id}.jpg` に限る（ほかのパスやホストの画像を読まないため。SPEC §3.6）。 */
    private fun MutableList<String>.addPortraitErrors(catalog: Catalog) {
        catalog.composers.forEach { c ->
            val p = c.portrait ?: return@forEach
            if (p.url != portraitPath(c.id)) add("composer ${c.id}: portrait.url が ${portraitPath(c.id)} ではない")
            if (!SHA256.matches(p.sha256)) add("composer ${c.id}: portrait.sha256 の形式が違う")
            if (!COMMONS_URL.matches(p.sourceUrl)) add("composer ${c.id}: portrait.sourceUrl が Commons ではない")
        }
    }

    fun portraitPath(composerId: String): String = "portraits/$composerId.jpg"

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
