package app.cmsl.classical

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.cmsl.classical.core.db.FavoriteEntity
import app.cmsl.classical.core.playback.PlayQueue
import app.cmsl.classical.ui.CmslRoot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 主要フロー: ホーム → ジャンル → 作曲家 → 作品 → 再生開始（PLAN 1.7）。 */
@RunWith(RobolectricTestRunner::class)
class HomeToPlaybackTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `ホームから作品を開いて作品を通して再生する`() {
        val player = FakePlayerController()
        val container = testContainer(context, tmp.root, player)
        compose.setContent { CmslRoot(container) }

        compose.onNodeWithTag("home").performScrollToNode(hasText("Symphonies"))
        compose.onNodeWithText("Symphonies").performClick()
        compose.onNodeWithText("L.V.Beethoven").performClick()
        compose.onNodeWithText(FATE).performClick()
        compose.onNodeWithText(context.getString(R.string.action_play_work)).performClick()

        assertEquals(listOf(PlayQueue(listOf("500111088-0", "500111088-1", "500150417"), 0)), player.played)
        compose.onNodeWithTag("miniPlayer").assertExists()
    }

    @Test
    fun `作品の途中の楽章をタップするとその曲から最後まで`() {
        val player = FakePlayerController()
        val container = testContainer(context, tmp.root, player)
        compose.setContent { CmslRoot(container) }

        compose.onNodeWithTag("home").performScrollToNode(hasText("Symphonies"))
        compose.onNodeWithText("Symphonies").performClick()
        compose.onNodeWithText("L.V.Beethoven").performClick()
        compose.onNodeWithText(FATE).performClick()
        compose.onNodeWithTag("work").performScrollToNode(hasText("第2楽章"))
        compose.onNodeWithText("第2楽章").performClick() // 英語タイトルのない曲は日本語の楽章名

        assertEquals(listOf(PlayQueue(listOf("500111088-1", "500150417"), 0)), player.played)
    }

    @Test
    fun `新しい録音に置き換えた曲のお気に入りと履歴を読み替える`() = runBlocking {
        val container = testContainer(context, tmp.root, FakePlayerController())
        val db = container.database
        db.favorites().add(FavoriteEntity("427916377", 1)) // 運命 2015 の第1楽章 → 2023 の 500111088-0
        db.history().record("428637877", 2) // 第2楽章 → 500111088-1
        db.favorites().add(FavoriteEntity("1", 3)) // カタログにない曲はそのまま（グレー表示）
        container.replaceStoredTrackIds(container.catalog.value!!.index)
        assertEquals(setOf("500111088-0", "1"), db.favorites().ids().toSet())
        assertEquals(listOf("500111088-1"), db.history().ids())
    }

    private companion object {
        const val FATE = "交響曲第5番 ハ短調 Op.67 「運命」"
    }
}
