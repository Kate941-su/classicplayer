package app.cmsl.classical.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkSpecTest {
    @Test
    fun `同じ作曲家とジャンルなら常に同じ見た目`() {
        val a = ArtworkSpec.of("l-v-beethoven", "piano")
        repeat(10) { assertEquals(a, ArtworkSpec.of("l-v-beethoven", "piano")) }
    }

    @Test
    fun `同じ作曲家ならジャンルが違っても色相は同じでパターンが変わる`() {
        val piano = ArtworkSpec.of("l-v-beethoven", "piano")
        val symphony = ArtworkSpec.of("l-v-beethoven", "symphony")
        assertEquals(piano.hue, symphony.hue)
        assertEquals(piano.variant, symphony.variant)
        assertEquals(ArtworkPattern.Keys, piano.pattern)
        assertEquals(ArtworkPattern.Rings, symphony.pattern)
    }

    @Test
    fun `作曲家が違えば色相が変わる`() {
        assertNotEquals(ArtworkSpec.of("l-v-beethoven", "piano").hue, ArtworkSpec.of("f-chopin", "piano").hue)
    }

    @Test
    fun `未知のジャンルは斜めの縞`() {
        assertEquals(ArtworkPattern.Stripes, ArtworkSpec.of("x", "unknown-genre").pattern)
        assertEquals(ArtworkPattern.Stripes, ArtworkSpec.of("x", "other").pattern)
    }

    /** iOS の実装と同じ値になることを確かめるための既知の値（docs/artwork.md）。 */
    @Test
    fun `FNV-1a の既知の値`() {
        assertEquals(0x811c9dc5u, ArtworkSpec.fnv1a32(""))
        assertEquals(0xe40c292cu, ArtworkSpec.fnv1a32("a"))
        assertEquals(0xbf9cf968u, ArtworkSpec.fnv1a32("foobar"))
        val beethoven = ArtworkSpec.fnv1a32("l-v-beethoven")
        assertEquals(
            ArtworkSpec((beethoven % 360u).toInt(), ArtworkPattern.Keys, ((beethoven shr 16) and 0xffu).toInt()),
            ArtworkSpec.of("l-v-beethoven", "piano")
        )
        // 日本語の ID（UTF-8 のバイト列でハッシュする）
        val unknown = ArtworkSpec.of("作曲者不明", "other")
        assertTrue(unknown.hue in 0..359)
        assertTrue(unknown.variant in 0..255)
    }
}
