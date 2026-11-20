package app.cmsl.classical.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogIndexTest {
    private val index = CatalogIndex(Fixtures.sample())

    @Test
    fun `ジャンルは order 順`() {
        assertEquals(listOf("symphony", "piano", "other"), index.genres.map { it.id })
    }

    @Test
    fun `作品の曲は楽章番号順`() {
        val ids = index.tracksOfWork("l-v-beethoven--piano-sonata-8").map { it.movement }
        assertEquals(listOf("第1楽章", "第2楽章", "第3楽章"), ids)
    }

    @Test
    fun `作曲家の全曲は作品順・楽章順`() {
        val tracks = index.tracksOf("l-v-beethoven")
        assertEquals(5, tracks.size)
        // 作品はタイトル順（ピアノソナタ → 交響曲）
        assertEquals("l-v-beethoven--piano-sonata-8", tracks.first().workId)
        assertEquals("l-v-beethoven--symphony-5", tracks.last().workId)
    }

    @Test
    fun `ジャンルの作曲家と曲数`() {
        val counts = index.composersOfGenre("piano").associate { it.composer.id to it.trackCount }
        assertEquals(mapOf("l-v-beethoven" to 3, "f-chopin" to 1), counts)
    }

    @Test
    fun `新着は公開日時の新しい順で日時のない曲を除く`() {
        val newest = index.newest(10)
        assertEquals(newest.sortedByDescending { instantKey(it.publishedAt!!) }, newest)
        assertEquals(index.catalog.tracks.count { it.publishedAt != null }, newest.size)
    }

    @Test
    fun `楽章番号の取り出し`() {
        assertEquals(2, Movement.number("第2楽章"))
        assertEquals(12, Movement.number("第１２楽章"))
        assertEquals(3, Movement.number("第三楽章"))
        assertEquals(14, Movement.number("第十四楽章"))
        assertEquals(20, Movement.number("第二十楽章"))
        assertNull(Movement.number("序奏"))
        assertNull(Movement.number(null))
    }

    @Test
    fun `英語 UI では英語名を優先しなければ日本語`() {
        val beethoven = index.composer("l-v-beethoven")!!
        assertEquals("L.V.Beethoven", beethoven.displayName(english = true))
        assertEquals("ベートーヴェン", beethoven.displayName(english = false))
        assertEquals("作曲者不明", index.composer("作曲者不明")!!.displayName(english = true))
        val first = index.tracksOfWork("l-v-beethoven--piano-sonata-8").first()
        assertEquals("I. Grave - Allegro di molto e con brio", first.shortTitle(english = true))
        assertEquals("第1楽章", first.shortTitle(english = false))
    }
}
