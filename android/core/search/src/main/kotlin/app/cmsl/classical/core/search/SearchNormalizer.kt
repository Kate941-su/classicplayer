package app.cmsl.classical.core.search

import java.text.Normalizer
import java.util.Locale

/** 検索の正規化。規則の正本は `docs/search-normalization.md`（iOS と共通）。 */
object SearchNormalizer {
    private const val HIRAGANA_FIRST = 'ぁ'
    private const val HIRAGANA_LAST = 'ゖ'
    private const val KATAKANA_OFFSET = 0x60
    private const val COMBINING_FIRST = '̀'
    private const val COMBINING_LAST = 'ͯ'

    private val VU = listOf("ヴァ" to "バ", "ヴィ" to "ビ", "ヴェ" to "ベ", "ヴォ" to "ボ", "ヴュ" to "ビュ", "ヴ" to "ブ")
    private val REMOVED: Set<Char> = "ー・-‐–—_.,'\"/:;!?()[]{}「」『』【】、。〜~".toSet()
    private val TERM_SEPARATOR = Regex("""[\s　]+""")

    fun normalize(text: String): String {
        var s = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        s = stripLatinDiacritics(s)
        s = buildString(s.length) {
            s.forEach { c -> append(if (c in HIRAGANA_FIRST..HIRAGANA_LAST) c + KATAKANA_OFFSET else c) }
        }
        VU.forEach { (from, to) -> s = s.replace(from, to) }
        return s.filterNot { it.isWhitespace() || it == '　' || it in REMOVED }
    }

    /** 検索語を語に分けて正規化する。空になった語は捨てる。 */
    fun terms(query: String): List<String> =
        query.split(TERM_SEPARATOR).map(::normalize).filter { it.isNotEmpty() }

    private fun stripLatinDiacritics(s: String): String {
        val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
        if (decomposed.none { it in COMBINING_FIRST..COMBINING_LAST }) return s
        return Normalizer.normalize(decomposed.filterNot { it in COMBINING_FIRST..COMBINING_LAST }, Normalizer.Form.NFC)
    }
}
