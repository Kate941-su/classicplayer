package app.cmsl.classical.core.model

import kotlinx.serialization.Serializable

/** カタログ（schemaVersion 1）。正本は `schema/catalog.schema.json`。未知のキーは無視する（SPEC §3.2）。 */
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
