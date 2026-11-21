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
        // 「L.V.Beethoven」で始まる英語タイトル（主キー）の 2 曲が先。英語タイトルのない「運命」は作曲家の英語名（副キー）で一致
        val tracks = index.search("L.V.Beethoven").tracks.map { it.id }
        assertEquals(setOf("501651167", "474143449"), tracks.take(2).toSet())
        assertEquals(
            listOf("427916377", "428637877", "431342999", "500111088-0", "500111088-1", "500150417"),
            tracks.drop(2),
        )
    }

    @Test
    fun `作品の曲は楽章順`() {
        val tracks = index.search("ブラームス").tracks.map { it.movement }
        assertEquals(listOf("第1楽章", "第2楽章", "第3楽章", "第4楽章"), tracks)
    }
}
