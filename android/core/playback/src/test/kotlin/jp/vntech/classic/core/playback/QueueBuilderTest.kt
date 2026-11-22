package jp.vntech.classic.core.playback

import jp.vntech.classic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class QueueBuilderTest {
    private fun track(id: String) = Track(
        id = id,
        title = "t$id",
        composerId = "c",
        workId = "w",
        genreId = "g",
        audioUrl = "https://classical-sound.up.seesaa.net/image/$id.mp3",
        fileName = "$id.mp3",
        articleUrl = "https://classical-sound.seesaa.net/article/1.html",
    )

    private val work = listOf("1", "2", "3", "4").map(::track)

    @Test
    fun `作品を通して再生は最初の楽章から`() {
        assertEquals(PlayQueue(listOf("1", "2", "3", "4"), 0), QueueBuilder.fromWork(work))
    }

    @Test
    fun `指定した曲から作品の最後まで`() {
        assertEquals(PlayQueue(listOf("3", "4"), 0), QueueBuilder.fromWork(work, startTrackId = "3"))
    }

    @Test
    fun `作品にない曲を指定したら最初から`() {
        assertEquals(listOf("1", "2", "3", "4"), QueueBuilder.fromWork(work, startTrackId = "x").trackIds)
    }

    @Test
    fun `一覧は並びのまま指定の位置から`() {
        assertEquals(PlayQueue(listOf("1", "2", "3", "4"), 2), QueueBuilder.fromList(work, startIndex = 2))
        assertEquals(3, QueueBuilder.fromList(work, startIndex = 99).startIndex)
        assertEquals(PlayQueue(emptyList()), QueueBuilder.fromList(emptyList(), startIndex = 1))
    }

    @Test
    fun `シャッフルは同じ曲を並べ替える`() {
        val many = (1..30).map { track(it.toString()) }
        val q = QueueBuilder.shuffled(many, Random(42))
        assertEquals(many.map { it.id }.toSet(), q.trackIds.toSet())
        assertEquals(30, q.trackIds.size)
        assertNotEquals(many.map { it.id }, q.trackIds)
        assertEquals(q, QueueBuilder.shuffled(many, Random(42)))
    }
}
