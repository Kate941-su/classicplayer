package app.cmsl.classical.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogIndexTest {
    private val index = CatalogIndex(Fixtures.sample())

    @Test
    fun `ジャンルは order 順`() {
        assertEquals(
            listOf("symphony", "concerto", "piano", "chamber", "vocal", "other"),
            index.genres.map { it.id },
        )
    }

    @Test
    fun `作品の曲は楽章番号順`() {
        // 1 記事に mp3 が 2 つ（500111088-0・-1）と、別の記事の続き
        val ids = index.tracksOfWork(FATE).map { it.id }
        assertEquals(listOf("500111088-0", "500111088-1", "500150417"), ids)
        // 新しい録音（2023、1 記事に 2 曲）と旧サーバーの録音（2006）が混ざった作品も楽章順
        val seventh = index.tracksOfWork(SEVENTH)
        assertEquals(listOf("499616036-0", "222057312", "222058571", "499616036-1"), seventh.map { it.id })
        assertEquals(listOf("第1楽章", "第2楽章", "第3楽章", "第4楽章"), seventh.map { it.movement })
    }

    @Test
    fun `新しい録音に置き換えた曲 ID は置き換え先に読み替える`() {
        assertEquals("500111088-0", index.currentTrackId("427916377")) // 運命 2015 → 2023
        assertEquals("500111088-0", index.currentTrackId("500111088-0")) // 今ある ID はそのまま
        assertEquals("1", index.currentTrackId("1")) // どこにもない ID もそのまま（グレー表示）
        assertEquals(
            mapOf("427916377" to "500111088-0"),
            index.replacementsFor(listOf("427916377", "500111088-0", "1")),
        )
    }

    @Test
    fun `作曲家の全曲は作品順・楽章順`() {
        val tracks = index.tracksOf("l-v-beethoven")
        assertEquals(9, tracks.size)
        // 作品はタイトル順（ピアノソナタ 第14番 → 第8番 → 交響曲 第7番 → 交響曲第5番）
        assertEquals("l-v-beethoven--ピアノソナタ第14番嬰ハ短調作品27-2-月光", tracks.first().workId)
        assertEquals(FATE, tracks.last().workId)
    }

    @Test
    fun `ジャンルの作曲家と曲数`() {
        val counts = index.composersOfGenre("piano").associate { it.composer.id to it.trackCount }
        assertEquals(mapOf("l-v-beethoven" to 2, "frederic-francois-chopin" to 2), counts)
    }

    @Test
    fun `新着は公開日時の新しい順で日時のない曲を除く`() {
        val newest = index.newest(10)
        assertEquals(newest.sortedByDescending { instantKey(it.publishedAt!!) }, newest)
        assertEquals(10, newest.size) // 上限
        assertEquals("502781377-0", newest.first().id) // 2024-03-25
        val all = index.newest(100)
        assertEquals(index.catalog.tracks.count { it.publishedAt != null }, all.size)
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
        assertEquals("作曲者不明", Composer("x", "作曲者不明", nameEn = null, trackCount = 1).displayName(english = true))
    }

    @Test
    fun `英語の短い曲名は楽章の印から`() {
        // 実データの書式: 全角コロン、「I:」、コロンなしの「3. …」
        assertEquals("II. Adagio cantabile", track("474143449").shortTitle(english = true))
        assertEquals("第2楽章", track("474143449").shortTitle(english = false))
        assertEquals("I. Allegro non troppo", track("499179250").shortTitle(english = true))
        assertEquals("3. Allegro - 4. Allegro-Presto", track("500150417").shortTitle(english = true))
        val roman = track("499179250").copy(titleEn = "Antonín Dvořák：Symphony No.9 \"From the New World\" I: Adagio")
        assertEquals("I: Adagio", roman.shortTitle(english = true))
        // 楽章のない単曲は作曲家の後ろの英語の曲名。英語タイトルがなければ日本語の楽章
        assertEquals("Recuerdos de la Alhambra", track("474475833").shortTitle(english = true))
        assertEquals("第2楽章", track("476156249-1").shortTitle(english = true))
        // 半角コロンの書式（SPEC §3.2 の例）
        val ascii = track("474143449").copy(titleEn = "L.V.Beethoven:Piano Sonata No.8, Op.13:I. Grave - Allegro")
        assertEquals("I. Grave - Allegro", ascii.shortTitle(english = true))
    }

    @Test
    fun `作品名は英語タイトルの楽章の前まで、なければ作品のタイトル`() {
        fun title(id: String, english: Boolean) = track(id).let { it.workTitle(english, index.work(it.workId)) }
        assertEquals("Piano Sonata No.14 in C sharp minor, Op.27-2 \"Moonlight\"", title("501651167", english = true))
        assertEquals("Symphony No.5 in C minor, Op.67", title("500150417", english = true))
        assertEquals("Recuerdos de la Alhambra", title("474475833", english = true))
        assertEquals("交響曲 第4番 ホ短調 Op.98", title("476156249-1", english = true))
        assertEquals("ピアノソナタ 第14番 嬰ハ短調 作品27-2 「月光」", title("501651167", english = false))
    }

    private fun track(id: String) = index.catalog.tracks.single { it.id == id }

    private companion object {
        const val FATE = "l-v-beethoven--交響曲第5番ハ短調op-67-運命"
        const val SEVENTH = "l-v-beethoven--交響曲第7番イ長調op-92"
    }
}
