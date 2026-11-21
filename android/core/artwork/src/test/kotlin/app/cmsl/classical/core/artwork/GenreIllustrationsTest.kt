package app.cmsl.classical.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreIllustrationsTest {
    @Test
    fun `カタログの 7 ジャンルにイラストがある`() {
        val genres = listOf("symphony", "orchestral", "concerto", "piano", "chamber", "vocal", "other")
        assertEquals(genres.toSet(), GenreIllustrations.shapes.keys)
        genres.forEach { assertEquals(it, ArtworkSpec.of("x", it).illustration) }
    }

    @Test
    fun `未知のジャンルは音符（その他）`() {
        assertEquals("other", ArtworkSpec.of("x", "baroque").illustration)
        assertEquals("other", ArtworkSpec.of("x", "").illustration)
    }

    @Test
    fun `命令列は M で始まり、引数の数がそろい、座標は 0〜100 に収まる`() {
        val args = mapOf(0 to 2, 1 to 2, 2 to 6, 3 to 0)
        GenreIllustrations.shapes.forEach { (genre, shapes) ->
            assertTrue(genre, shapes.isNotEmpty())
            shapes.forEach { shape -> checkShape(genre, shape, args) }
        }
    }

    private fun checkShape(genre: String, shape: IllustrationShape, args: Map<Int, Int>) {
        assertEquals(genre, 0f, shape.ops.first())
        assertTrue(genre, shape.strokeWidth >= 0f)
        var i = 0
        while (i < shape.ops.size) {
            val n = args[shape.ops[i].toInt()] ?: error("$genre: 不明な命令 ${shape.ops[i]}")
            (1..n).forEach { k -> assertTrue(genre, shape.ops[i + k] in -1f..101f) }
            i += n + 1
        }
        assertEquals(genre, shape.ops.size, i)
    }
}
