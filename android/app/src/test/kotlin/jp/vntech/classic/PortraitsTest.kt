package jp.vntech.classic

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import jp.vntech.classic.core.model.Catalog
import jp.vntech.classic.core.model.CatalogJson
import jp.vntech.classic.ui.CmslRoot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** 作曲家の肖像画（PLAN 1.11、SPEC §4.10）: あり・なし・取得失敗と、クレジットの表示。 */
@RunWith(RobolectricTestRunner::class)
class PortraitsTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val fixtures = File(requireNotNull(System.getProperty("fixturesDir")))
    private val beethovenJpeg = File(fixtures, "../site/v1/portraits/l-v-beethoven.jpg").readBytes()

    private fun artworkOf(trackId: String, container: jp.vntech.classic.di.AppContainer): ByteArray? =
        runBlocking { container.playbackEnvironment.resolve(listOf(trackId)).single().artworkData }

    @Test
    fun `肖像画のある作曲家の曲は、通知に同梱の JPEG を使う`() {
        val container = testContainer(context, tmp.newFolder(), FakePlayerController())
        assertArrayEquals(beethovenJpeg, artworkOf(SONATA, container))
        assertArrayEquals(beethovenJpeg, runBlocking { container.playbackEnvironment.artwork(SONATA) })
        assertNotNull(runBlocking { container.portraits.bitmap("l-v-beethoven") })
    }

    @Test
    fun `肖像画を取得できなければ生成アートワークの PNG`() {
        // 同梱がなく、配信元にも届かない（example.invalid）
        val container = testContainer(context, tmp.newFolder(), FakePlayerController(), portraitsDir = tmp.newFolder())
        assertPng(artworkOf(SONATA, container))
        assertNull(runBlocking { container.playbackEnvironment.artwork(SONATA) })
        assertNull(runBlocking { container.portraits.bitmap("l-v-beethoven") })
    }

    @Test
    fun `肖像画のない作曲家は生成アートワークで、クレジットにも載せない`() {
        val sample = CatalogJson.decodeCatalog(File(fixtures, "catalog.sample.json").readText())
        val without = sample.copy(
            composers = sample.composers.map { if (it.id == "l-v-beethoven") it.copy(portrait = null) else it },
        )
        val seed = tmp.newFile("seed.json").apply {
            writeText(CatalogJson.json.encodeToString(Catalog.serializer(), without))
        }
        val container = testContainer(context, tmp.newFolder(), FakePlayerController(), seed = seed)
        assertNull(container.portraits.portraitOf("l-v-beethoven"))
        assertPng(artworkOf(SONATA, container))
        assertNull(runBlocking { container.playbackEnvironment.artwork(SONATA) })

        compose.setContent { CmslRoot(container) }
        openImageCredits()
        scrollToCredit("Sergei Vasil'evich Rachmaninov")
        compose.onNodeWithText("L.V.Beethoven").assertDoesNotExist()
    }

    @Test
    fun `画像のクレジット画面に、加工したことと作曲家ごとのクレジットを出す`() {
        val container = testContainer(context, tmp.newFolder(), FakePlayerController())
        compose.setContent { CmslRoot(container) }
        openImageCredits()
        compose.onNodeWithText(context.getString(R.string.image_credits_intro)).assertExists()
        scrollToCredit("L.V.Beethoven")
        compose.onNodeWithText(context.getString(R.string.image_credit_artist, "Joseph Karl Stieler")).assertExists()
    }

    @Test
    fun `作曲家詳細に肖像画のクレジットを出す`() {
        val container = testContainer(context, tmp.newFolder(), FakePlayerController())
        compose.setContent { CmslRoot(container) }
        compose.onNodeWithTag("home").performScrollToNode(hasText("Symphonies"))
        compose.onNodeWithText("Symphonies").performClick()
        compose.onNodeWithText("L.V.Beethoven").performClick()
        compose.onNodeWithText(context.getString(R.string.portrait_credit, "Joseph Karl Stieler", "Public domain"))
            .assertExists()
    }

    private fun openImageCredits() {
        compose.onNodeWithText(context.getString(R.string.tab_info)).performClick()
        val label = context.getString(R.string.image_credits)
        compose.onNodeWithTag("info").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
        compose.onNodeWithTag("imageCredits").assertExists()
    }

    private fun scrollToCredit(name: String) {
        compose.onNodeWithTag("imageCredits").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).assertExists()
    }

    private fun assertPng(bytes: ByteArray?) {
        assertNotNull(bytes)
        assertEquals("PNG", bytes!!.copyOfRange(1, 4).decodeToString())
    }

    private companion object {
        /** ベートーヴェンのピアノソナタ（悲愴）。 */
        const val SONATA = "474143449"
    }
}
