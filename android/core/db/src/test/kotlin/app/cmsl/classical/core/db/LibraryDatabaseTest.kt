package app.cmsl.classical.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryDatabaseTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        LibraryDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `お気に入りは追加日の新しい順で、重複して追加しても追加日は変わらない`() = runTest {
        val dao = db.favorites()
        dao.add(FavoriteEntity("a", 100))
        dao.add(FavoriteEntity("b", 200))
        dao.add(FavoriteEntity("a", 300))
        dao.observeAll().test {
            assertEquals(listOf(FavoriteEntity("b", 200), FavoriteEntity("a", 100)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `お気に入りの切り替え`() = runTest {
        val dao = db.favorites()
        assertTrue(dao.toggle("a", 1))
        assertTrue(dao.contains("a"))
        assertFalse(dao.toggle("a", 2))
        assertFalse(dao.contains("a"))
        dao.observeIds().test {
            assertEquals(emptyList<String>(), awaitItem())
            dao.toggle("b", 3)
            assertEquals(listOf("b"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `履歴は同じ曲を1件にまとめ、新しい順`() = runTest {
        val dao = db.history()
        dao.record("a", 1)
        dao.record("b", 2)
        dao.record("a", 3)
        dao.observeRecent().test {
            assertEquals(listOf(HistoryEntity("a", 3), HistoryEntity("b", 2)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `履歴は最大100件で古いものから消える`() = runTest {
        val dao = db.history()
        (1..105).forEach { dao.record("t$it", it.toLong()) }
        dao.observeRecent().test {
            val items = awaitItem()
            assertEquals(HistoryDao.MAX_HISTORY, items.size)
            assertEquals("t105", items.first().trackId)
            assertEquals("t6", items.last().trackId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
