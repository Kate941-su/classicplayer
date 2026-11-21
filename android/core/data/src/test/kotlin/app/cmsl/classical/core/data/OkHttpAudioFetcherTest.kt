package app.cmsl.classical.core.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OkHttpAudioFetcherTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `ファイルに書き、進み具合を知らせる`() = runBlocking {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        server.enqueue(MockResponse.Builder().body(okio.Buffer().write(bytes)).build())
        val target = File(tmp.root, "a.part")
        val progress = mutableListOf<Float>()

        OkHttpAudioFetcher(OkHttpClient()).fetch(server.url("/a.mp3").toString(), target) { progress += it }

        assertArrayEquals(bytes, target.readBytes())
        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
        assertEquals("CMSLClassical-Android", server.takeRequest().headers["User-Agent"])
    }

    @Test(expected = DownloadException::class)
    fun `HTTP のエラーは失敗にする`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        OkHttpAudioFetcher(OkHttpClient()).fetch(server.url("/a.mp3").toString(), File(tmp.root, "a.part")) {}
    }
}
