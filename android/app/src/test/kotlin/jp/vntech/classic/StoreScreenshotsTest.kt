package jp.vntech.classic

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
import jp.vntech.classic.ui.CmslRoot
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
 * ストアの掲載用の素の画面（公開中のカタログ site/v1/catalog.json、ネイティブ描画）。
 * `./gradlew :app:testDebugUnitTest --tests '*StoreScreenshotsTest' -Pcmsl.storeScreenshots` のときだけ、
 * `build/store_screenshots/raw-android/<ja|en>/<phone|tablet>/<name>.png` に保存する（fastlane/store_assets.json）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshotsTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val outRoot = System.getProperty("storeScreenshotsDir")?.let(::File)
    private val seed = System.getProperty("storeSeedCatalog")?.let(::File)

    private fun save(dir: File, name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun capture(language: String, kind: String) {
        assumeTrue("記録するときだけ実行する", outRoot != null)
        val english = language == "en"
        val dir = File(outRoot, "$language/$kind")
        val player = FakePlayerController()
        compose.setContent { CmslRoot(testContainer(context, tmp.root, player, english, seed = seed)) }
        save(dir, "home")
        val symphonies = if (english) "Symphonies" else "交響曲"
        compose.onNodeWithTag("home").performScrollToNode(hasText(symphonies))
        compose.onNodeWithText(symphonies).performClick()
        save(dir, "genre")
        compose.onNodeWithText(if (english) "L.V.Beethoven" else "ベートーヴェン").performClick()
        save(dir, "composer")
        compose.onNodeWithText("交響曲第5番 ハ短調 Op.67 「運命」", substring = true, useUnmergedTree = true).performClick()
        compose.onNodeWithText(context.getString(R.string.action_play_work)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_download_work)).performClick()
        save(dir, "work")
        compose.onNodeWithTag("miniPlayer").performClick()
        save(dir, "player")
    }

    @Test
    @Config(qualifiers = "ja-w411dp-h891dp-xxhdpi")
    fun `日本語・スマホ`() = capture("ja", "phone")

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun `英語・スマホ`() = capture("en", "phone")

    @Test
    @Config(qualifiers = "ja-w800dp-h1280dp-xhdpi")
    fun `日本語・タブレット`() = capture("ja", "tablet")

    @Test
    @Config(qualifiers = "en-w800dp-h1280dp-xhdpi")
    fun `英語・タブレット`() = capture("en", "tablet")
}
