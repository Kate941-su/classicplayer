package app.cmsl.classical

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.cmsl.classical.ui.CmslRoot
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * PR 用のスクリーンショット（フィクスチャのカタログ、ネイティブ描画）。
 * `./gradlew :app:testDebugUnitTest --tests '*ScreenshotsTest' -Pcmsl.recordScreenshots` のときだけ保存する。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotsTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val outDir = System.getProperty("screenshotsDir")?.let(::File)

    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        outDir!!.mkdirs()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun capture(prefix: String, english: Boolean) {
        assumeTrue("記録するときだけ実行する", outDir != null)
        val player = FakePlayerController()
        compose.setContent { CmslRoot(testContainer(context, tmp.root, player, english)) }
        save("$prefix-home")
        val symphonies = if (english) "Symphonies" else "交響曲"
        compose.onNodeWithTag("home").performScrollToNode(hasText(symphonies))
        compose.onNodeWithText(symphonies).performClick()
        save("$prefix-genre")
        compose.onNodeWithText(if (english) "L.V.Beethoven" else "ベートーヴェン").performClick()
        compose.onNodeWithText("交響曲 第5番 ハ短調 Op.67 「運命」（2015）").performClick()
        compose.onNodeWithText(context.getString(R.string.action_play_work)).performClick()
        save("$prefix-work")
        compose.onNodeWithTag("miniPlayer").performClick()
        save("$prefix-player")
    }

    @Test
    @Config(qualifiers = "+ja")
    fun `日本語・ライト`() = capture("ja-light", english = false)

    @Test
    @Config(qualifiers = "+ja-night")
    fun `日本語・ダーク`() = capture("ja-dark", english = false)

    @Test
    @Config(qualifiers = "+en")
    fun `英語・ライト`() = capture("en-light", english = true)
}
