package app.cmsl.classical.core.data

import app.cmsl.classical.core.model.CatalogJson
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class CatalogRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val fixtures = File(requireNotNull(System.getProperty("fixturesDir")))
    private val sampleBytes = File(fixtures, "catalog.sample.json").readBytes()
    private val sampleCount = CatalogJson.decodeCatalog(sampleBytes.decodeToString()).tracks.size

    /** シードは「サンプルから 1 曲だけの版」にして、ダウンロード版と区別できるようにする。 */
    private val seedBytes = File(fixtures, "catalog.sample.json").readText().let { text ->
        val catalog = CatalogJson.decodeCatalog(text)
        val track = catalog.tracks.first()
        val one = catalog.copy(
            genres = catalog.genres.filter { it.id == track.genreId }.map { it.copy(trackCount = 1) },
            composers = catalog.composers.filter { it.id == track.composerId }.map { it.copy(trackCount = 1) },
            works = catalog.works.filter { it.id == track.workId },
            tracks = listOf(track),
            replacedTracks = catalog.replacedTracks.filterValues { it == track.id },
        )
        CatalogJson.json.encodeToString(app.cmsl.classical.core.model.Catalog.serializer(), one).encodeToByteArray()
    }

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val clock = MutableClock(Instant.parse("2026-12-01T00:00:00Z"))

    @Before
    fun setUp() {
        // 本番と同じく HTTPS で試す（catalogUrl は https で同じホストに限るため）
        val localhost = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(localhost).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(localhost.certificate).build()
        server = MockWebServer()
        server.useHttps(serverCerts.sslSocketFactory())
        server.start()
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun manifestUrl(): HttpUrl = server.url("/v1/manifest.json")

    private fun repository(dispatcher: TestDispatcher) = CatalogRepository(
        seed = { seedBytes.inputStream() },
        dir = tmp.root,
        client = client,
        manifestUrl = manifestUrl(),
        clock = clock,
        io = dispatcher,
    )

    private fun manifest(version: String, bytes: ByteArray = sampleBytes, schemaVersion: Int = 2, sha: String? = null) =
        """
        {"schemaVersion": $schemaVersion, "catalogVersion": "$version", "catalogUrl": "catalog.json",
         "sha256": "${sha ?: CatalogRepository.sha256(bytes)}", "trackCount": $sampleCount,
         "generatedAt": "2026-12-01T03:17:00+00:00", "minAppSchema": 2}
        """.trimIndent()

    private fun enqueueManifest(body: String, etag: String = "\"m1\"") {
        server.enqueue(MockResponse.Builder().body(body).addHeader("ETag", etag).build())
    }

    private fun enqueueCatalog(bytes: ByteArray) {
        server.enqueue(MockResponse.Builder().body(okio.Buffer().write(bytes)).build())
    }

    @Test
    fun `初回はシードを読む`() = runTest {
        val repo = repository(StandardTestDispatcher(testScheduler))
        val state = repo.load()
        assertEquals(CatalogSource.Seed, state.source)
        assertNull(state.catalogVersion)
        assertEquals(1, state.index.catalog.tracks.size)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `新しい版を取得して差し替え、再起動後もローカルを使う`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = repository(dispatcher)
        enqueueManifest(manifest("2026.12.01"))
        enqueueCatalog(sampleBytes)

        assertEquals(RefreshResult.Updated("2026.12.01"), repo.refresh())
        assertEquals(sampleCount, repo.state.value!!.index.catalog.tracks.size)
        assertEquals("/v1/catalog.json", server.takeRequest().let { server.takeRequest().url.encodedPath })

        val restarted = repository(dispatcher).load()
        assertEquals(CatalogSource.Downloaded, restarted.source)
        assertEquals("2026.12.01", restarted.catalogVersion)
        assertEquals(sampleCount, restarted.index.catalog.tracks.size)
    }

    @Test
    fun `24時間以内は確認しない。force なら確認する`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = repository(dispatcher)
        enqueueManifest(manifest("2026.12.01"))
        enqueueCatalog(sampleBytes)
        repo.refresh()
        assertEquals(2, server.requestCount)

        clock.advance(Duration.ofHours(23))
        assertEquals(RefreshResult.Skipped, repo.refresh())
        assertEquals(2, server.requestCount)

        enqueueManifest(manifest("2026.12.01"))
        assertEquals(RefreshResult.UpToDate, repo.refresh(force = true))
        assertEquals(3, server.requestCount)

        clock.advance(Duration.ofHours(25))
        server.enqueue(MockResponse.Builder().code(304).build())
        assertEquals(RefreshResult.NotModified, repo.refresh())
        // 前回の ETag を送っている
        server.takeRequest()
        server.takeRequest()
        server.takeRequest()
        assertEquals("\"m1\"", server.takeRequest().headers["If-None-Match"])
    }

    @Test
    fun `SHA-256 が合わなければ旧版のまま`() = runTest {
        val repo = repository(StandardTestDispatcher(testScheduler))
        enqueueManifest(manifest("2026.12.01", sha = "0".repeat(64)))
        enqueueCatalog(sampleBytes)
        val result = repo.refresh()
        assertTrue(result is RefreshResult.Failed)
        assertEquals(CatalogSource.Seed, repo.state.value!!.source)
        assertFalse(File(tmp.root, CatalogRepository.CATALOG_FILE).exists())
    }

    @Test
    fun `異常系のカタログを受け取ったら差し替えない`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = repository(dispatcher)
        enqueueManifest(manifest("2026.12.01"))
        enqueueCatalog(sampleBytes)
        repo.refresh()

        File(fixtures, "invalid").listFiles { f -> f.extension == "json" }!!.sorted().forEachIndexed { i, file ->
            val bytes = file.readBytes()
            clock.advance(Duration.ofHours(25))
            enqueueManifest(manifest("2026.12.0${i + 2}", bytes))
            enqueueCatalog(bytes)
            val result = repo.refresh()
            assertTrue("${file.name}: $result", result is RefreshResult.Failed)
            assertEquals("2026.12.01", repo.state.value!!.catalogVersion)
            assertEquals(sampleCount, repo.state.value!!.index.catalog.tracks.size)
        }
        assertEquals("2026.12.01", repository(dispatcher).load().catalogVersion)
    }

    @Test
    fun `manifest の schemaVersion が対応範囲外なら更新しない`() = runTest {
        val repo = repository(StandardTestDispatcher(testScheduler))
        enqueueManifest(manifest("2026.12.01", schemaVersion = 3))
        assertEquals(RefreshResult.Unsupported, repo.refresh())
        assertEquals(1, server.requestCount)
        assertEquals(CatalogSource.Seed, repo.state.value!!.source)
    }

    @Test
    fun `通信エラーやサーバーエラーでは旧版のまま`() = runTest {
        val repo = repository(StandardTestDispatcher(testScheduler))
        server.enqueue(MockResponse.Builder().code(500).build())
        assertTrue(repo.refresh() is RefreshResult.Failed)
        // 失敗は確認済みにしない（次の起動で再確認する）
        server.close()
        assertTrue(repo.refresh() is RefreshResult.Failed)
        assertEquals(CatalogSource.Seed, repo.state.value!!.source)
    }

    @Test
    fun `壊れたローカルはシードに戻り、次の確認で取り直す`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        File(tmp.root, CatalogRepository.CATALOG_FILE).writeText("{ broken")
        File(tmp.root, CatalogRepository.META_FILE).writeText("""{"catalogVersion": "2026.12.01"}""")
        val repo = repository(dispatcher)
        assertEquals(CatalogSource.Seed, repo.load().source)

        enqueueManifest(manifest("2026.12.01"))
        enqueueCatalog(sampleBytes)
        assertEquals(RefreshResult.Updated("2026.12.01"), repo.refresh())
    }
}

internal class MutableClock(private var now: Instant) : Clock() {
    fun advance(d: Duration) {
        now = now.plus(d)
    }

    override fun instant(): Instant = now

    override fun getZone(): ZoneOffset = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId?): Clock = this
}
