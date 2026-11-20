package app.cmsl.classical.core.model

/**
 * 楽章名から並び順の番号を取り出す（SPEC §4.5）。「第2楽章」「第２楽章」「第二楽章」→ 2。取れなければ null。
 */
object Movement {
    private const val TEN = 10
    private val DIGITS = Regex("""[0-9０-９]+""")
    private val KANJI = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )

    fun number(movement: String?): Int? {
        if (movement.isNullOrBlank()) return null
        DIGITS.find(movement)?.let { m ->
            return m.value.map { c -> if (c in '０'..'９') c - '０' else c - '0' }.fold(0) { acc, d -> acc * TEN + d }
        }
        return kanjiNumber(movement)
    }

    /** 「第十二楽章」まで（十の位は「十」「二十」…）。 */
    private fun kanjiNumber(text: String): Int? {
        val start = text.indexOfFirst { it in KANJI || it == '十' }
        if (start < 0) return null
        var i = start
        var value = 0
        var current = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '十' -> {
                    value += (if (current == 0) 1 else current) * TEN
                    current = 0
                }
                c in KANJI -> current = KANJI.getValue(c)
                else -> break
            }
            i++
        }
        return (value + current).takeIf { it > 0 }
    }
}
