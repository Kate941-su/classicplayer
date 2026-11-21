package app.cmsl.classical.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.cmsl.classical.core.data.CatalogState
import app.cmsl.classical.core.data.PortraitRepository
import app.cmsl.classical.core.model.Portrait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * 作曲家の肖像画（SPEC §4.10）。今のカタログの `portrait` を見て [PortraitRepository] から取り、
 * デコードしたものをメモリにためておく（画面と通知で共有する）。肖像画がない・取れないときは null。
 */
class Portraits(
    private val repository: PortraitRepository,
    private val catalog: StateFlow<CatalogState?>,
    maxEntries: Int = 48,
) {
    private data class Key(val composerId: String, val sha256: String)

    private val bitmaps = LruCache<Key, Bitmap>(maxEntries)

    fun portraitOf(composerId: String): Portrait? = catalog.value?.index?.composer(composerId)?.portrait

    /** メモリにあればすぐ返す（最初の描画で生成アートワークがちらつかないように）。 */
    fun cached(composerId: String): Bitmap? = portraitOf(composerId)?.let {
        synchronized(bitmaps) { bitmaps.get(Key(composerId, it.sha256)) }
    }

    suspend fun bitmap(composerId: String): Bitmap? {
        val portrait = portraitOf(composerId) ?: return null
        val key = Key(composerId, portrait.sha256)
        return synchronized(bitmaps) { bitmaps.get(key) } ?: repository.load(composerId, portrait)?.let { bytes ->
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                ?.also { synchronized(bitmaps) { bitmaps.put(key, it) } }
        }
    }

    /** 通知・ロック画面用の JPEG。`network = false` なら同梱とキャッシュだけ。 */
    suspend fun jpeg(composerId: String, network: Boolean): ByteArray? {
        val portrait = portraitOf(composerId) ?: return null
        return repository.load(composerId, portrait, network)
    }
}
