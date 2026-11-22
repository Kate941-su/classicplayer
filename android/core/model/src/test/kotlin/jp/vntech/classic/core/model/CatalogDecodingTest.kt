package jp.vntech.classic.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogDecodingTest {
    @Test
    fun `サンプルフィクスチャをデコードできる`() {
        val catalog = Fixtures.sample()
        assertEquals(2, catalog.schemaVersion)
        assertTrue(catalog.replacedTracks.isNotEmpty())
        assertEquals(catalog.tracks.size, catalog.genres.sumOf { it.trackCount })
        assertTrue(catalog.license.credit.isNotBlank())
        assertTrue(catalog.tracks.all { it.audioUrl.endsWith(".mp3") })
    }

    @Test
    fun `null と空配列を扱える`() {
        val catalog = Fixtures.sample()
        assertTrue(catalog.tracks.any { it.titleEn == null })
        assertTrue(catalog.tracks.any { it.movement == null })
        assertTrue(catalog.tracks.any { it.tags.isEmpty() })
        assertNull(catalog.tracks.first().durationSec)
        // 実カタログ（2026-11-21）には publishedAt と nameEn が null のものがないので、書き換えて確かめる
        val text = Fixtures.text("catalog.sample.json")
            .replaceFirst(Regex(""""publishedAt": "[^"]+""""), "\"publishedAt\": null")
            .replaceFirst(Regex(""""nameEn": "L\.V\.Beethoven""""), "\"nameEn\": null")
        val nulls = CatalogJson.decodeCatalog(text)
        assertNull(nulls.tracks.first().publishedAt)
        assertNull(nulls.composers.single { it.id == "l-v-beethoven" }.nameEn)
    }

    @Test
    fun `肖像画をデコードでき、キーのない古いカタログは null になる`() {
        val catalog = Fixtures.sample()
        val portrait = requireNotNull(catalog.composers.single { it.id == "l-v-beethoven" }.portrait)
        assertEquals("portraits/l-v-beethoven.jpg", portrait.url)
        assertEquals(64, portrait.sha256.length)
        assertTrue(portrait.sourceUrl.startsWith("https://commons.wikimedia.org/"))
        val withoutKey = Fixtures.text("catalog.sample.json").replace(Regex(""", "portrait": \{[^{}]*\}"""), "")
        assertTrue(!withoutKey.contains("\"portrait\""))
        assertTrue(CatalogJson.decodeCatalog(withoutKey).composers.all { it.portrait == null })
    }

    @Test
    fun `未知のキーを無視する`() {
        val text = Fixtures.text("catalog.sample.json")
            .replaceFirst("\"schemaVersion\": 2,", "\"schemaVersion\": 2, \"futureField\": {\"a\": [1, 2]},")
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
