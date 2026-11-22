package jp.vntech.classic.core.model

import kotlinx.serialization.Serializable

/** カタログ（schemaVersion 2）。正本は `schema/catalog.schema.json`。未知のキーは無視する（SPEC §3.2）。 */
@Serializable
data class Catalog(
    val schemaVersion: Int,
    val generatedAt: String,
    val source: String,
    val license: License,
    val genres: List<Genre>,
    val composers: List<Composer>,
    val works: List<Work>,
    val tracks: List<Track>,
    /** 新しい録音に置き換えて外した曲 ID → 同じ楽章の新しい録音の曲 ID（SPEC §3.2「作品と録音」）。 */
    val replacedTracks: Map<String, String> = emptyMap(),
)

@Serializable
data class License(
    val name: String,
    val credit: String,
    val url: String,
    val notes: String? = null,
)

@Serializable
data class Genre(
    val id: String,
    val name: String,
    val nameEn: String? = null,
    val order: Int,
    val trackCount: Int,
)

/** `id` は表記揺れの修正で変わることがあるので永続化しない。 */
@Serializable
data class Composer(
    val id: String,
    val name: String,
    val nameEn: String? = null,
    val trackCount: Int,
    /** 肖像画（SPEC §3.6）。ない作曲家と、キーのない古いカタログでは null（生成アートワークを使う）。 */
    val portrait: Portrait? = null,
)

/**
 * 作曲家の肖像画（SPEC §3.6）。画像は `url`（`portraits/{composer.id}.jpg`）を catalog のベース URL から解決して取る。
 * `title`〜`sourceUrl` はクレジットの表示に使う（SPEC §4.9）。
 */
@Serializable
data class Portrait(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    /** JPEG のハッシュ（小文字の 16 進）。キャッシュの検証と更新の判定に使う。 */
    val sha256: String,
    val title: String? = null,
    val artist: String? = null,
    val date: String? = null,
    /** 表示用のライセンス名（`CC BY-SA 4.0`、`Public domain`）。 */
    val license: String,
    /** `pd` / `cc0` / `cc-by-4.0` / `cc-by-sa-3.0` など。 */
    val licenseCode: String? = null,
    val licenseUrl: String? = null,
    /** Commons のファイルページ。 */
    val sourceUrl: String,
    val attributionRequired: Boolean = true,
    val wikidata: String? = null,
)

/** `id` は表記揺れの修正で変わることがあるので永続化しない。 */
@Serializable
data class Work(
    val id: String,
    val title: String,
    val composerId: String,
    val genreId: String,
)

/** `id` だけが永続化してよい ID（お気に入り・履歴・キュー）。 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val titleEn: String? = null,
    val composerId: String,
    val workId: String,
    val genreId: String,
    val movement: String? = null,
    val tags: List<String> = emptyList(),
    val audioUrl: String,
    val fileName: String,
    val articleUrl: String,
    val publishedAt: String? = null,
    val durationSec: Double? = null,
)

/** SPEC §3.5。 */
@Serializable
data class Manifest(
    val schemaVersion: Int,
    val catalogVersion: String,
    val catalogUrl: String,
    val sha256: String,
    val trackCount: Int,
    val generatedAt: String,
    val minAppSchema: Int,
)
