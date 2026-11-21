package app.cmsl.classical

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.cmsl.classical.core.playback.PlayQueue
import app.cmsl.classical.ui.CmslRoot
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
        compose.onNodeWithText(FATE_2015).performClick()
        compose.onNodeWithText(context.getString(R.string.action_play_work)).performClick()

        assertEquals(listOf(PlayQueue(listOf("427916377", "428637877", "431342999"), 0)), player.played)
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
        compose.onNodeWithText(FATE_2015).performClick()
        compose.onNodeWithText("第2楽章").performClick() // 英語タイトルのない曲は日本語の楽章名

        assertEquals(listOf(PlayQueue(listOf("428637877", "431342999"), 0)), player.played)
    }

    private companion object {
        const val FATE_2015 = "交響曲 第5番 ハ短調 Op.67 「運命」（2015）"
    }
}
