package jp.vntech.classic.core.data

import jp.vntech.classic.core.model.Portrait
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.time.Duration
import java.time.Instant

class PortraitRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val jpeg = "portrait-v1".encodeToByteArray()
    private val jpeg2 = "portrait-v2".encodeToByteArray()
    private val id = "l-v-beethoven"

    private fun portrait(bytes: ByteArray = jpeg) = Portrait(
        url = "portraits/$id.jpg",
        sha256 = CatalogRepository.sha256(bytes),
        license = "Public domain",
        sourceUrl = "https://commons.wikimedia.org/wiki/File:Beethoven.jpg",
    )

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val clock = MutableClock(Instant.parse("2026-12-01T00:00:00Z"))
    private var bundledFiles = mapOf<String, ByteArray>()
    private val cacheDir get() = File(tmp.root, "portraits")

    @Before
    fun setUp() {
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

    private fun TestScope.repository() = PortraitRepository(
        bundled = { path: String -> bundledFiles[path]?.inputStream() as InputStream? },
        cacheDir = cacheDir,
        client = client,
        baseUrl = server.url("/v1/catalog.json"),
        clock = clock,
        io = StandardTestDispatcher(testScheduler),
    )

    private fun enqueue(bytes: ByteArray, code: Int = 200) {
        server.enqueue(MockResponse.Builder().code(code).body(okio.Buffer().write(bytes)).build())
    }

    @Test
    fun `同梱の肖像画があれば通信しない`() = runTest {
        bundledFiles = mapOf("portraits/$id.jpg" to jpeg)
        assertArrayEquals(jpeg, repository().load(id, portrait()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `同梱とハッシュが違えば配信元から取り、キャッシュから読む`() = runTest {
        bundledFiles = mapOf("portraits/$id.jpg" to jpeg)
        enqueue(jpeg2)
        val repo = repository()
        assertArrayEquals(jpeg2, repo.load(id, portrait(jpeg2)))
        assertEquals("/v1/portraits/$id.jpg", server.takeRequest().url.encodedPath)
        assertArrayEquals(jpeg2, File(cacheDir, "$id.jpg").readBytes())

        // 再起動後もキャッシュを使う
        assertArrayEquals(jpeg2, repository().load(id, portrait(jpeg2)))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `network が false なら通信しない`() = runTest {
        val repo = repository()
        assertNull(repo.load(id, portrait(), network = false))
        assertEquals(0, server.requestCount)
        enqueue(jpeg)
        repo.load(id, portrait())
        assertArrayEquals(jpeg, repo.load(id, portrait(), network = false))
    }

    @Test
    fun `sha256 が変わったら取り直して上書きする`() = runTest {
        enqueue(jpeg)
        enqueue(jpeg2)
        val repo = repository()
        assertArrayEquals(jpeg, repo.load(id, portrait(jpeg)))
        assertArrayEquals(jpeg2, repo.load(id, portrait(jpeg2)))
        assertEquals(2, server.requestCount)
        assertArrayEquals(jpeg2, File(cacheDir, "$id.jpg").readBytes())
    }

    @Test
    fun `取得に失敗したら null で、しばらくは取りに行かない`() = runTest {
        enqueue(ByteArray(0), code = 404)
        val repo = repository()
        assertNull(repo.load(id, portrait()))
        assertNull(repo.load(id, portrait()))
        assertEquals(1, server.requestCount)

        clock.advance(PortraitRepository.RETRY_AFTER.plus(Duration.ofSeconds(1)))
        enqueue(jpeg)
        assertArrayEquals(jpeg, repo.load(id, portrait()))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `ハッシュの合わない応答は使わず、キャッシュにも置かない`() = runTest {
        enqueue("broken".encodeToByteArray())
        assertNull(repository().load(id, portrait()))
        assertFalse(File(cacheDir, "$id.jpg").exists())
    }

    @Test
    fun `通信できないときは null`() = runTest {
        server.close()
        assertNull(repository().load(id, portrait()))
    }

    @Test
    fun `決まったパス以外と別のホストは取りに行かない`() = runTest {
        val repo = repository()
        assertNull(repo.load(id, portrait().copy(url = "portraits/other.jpg")))
        assertNull(repo.load(id, portrait().copy(url = "https://example.com/portraits/$id.jpg")))
        val foreign = PortraitRepository(
            bundled = { null },
            cacheDir = cacheDir,
            client = client,
            baseUrl = "http://localhost/v1/catalog.json".toHttpUrl(),
            clock = clock,
            io = StandardTestDispatcher(testScheduler),
        )
        assertNull(foreign.load(id, portrait()))
        assertEquals(0, server.requestCount)
    }
}
