package jp.vntech.classic.core.data

import jp.vntech.classic.core.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dir get() = File(tmp.root, "downloads")

    private fun track(id: String, url: String = "https://classical-sound.up.seesaa.net/image/$id.mp3") = Track(
        id = id,
        title = id,
        composerId = "c",
        workId = "w",
        genreId = "g",
        audioUrl = url,
        fileName = "$id.mp3",
        articleUrl = "https://classical-sound.seesaa.net/article/1.html",
    )

    /** 取得を記録するフェイク。`gates` に入れた曲は、complete するまで終わらない。 */
    private class FakeFetcher : AudioFetcher {
        val calls = mutableListOf<String>()
        val failing = mutableSetOf<String>()
        val empty = mutableSetOf<String>()
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        var running = 0
        var maxRunning = 0

        override suspend fun fetch(url: String, target: File, onProgress: (Float) -> Unit) {
            val id = url.substringAfterLast('/').removeSuffix(".mp3")
            calls += id
            running++
            maxRunning = maxOf(maxRunning, running)
            try {
                onProgress(0.5f)
                gates[id]?.await()
                if (id in failing) throw IOException("HTTP 404")
                target.writeBytes(if (id in empty) ByteArray(0) else "mp3:$id".encodeToByteArray())
                onProgress(1f)
            } finally {
                running--
            }
        }
    }

    private fun TestScope.repository(
        fetcher: AudioFetcher,
        network: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ) = DownloadRepository(
        dir = dir,
        fetcher = fetcher,
        scope = backgroundScope,
        networkAllowed = network,
        io = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `1 曲ずつ順番に取得してファイルを置く`() = runTest {
        val fetcher = FakeFetcher()
        val repo = repository(fetcher)
        val changes = mutableListOf<String>()
        backgroundScope.launch { repo.changes.collect { changes += it } }
        runCurrent()

        repo.enqueue(listOf(track("a"), track("b"), track("a")))
        repo.enqueue(listOf(track("b"), track("c")))
        repo.awaitIdle()

        assertEquals(listOf("a", "b", "c"), fetcher.calls)
        assertEquals(1, fetcher.maxRunning)
        val s = repo.state.value
        assertEquals(setOf("a", "b", "c"), s.downloaded.keys)
        assertEquals("mp3:a".length.toLong(), s.downloaded["a"])
        assertEquals(3L * "mp3:a".length, s.totalBytes)
        assertEquals("mp3:b", repo.file("b")?.readText())
        assertEquals(File(dir, "b.mp3"), repo.file("b"))
        assertNull(repo.file("x"))
        runCurrent()
        assertEquals(listOf("a", "b", "c"), changes)
        // 一時ファイルは残らない
        assertEquals(setOf("a.mp3", "b.mp3", "c.mp3"), dir.list()!!.toSet())

        // 済みの曲は取り直さない
        repo.enqueue(listOf(track("a")))
        repo.awaitIdle()
        assertEquals(3, fetcher.calls.size)
    }

    @Test
    fun `失敗した曲は未ダウンロードに戻し、次の曲へ進む。もう一度選べば取り直す`() = runTest {
        val fetcher = FakeFetcher().apply {
            failing += "a"
            empty += "b"
        }
        val repo = repository(fetcher)
        repo.enqueue(listOf(track("a"), track("b"), track("c")))
        repo.awaitIdle()

        val s = repo.state.value
        assertEquals(setOf("c"), s.downloaded.keys)
        assertEquals("b", s.lastFailure)
        assertFalse(s.isPending("a"))
        assertNull(repo.file("a"))
        assertEquals(setOf("c.mp3"), dir.list()!!.toSet())

        fetcher.failing.clear()
        repo.enqueue(listOf(track("a")))
        assertNull(repo.state.value.lastFailure)
        repo.awaitIdle()
        assertTrue(repo.state.value.isDownloaded("a"))
    }

    @Test
    fun `すぐに実行される Dispatcher と既定の通信設定でも、続けて選んだ曲を取得する`() = runTest {
        val repo =
            DownloadRepository(dir, FakeFetcher(), CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined)
        repo.enqueue(listOf(track("a")))
        repo.enqueue(listOf(track("b")))
        assertEquals(setOf("a", "b"), repo.state.value.downloaded.keys)
    }

    @Test
    fun `許していない URL の曲は取得しない`() = runTest {
        val fetcher = FakeFetcher()
        val repo = repository(fetcher)
        repo.enqueue(
            listOf(
                track("x", url = "https://example.com/x.mp3"),
                track("y", url = "http://classical-sound.up.seesaa.net/y.mp3")
            )
        )
        repo.awaitIdle()
        assertEquals(emptyList<String>(), fetcher.calls)
        assertEquals(DownloadState(), repo.state.value)
    }

    @Test
    fun `削除 済みのファイルを消す・取得中と待ちを取り消す・すべて削除`() = runTest {
        val fetcher = FakeFetcher()
        val repo = repository(fetcher)
        repo.enqueue(listOf(track("a"), track("b")))
        repo.awaitIdle()

        repo.remove("a")
        assertFalse(repo.state.value.isDownloaded("a"))
        assertFalse(File(dir, "a.mp3").exists())

        // 取得中の曲（c）と待ちの曲（d）を取り消す
        val gate = CompletableDeferred<Unit>()
        fetcher.gates["c"] = gate
        repo.enqueue(listOf(track("c"), track("d"), track("e")))
        runCurrent()
        assertEquals("c", repo.state.value.active)
        assertEquals(0.5f, repo.state.value.progress)
        repo.remove("d")
        repo.remove("c")
        repo.awaitIdle()
        gate.complete(Unit)
        assertEquals(setOf("b", "e"), repo.state.value.downloaded.keys)
        assertFalse(dir.list()!!.any { it.startsWith(".c") || it.startsWith("c") || it.startsWith("d") })

        repo.removeAll()
        assertEquals(emptyMap<String, Long>(), repo.state.value.downloaded)
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun `置き換え・削除された曲のファイルを消す`() = runTest {
        val repo = repository(FakeFetcher())
        repo.enqueue(listOf(track("old"), track("keep")))
        repo.awaitIdle()

        repo.prune { it == "keep" }
        assertEquals(setOf("keep"), repo.state.value.downloaded.keys)
        assertEquals(listOf("keep.mp3"), dir.list()!!.toList())
    }

    @Test
    fun `起動時に置いてあるファイルを数え、取得途中の一時ファイルは消す`() = runTest {
        dir.mkdirs()
        File(dir, "a.mp3").writeText("12345")
        File(dir, ".b.part").writeText("12")
        val repo = repository(FakeFetcher())
        assertEquals(mapOf("a" to 5L), repo.state.value.downloaded)
        assertNotNull(repo.file("a"))
        assertFalse(File(dir, ".b.part").exists())
    }

    @Test
    fun `Wi-Fi のときだけ 許されるまで待ち、取得中に切れたら最初から取り直す`() = runTest {
        val fetcher = FakeFetcher()
        val network = MutableStateFlow(false)
        val repo = repository(fetcher, network)
        val gate = CompletableDeferred<Unit>()
        fetcher.gates["a"] = gate

        repo.enqueue(listOf(track("a")))
        runCurrent()
        assertEquals(emptyList<String>(), fetcher.calls)
        assertTrue(repo.state.value.waitingForNetwork)
        assertEquals("a", repo.state.value.active)

        network.value = true
        runCurrent()
        assertEquals(listOf("a"), fetcher.calls)
        assertFalse(repo.state.value.waitingForNetwork)

        // 取得中にモバイル通信になった
        network.value = false
        runCurrent()
        assertTrue(repo.state.value.waitingForNetwork)
        assertEquals(0, fetcher.running)

        gate.complete(Unit)
        network.value = true
        repo.awaitIdle()
        assertEquals(listOf("a", "a"), fetcher.calls)
        assertTrue(repo.state.value.isDownloaded("a"))
        assertNull(repo.state.value.lastFailure)
    }
}
