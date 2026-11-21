package app.cmsl.classical.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogValidatorTest {
    @Test
    fun `サンプルは検査を通る`() {
        assertEquals(emptyList<String>(), CatalogValidator.validate(Fixtures.sample()))
    }

    @Test
    fun `異常系のフィクスチャはすべて拒否する`() {
        val invalid = listOf(
            "duplicate-track-id.json",
            "dangling-work-ref.json",
            "foreign-audio-domain.json",
            "schema-version-3.json",
            "trackcount-mismatch.json",
        )
        invalid.forEach { name ->
            val catalog = CatalogJson.decodeCatalog(Fixtures.text("invalid/$name"))
            assertTrue("$name が通ってしまった", CatalogValidator.validate(catalog).isNotEmpty())
        }
    }

    @Test
    fun `旧サーバーは http だけ通し、置き換え先のない置き換えは拒否する`() {
        val sample = Fixtures.sample()
        assertTrue(sample.tracks.any { it.audioUrl.startsWith("http://") })
        fun withAudio(url: String) =
            sample.copy(tracks = listOf(sample.tracks[0].copy(audioUrl = url)) + sample.tracks.drop(1))
        assertTrue(CatalogValidator.validate(withAudio("https://classical-music.aki.gs/a.mp3")).isNotEmpty())
        assertTrue(CatalogValidator.validate(withAudio("http://classical-sound.up.seesaa.net/a.mp3")).isNotEmpty())
        val dangling = sample.copy(replacedTracks = mapOf("1" to "2"))
        assertEquals(listOf("replacedTracks 1: 置き換え先 2 がない"), CatalogValidator.validate(dangling))
    }
}
