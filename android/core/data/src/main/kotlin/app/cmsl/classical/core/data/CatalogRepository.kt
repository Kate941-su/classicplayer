package app.cmsl.classical.core.data

import app.cmsl.classical.core.model.Catalog
import app.cmsl.classical.core.model.CatalogIndex
import app.cmsl.classical.core.model.CatalogJson
import app.cmsl.classical.core.model.CatalogValidator
import app.cmsl.classical.core.model.Manifest
import app.cmsl.classical.core.model.SUPPORTED_SCHEMA_VERSION
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * カタログの取得と更新（SPEC §7）。
 *
 * 1. [load]: ローカル（`dir/catalog.json`）を読む。なければ・壊れていればシード（同梱）を使う。ネットワークは待たない
 * 2. [refresh]: manifest を ETag 付きで取得する（前回の確認から 24 時間以内なら何もしない。`force` で無視）
 * 3. `catalogVersion` が変わっていれば catalog を取得し、SHA-256 と内容を検証してから差し替える。失敗したら旧版のまま
 */
class CatalogRepository(
    private val seed: () -> InputStream,
    private val dir: File,
    private val client: OkHttpClient,
    private val manifestUrl: HttpUrl,
    private val clock: Clock = Clock.systemUTC(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val catalogFile = File(dir, CATALOG_FILE)
    private val metaFile = File(dir, META_FILE)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<CatalogState?>(null)

    /** 読み込み前は null。 */
    val state: StateFlow<CatalogState?> = _state.asStateFlow()

    suspend fun load(): CatalogState = mutex.withLock {
        _state.value?.let { return it }
        withContext(io) {
            val meta = readMeta()
            val local = readLocal()
            val loaded = if (local != null) {
                CatalogState(CatalogIndex(local), CatalogSource.Downloaded, meta.catalogVersion, meta.lastCheckedAt())
            } else {
                // ローカルがなければ版の記録も無効（次の確認で必ず取得する）
                writeMeta(meta.copy(catalogVersion = null, manifestEtag = null))
                CatalogState(CatalogIndex(readSeed()), CatalogSource.Seed, null, meta.lastCheckedAt())
            }
            loaded.also { _state.value = it }
        }
    }

    suspend fun refresh(force: Boolean = false): RefreshResult {
        load()
        return mutex.withLock { withContext(io) { refreshLocked(force) } }
    }

    private fun refreshLocked(force: Boolean): RefreshResult {
        val meta = readMeta()
        val now = clock.instant()
        val last = meta.lastCheckedAt()
        if (!force && isRecent(last, now)) return RefreshResult.Skipped
        return try {
            fetchManifest(meta, now)
        } catch (e: IOException) {
            RefreshResult.Failed("通信エラー: ${e.message}")
        } catch (e: SerializationException) {
            RefreshResult.Failed("JSON を読めない: ${e.message}")
        } catch (e: IllegalArgumentException) {
            RefreshResult.Failed("内容が不正: ${e.message}")
        }
    }

    private fun fetchManifest(meta: CatalogMeta, now: Instant): RefreshResult {
        val request = Request.Builder().url(manifestUrl)
            .apply { meta.manifestEtag?.let { header("If-None-Match", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == HTTP_NOT_MODIFIED) {
                writeMeta(meta.copy(lastCheckedAtMs = now.toEpochMilli()))
                return RefreshResult.NotModified
            }
            if (!response.isSuccessful) return RefreshResult.Failed("manifest: HTTP ${response.code}")
            val manifest = CatalogJson.decodeManifest(response.body.string())
            val checked = meta.copy(lastCheckedAtMs = now.toEpochMilli(), manifestEtag = response.header("ETag"))
            return when {
                manifest.schemaVersion != SUPPORTED_SCHEMA_VERSION || manifest.minAppSchema > SUPPORTED_SCHEMA_VERSION -> {
                    writeMeta(checked)
                    RefreshResult.Unsupported
                }
                manifest.catalogVersion == meta.catalogVersion && catalogFile.exists() -> {
                    writeMeta(checked)
                    updateCheckedAt(now)
                    RefreshResult.UpToDate
                }
                else -> fetchCatalog(manifest, checked, now)
            }
        }
    }

    private fun fetchCatalog(manifest: Manifest, meta: CatalogMeta, now: Instant): RefreshResult {
        val url = requireNotNull(manifestUrl.resolve(manifest.catalogUrl)) { "catalogUrl を解決できない" }
        require(url.isHttps && url.host == manifestUrl.host) { "catalogUrl が manifest と別のホスト" }
        val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return RefreshResult.Failed("catalog: HTTP ${response.code}")
            response.body.bytes()
        }
        if (sha256(bytes) != manifest.sha256) return RefreshResult.Failed("SHA-256 が manifest と一致しない")
        val catalog = CatalogJson.decodeCatalog(bytes.decodeToString())
        val errors = CatalogValidator.validate(catalog)
        if (errors.isNotEmpty()) return RefreshResult.Failed("検証エラー: ${errors.first()}（ほか ${errors.size - 1} 件）")

        writeAtomically(catalogFile, bytes)
        // 版の記録は差し替えの後（途中で落ちても、次回は取り直すだけで済む）
        writeMeta(meta.copy(catalogVersion = manifest.catalogVersion))
        _state.value = CatalogState(CatalogIndex(catalog), CatalogSource.Downloaded, manifest.catalogVersion, now)
        return RefreshResult.Updated(manifest.catalogVersion)
    }

    /** 時計が戻った（last が未来）ときは確認する。 */
    private fun isRecent(last: Instant?, now: Instant): Boolean =
        last != null && !now.isBefore(last) && Duration.between(last, now) < CHECK_INTERVAL

    private fun updateCheckedAt(now: Instant) {
        _state.value = _state.value?.copy(lastCheckedAt = now)
    }

    private fun readSeed(): Catalog {
        val catalog = seed().use { CatalogJson.decodeCatalog(it.readBytes().decodeToString()) }
        val errors = CatalogValidator.validate(catalog)
        check(errors.isEmpty()) { "同梱カタログが不正: $errors" }
        return catalog
    }

    /** 壊れていたら消して null（シードに戻る）。 */
    private fun readLocal(): Catalog? {
        if (!catalogFile.exists()) return null
        val catalog = try {
            CatalogJson.decodeCatalog(catalogFile.readText())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        if (catalog == null || CatalogValidator.validate(catalog).isNotEmpty()) {
            catalogFile.delete()
            return null
        }
        return catalog
    }

    private fun readMeta(): CatalogMeta = try {
        if (metaFile.exists()) {
            CatalogJson.json.decodeFromString(CatalogMeta.serializer(), metaFile.readText())
        } else {
            CatalogMeta()
        }
    } catch (_: SerializationException) {
        CatalogMeta()
    } catch (_: IllegalArgumentException) {
        CatalogMeta()
    }

    private fun writeMeta(meta: CatalogMeta) {
        writeAtomically(metaFile, CatalogJson.json.encodeToString(CatalogMeta.serializer(), meta).encodeToByteArray())
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        dir.mkdirs()
        val tmp = File(dir, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "${target.name} を書き込めない" }
        }
    }

    companion object {
        const val CATALOG_FILE = "catalog.json"
        const val META_FILE = "catalog.meta.json"
        val CHECK_INTERVAL: Duration = Duration.ofHours(24)
        private const val HTTP_NOT_MODIFIED = 304

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

data class CatalogState(
    val index: CatalogIndex,
    val source: CatalogSource,
    /** シードのときは null。 */
    val catalogVersion: String?,
    val lastCheckedAt: Instant?,
)

enum class CatalogSource { Seed, Downloaded }

sealed interface RefreshResult {
    /** 前回の確認から 24 時間以内。 */
    data object Skipped : RefreshResult

    /** manifest が 304。 */
    data object NotModified : RefreshResult

    /** manifest は取れたが catalogVersion が同じ。 */
    data object UpToDate : RefreshResult

    /** manifest の schemaVersion がアプリの対応範囲外。手持ちのカタログを使い続ける。 */
    data object Unsupported : RefreshResult

    data class Updated(val catalogVersion: String) : RefreshResult

    data class Failed(val reason: String) : RefreshResult
}
