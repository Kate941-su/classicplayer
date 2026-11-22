package jp.vntech.classic.core.model

/**
 * カタログを画面から引きやすい形にした索引。生成は 1 回（カタログの差し替え時）だけ。
 */
class CatalogIndex(val catalog: Catalog) {
    val genres: List<Genre> = catalog.genres.sortedWith(compareBy<Genre> { it.order }.thenBy { it.id })

    private val genreById = catalog.genres.associateBy { it.id }
    private val composerById = catalog.composers.associateBy { it.id }
    private val workById = catalog.works.associateBy { it.id }
    private val trackById = catalog.tracks.associateBy { it.id }

    /** 作品内の曲（楽章番号 → 公開日時 → タイトル → ID の順）。 */
    private val tracksByWork: Map<String, List<Track>> = catalog.tracks.groupBy { it.workId }
        .mapValues { (_, tracks) -> tracks.sortedWith(TRACK_IN_WORK_ORDER) }

    private val worksByComposer: Map<String, List<Work>> = catalog.works.groupBy { it.composerId }
        .mapValues { (_, works) -> works.sortedWith(compareBy<Work> { it.title }.thenBy { it.id }) }

    fun genre(id: String): Genre? = genreById[id]

    fun composer(id: String): Composer? = composerById[id]

    fun work(id: String): Work? = workById[id]

    fun track(id: String): Track? = trackById[id]

    /**
     * 保存してある曲 ID を今のカタログの ID にする。新しい録音に置き換えた曲は置き換え先（SPEC §3.2）。
     * カタログにない ID はそのまま返す（お気に入りでグレー表示するため）。
     */
    fun currentTrackId(id: String): String =
        if (id in trackById) id else catalog.replacedTracks[id]?.takeIf { it in trackById } ?: id

    /** 保存してある ID のうち、置き換えたもの（古い ID → 新しい ID）。 */
    fun replacementsFor(ids: Collection<String>): Map<String, String> =
        ids.filter { it !in trackById }.associateWith(::currentTrackId).filter { (old, new) -> old != new }

    fun tracksOfWork(workId: String): List<Track> = tracksByWork[workId].orEmpty()

    /** 作曲家の作品（genreId を指定するとそのジャンルの作品だけ）。 */
    fun worksOf(composerId: String, genreId: String? = null): List<Work> =
        worksByComposer[composerId].orEmpty().filter { genreId == null || it.genreId == genreId }

    /** 作曲家の全曲（作品順、楽章順）。「すべて再生」に使う（SPEC §4.4）。 */
    fun tracksOf(composerId: String, genreId: String? = null): List<Track> =
        worksOf(composerId, genreId).flatMap { tracksOfWork(it.id) }

    fun tracksOfGenre(genreId: String): List<Track> = catalog.tracks.filter { it.genreId == genreId }

    /** ジャンルに曲がある作曲家と、そのジャンルでの曲数。 */
    fun composersOfGenre(genreId: String): List<ComposerCount> =
        tracksOfGenre(genreId).groupingBy { it.composerId }.eachCount()
            .mapNotNull { (id, count) -> composerById[id]?.let { ComposerCount(it, count) } }

    /** 公開日時が新しい順（日時のない曲は含めない）。SPEC §4.2 の「新着」。 */
    fun newest(limit: Int): List<Track> = catalog.tracks
        .filter { it.publishedAt != null }
        .sortedWith(compareByDescending<Track> { it.publishedAt?.let(::instantKey) }.thenBy { it.id })
        .take(limit)

    companion object {
        val TRACK_IN_WORK_ORDER: Comparator<Track> =
            compareBy<Track, Int?>(nullsLast()) { Movement.number(it.movement) }
                .thenBy(nullsLast()) { it.publishedAt?.let(::instantKey) }
                .thenBy { it.title }
                .thenBy { it.id }
    }
}

data class ComposerCount(val composer: Composer, val trackCount: Int)

/** RFC 3339 の日時を、タイムゾーンをそろえて比較できるキーにする（不正な値は文字列のまま比べる）。 */
internal fun instantKey(rfc3339: String): String = try {
    java.time.OffsetDateTime.parse(rfc3339).toInstant().toString()
} catch (_: java.time.format.DateTimeParseException) {
    rfc3339
}
