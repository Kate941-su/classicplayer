package app.cmsl.classical.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogDecodingTest {
    @Test
    fun `サンプルフィクスチャをデコードできる`() {
        val catalog = Fixtures.sample()
        assertEquals(1, catalog.schemaVersion)
        assertEquals(catalog.tracks.size, catalog.genres.sumOf { it.trackCount })
        assertTrue(catalog.license.credit.isNotBlank())
        assertTrue(catalog.tracks.all { it.audioUrl.endsWith(".mp3") })
    }

    @Test
    fun `null と空配列を扱える`() {
        val catalog = Fixtures.sample()
        assertTrue(catalog.tracks.any { it.titleEn == null })
        assertTrue(catalog.tracks.any { it.publishedAt == null })
        assertTrue(catalog.tracks.any { it.movement == null })
        assertTrue(catalog.tracks.any { it.tags.isEmpty() })
        assertTrue(catalog.composers.any { it.nameEn == null })
        assertNull(catalog.tracks.first().durationSec)
    }

    @Test
    fun `未知のキーを無視する`() {
        val text = Fixtures.text("catalog.sample.json")
            .replaceFirst("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"futureField\": {\"a\": [1, 2]},")
            .replace("\"durationSec\": null", "\"durationSec\": null, \"bpm\": 120")
        val catalog = CatalogJson.decodeCatalog(text)
        assertEquals(Fixtures.sample(), catalog)
    }

    @Test
    fun `manifest をデコードできる`() {
        val manifest = CatalogJson.decodeManifest(Fixtures.text("manifest.sample.json"))
        assertEquals("catalog.json", manifest.catalogUrl)
        assertEquals(64, manifest.sha256.length)
        assertEquals(Fixtures.sample().tracks.size, manifest.trackCount)
    }
}
