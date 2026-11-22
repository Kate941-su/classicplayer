package jp.vntech.classic.core.artwork

import android.graphics.Bitmap
import android.util.LruCache
import java.io.ByteArrayOutputStream

/** 描いたアートワークをメモリにためておく（画面と通知で共有する）。 */
class ArtworkProvider(maxEntries: Int = 64) {
    private data class Key(val spec: ArtworkSpec, val sizePx: Int)

    private val cache = LruCache<Key, Bitmap>(maxEntries)

    fun bitmap(composerId: String, genreId: String, sizePx: Int): Bitmap {
        val key = Key(ArtworkSpec.of(composerId, genreId), sizePx)
        return synchronized(cache) {
            cache.get(key) ?: ArtworkRenderer.render(key.spec, sizePx).also { cache.put(key, it) }
        }
    }

    /** 通知・ロック画面用（MediaMetadata.artworkData）。 */
    fun png(composerId: String, genreId: String, sizePx: Int = NOTIFICATION_SIZE_PX): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap(composerId, genreId, sizePx).compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            out.toByteArray()
        }

    companion object {
        const val NOTIFICATION_SIZE_PX = 512
        private const val PNG_QUALITY = 100
    }
}
