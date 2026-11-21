package app.cmsl.classical.core.data

import app.cmsl.classical.core.model.CatalogValidator
import app.cmsl.classical.core.model.Portrait
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 作曲家の肖像画の JPEG を取り出す（SPEC §3.6・§4.10・§6）。どれも `sha256` が一致したものだけを使う。
 *
 * 1. 同梱（シードの作曲家の分。`bundled("portraits/{id}.jpg")`）
 * 2. 端末のキャッシュ（`cacheDir/{id}.jpg`。`sha256` が変わったら取り直して上書きする）
 * 3. 配信元（catalog のベース URL からの相対。https で同じホストに限る）。1 枚ずつ取り、失敗したら [RETRY_AFTER] は取りに行かない
 *
 * 取れなければ null（呼び出し側は生成アートワークにする）。アクセスするのは配信元（`site/v1/portraits/`）だけ。
 */
class PortraitRepository(
    private val bundled: (String) -> InputStream?,
    private val cacheDir: File,
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val clock: Clock = Clock.systemUTC(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** 配信元へは同時に 1 つだけ（同じ画像を重ねて取らない）。 */
    private val fetchLock = Mutex()
    private val failedAt = ConcurrentHashMap<String, Instant>()

    /** `network = false` なら同梱とキャッシュだけを見る（すぐ返す。再生の準備など、通信を待てないところ用）。 */
    suspend fun load(composerId: String, portrait: Portrait, network: Boolean = true): ByteArray? = withContext(io) {
        val path = CatalogValidator.portraitPath(composerId)
        if (portrait.url != path) return@withContext null
        val local = readBundled(path, portrait) ?: readCache(composerId, portrait)
        if (local != null || !network) return@withContext local
        fetchLock.withLock {
            // 待っている間にほかの呼び出しが取ったかもしれない
            readCache(composerId, portrait) ?: fetch(composerId, portrait)
        }
    }

    private fun readBundled(path: String, portrait: Portrait): ByteArray? = try {
        bundled(path)?.use { it.readBytes() }?.takeIf { CatalogRepository.sha256(it) == portrait.sha256 }
    } catch (_: IOException) {
        null
    }

    private fun cacheFile(composerId: String) = File(cacheDir, "$composerId.jpg")

    private fun readCache(composerId: String, portrait: Portrait): ByteArray? {
        val file = cacheFile(composerId)
        if (!file.exists()) return null
        return try {
            file.readBytes().takeIf { CatalogRepository.sha256(it) == portrait.sha256 }
        } catch (_: IOException) {
            null
        }
    }

    private fun fetch(composerId: String, portrait: Portrait): ByteArray? {
        val key = "$composerId:${portrait.sha256}"
        val now = clock.instant()
        failedAt[key]?.let { last -> if (!now.isBefore(last) && Duration.between(last, now) < RETRY_AFTER) return null }
        val bytes = try {
            download(portrait)
        } catch (_: IOException) {
            null
        }
        if (bytes == null || CatalogRepository.sha256(bytes) != portrait.sha256) {
            failedAt[key] = now
            return null
        }
        failedAt.remove(key)
        writeCache(composerId, bytes)
        return bytes
    }

    private fun download(portrait: Portrait): ByteArray? {
        val url = baseUrl.resolve(portrait.url) ?: return null
        if (!url.isHttps || url.host != baseUrl.host) return null
        return client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body.bytes() else null
        }
    }

    /** 書けなくても表示はできるので、失敗は無視する。 */
    private fun writeCache(composerId: String, bytes: ByteArray) {
        try {
            cacheDir.mkdirs()
            val tmp = File(cacheDir, "$composerId.jpg.tmp")
            tmp.writeBytes(bytes)
            val target = cacheFile(composerId)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } catch (_: IOException) {
            // 次回また取りに行く
        }
    }

    companion object {
        val RETRY_AFTER: Duration = Duration.ofMinutes(10)
    }
}
