package app.cmsl.classical.core.db

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cmsl.classical.core.model.QueueSnapshot
import app.cmsl.classical.core.model.RepeatMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreferencesStoresTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataStore(name: String = "prefs") = PreferenceDataStoreFactory.create(
        scope = backgroundScope
    ) {
        File(tmp.root, "$name.preferences_pb")
    }

    @Test
    fun `最近の検索は新しい順・重複なし・最大10件`() = runTest {
        val store = RecentSearchStore(dataStore())
        (1..12).forEach { store.add("q$it") }
        store.add("  q5 ")
        store.add("   ")
        val recent = store.recent.first()
        assertEquals(RecentSearchStore.MAX, recent.size)
        assertEquals(listOf("q5", "q12", "q11"), recent.take(3))
        assertEquals(1, recent.count { it == "q5" })

        store.remove("q12")
        assertEquals(listOf("q5", "q11"), store.recent.first().take(2))
        store.clear()
        assertEquals(emptyList<String>(), store.recent.first())
    }

    @Test
    fun `キューを保存して読み戻す`() = runTest {
        val store = QueueStore(dataStore())
        assertNull(store.load())
        val snapshot = QueueSnapshot(
            listOf("a", "b"),
            index = 1,
            positionMs = 1234,
            shuffle = true,
            repeatMode = RepeatMode.One
        )
        store.save(snapshot)
        assertEquals(snapshot, store.load())
        store.clear()
        assertNull(store.load())
    }
}
