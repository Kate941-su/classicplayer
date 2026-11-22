package jp.vntech.classic

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import jp.vntech.classic.ui.CmslRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TalkBack で操作できることの機械的な確認（PLAN 1.9）: タップできる要素にはすべて読み上げのラベルがあり、
 * タップ領域は 48dp 以上。実機での確認項目は docs/manual-qa.md。
 */
@RunWith(RobolectricTestRunner::class)
// 画面外の要素はタップ領域が表示範囲で切られるので、全体が収まる縦長の画面で調べる
@Config(qualifiers = "w360dp-h2400dp")
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun SemanticsNodeInteractionCollection.assertLabeledAndLargeEnough(screen: String) {
        fetchSemanticsNodes().forEach { node ->
            val c = node.config
            val label = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() +
                c.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty()
            assertTrue("$screen: ラベルのないタップ要素 $c", label.isNotBlank())
            val minPx = with(compose.density) { 48.dp.roundToPx() } - 1
            val w = node.touchBoundsInRoot.width
            val h = node.touchBoundsInRoot.height
            assertTrue("$screen: 「$label」のタップ領域が小さい（${w}x$h px）", w >= minPx && h >= minPx)
        }
    }

    @Test
    fun `主要な画面のタップ要素にラベルがあり48dp以上`() {
        val player = FakePlayerController()
        compose.setContent { CmslRoot(testContainer(context, tmp.root, player)) }
        compose.onAllNodes(hasClickAction()).assertLabeledAndLargeEnough("ホーム")

        compose.onNodeWithTag("home").performScrollToNode(hasText("Symphonies"))
        compose.onNodeWithText("Symphonies").performClick()
        compose.onAllNodes(hasClickAction()).assertLabeledAndLargeEnough("ジャンル")
        compose.onNodeWithText("L.V.Beethoven").performClick()
        compose.onNodeWithText("交響曲第5番 ハ短調 Op.67 「運命」").performClick()
        compose.onAllNodes(hasClickAction()).assertLabeledAndLargeEnough("作品")

        compose.onNodeWithText(context.getString(R.string.action_play_work)).performClick()
        compose.onNodeWithTag("miniPlayer").performClick()
        compose.onAllNodes(hasClickAction()).assertLabeledAndLargeEnough("プレイヤー")
    }

    @Test
    @Config(qualifiers = "+ja")
    fun `日本語の端末では日本語の UI と日本語名`() {
        val player = FakePlayerController()
        compose.setContent { CmslRoot(testContainer(context, tmp.root, player, english = false)) }
        compose.onNodeWithText("おまかせ再生").assertExists()
        compose.onNodeWithTag("home").performScrollToNode(hasText("ピアノ"))
        assertTrue(compose.onAllNodesWithText("ピアノ").fetchSemanticsNodes().isNotEmpty())
    }
}
