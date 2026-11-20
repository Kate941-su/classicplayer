package app.cmsl.classical.core.data

import kotlinx.serialization.Serializable
import java.time.Instant

/** `catalog.meta.json`。ローカルのカタログの版と、manifest の確認の記録。 */
@Serializable
internal data class CatalogMeta(
    val catalogVersion: String? = null,
    val manifestEtag: String? = null,
    val lastCheckedAtMs: Long? = null,
) {
    fun lastCheckedAt(): Instant? = lastCheckedAtMs?.let(Instant::ofEpochMilli)
}
