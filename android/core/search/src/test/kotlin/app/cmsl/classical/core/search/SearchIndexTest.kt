package app.cmsl.classical.core.search

import app.cmsl.classical.core.model.CatalogIndex
import app.cmsl.classical.core.model.CatalogJson
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SearchIndexTest {
    private val catalog = CatalogIndex(
        CatalogJson.decodeCatalog(
            File(requireNotNull(System.getProperty("fixturesDir")), "catalog.sample.json").readText()
        ),
    )
    private val index = SearchIndex(catalog)

    @Test
    fun `主キーで一致した曲を、作曲家名だけで一致した曲より前に出す`() {
        val tracks = index.search("悲愴").tracks.map { it.id }
        assertEquals(listOf("900000001", "900000002", "900000003"), tracks)
    }

    @Test
    fun `作品の曲は楽章順`() {
        val tracks = index.search("ピアノソナタ").tracks.map { it.movement }
        assertEquals(listOf("第1楽章", "第2楽章", "第3楽章"), tracks)
    }
}
