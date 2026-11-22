package jp.vntech.classic

import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import jp.vntech.classic.core.data.CatalogSource
import jp.vntech.classic.core.model.CatalogIndex
import jp.vntech.classic.di.AppContainer
import jp.vntech.classic.ui.CmslRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** ダウンロード（PLAN 1.12、SPEC §5.1）: 再生時にファイルを使う、カタログの更新で消す、作品のダウンロードとライブラリ。 */
@RunWith(RobolectricTestRunner::class)
class DownloadsTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun AppContainer.download(vararg ids: String) {
        val index = requireNotNull(catalog.value).index
        downloads.enqueue(ids.map { requireNotNull(index.track(it)) })
    }

    private fun AppContainer.audioUrlOf(id: String): String =
        runBlocking { playbackEnvironment.resolve(listOf(id)).single().audioUrl }

    @Test
    fun `ダウンロード済みの曲は端末のファイルから再生し、消したら配信元に戻る`() {
        val dir = tmp.newFolder()
        val container = testContainer(context, dir, FakePlayerController())
        val streamUrl = container.audioUrlOf(SONATA)
        assertTrue(streamUrl.startsWith("https://"))

        val changed = mutableListOf<String>()
        val collector = CoroutineScope(Dispatchers.Unconfined).launch {
            container.playbackEnvironment.trackChanges.collect { changed += it }
        }
        container.download(SONATA)

        val file = File(dir, "downloads/$SONATA.mp3")
        assertTrue(file.isFile)
        assertEquals(Uri.fromFile(file).toString(), container.audioUrlOf(SONATA))
        assertEquals(listOf(SONATA), changed)
        // ほかの曲は配信元のまま
        assertTrue(container.audioUrlOf(FATE_1).startsWith("https://"))

        container.downloads.remove(SONATA)
        assertFalse(file.exists())
        assertEquals(streamUrl, container.audioUrlOf(SONATA))
        assertEquals(listOf(SONATA, SONATA), changed)
        collector.cancel()
    }

    @Test
    fun `カタログの更新で置き換わった・消えた曲のファイルを消す（シードのときは消さない）`() {
        val container = testContainer(context, tmp.newFolder(), FakePlayerController())
        container.download(SONATA, FATE_1)
        val state = runBlocking { container.catalog.filterNotNull().first() }
        val catalog = state.index.catalog
        val without = CatalogIndex(catalog.copy(tracks = catalog.tracks.filterNot { it.id == SONATA }))

        container.pruneDownloads(state.copy(index = without, source = CatalogSource.Seed))
        assertNotNull(container.downloads.file(SONATA))

        container.pruneDownloads(state.copy(index = without, source = CatalogSource.Downloaded))
        assertNull(container.downloads.file(SONATA))
        assertNotNull(container.downloads.file(FATE_1))
    }

    @Test
    fun `作品をダウンロードするとライブラリに出て、すべて削除できる`() {
        val container = testContainer(context, tmp.newFolder(), FakePlayerController())
        compose.setContent { CmslRoot(container) }

        compose.onNodeWithTag("home").performScrollToNode(hasText("Symphonies"))
        compose.onNodeWithText("Symphonies").performClick()
        compose.onNodeWithText("L.V.Beethoven").performClick()
        compose.onNodeWithText(FATE).performClick()
        compose.onNodeWithText(context.getString(R.string.action_download_work)).performClick()
        compose.waitForIdle()
        assertEquals(setOf(FATE_1, "500111088-1", "500150417"), container.downloads.state.value.downloaded.keys)
        compose.onNodeWithText(context.getString(R.string.downloads_work_done)).assertExists()

        compose.onNodeWithText(context.getString(R.string.tab_library)).performClick()
        waitForText(context.getString(R.string.library_downloads))
        compose.onNodeWithText(context.getString(R.string.library_downloads)).performClick()
        compose.onNodeWithText(context.getString(R.string.downloads_wifi_only)).assertExists()
        waitForText(context.getString(R.string.downloads_wifi_only))
        // 合計サイズ（3 曲分。フェイクの取得は小さいファイルを書く）
        compose.onNode(hasText(context.getString(R.string.downloads_total, ""), substring = true)).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_remove_all_downloads)).performClick()
        // 確かめるダイアログの「すべて削除」
        compose.onAllNodesWithText(context.getString(R.string.action_remove_all_downloads))[1].performClick()
        compose.waitForIdle()
        assertEquals(emptyMap<String, Long>(), container.downloads.state.value.downloaded)
        compose.onNodeWithText(context.getString(R.string.library_downloads_empty)).assertExists()
    }

    /** Room・DataStore は別スレッドで読むので、表示されるまで待つ。 */
    private fun waitForText(text: String) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val SONATA = "474143449"
        const val FATE = "交響曲第5番 ハ短調 Op.67 「運命」"
        const val FATE_1 = "500111088-0"
        const val WAIT_MS = 5_000L
    }
}
