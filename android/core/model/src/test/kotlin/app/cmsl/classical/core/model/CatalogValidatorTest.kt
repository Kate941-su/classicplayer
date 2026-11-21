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

    @Test
    fun `肖像画はなくても通り、パスとハッシュの形式が違えば拒否する`() {
        val sample = Fixtures.sample()
        val beethoven = sample.composers.first { it.id == "l-v-beethoven" }
        val portrait = requireNotNull(beethoven.portrait)
        assertEquals("portraits/l-v-beethoven.jpg", portrait.url)
        fun with(p: Portrait?) = sample.copy(
            composers = sample.composers.map { if (it == beethoven) it.copy(portrait = p) else it }
        )
        assertEquals(emptyList<String>(), CatalogValidator.validate(with(null)))
        assertTrue(CatalogValidator.validate(with(portrait.copy(url = "portraits/j-s-bach.jpg"))).isNotEmpty())
        assertTrue(CatalogValidator.validate(with(portrait.copy(url = "../catalog.json"))).isNotEmpty())
        assertTrue(CatalogValidator.validate(with(portrait.copy(sha256 = "ABC"))).isNotEmpty())
        assertTrue(CatalogValidator.validate(with(portrait.copy(sourceUrl = "https://example.com/a"))).isNotEmpty())
    }
}
