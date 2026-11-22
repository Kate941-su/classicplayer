package jp.vntech.classic.core.model

/**
 * 英語 UI では `nameEn` / `titleEn` を優先し、なければ日本語にフォールバックする（SPEC §4.11）。
 */
fun Genre.displayName(english: Boolean): String = if (english) nameEn ?: name else name

fun Composer.displayName(english: Boolean): String = if (english) nameEn ?: name else name

fun Track.displayTitle(english: Boolean): String = if (english) titleEn ?: title else title

/**
 * 作品画面の行に出す短い名前（SPEC §4.11）。日本語は楽章名。英語は `titleEn` の作曲家の区切りより後ろから、
 * 最初の楽章の印（`II. Adagio`・`I: Adagio`・`3. Allegro`）以降。印がなく楽章もない単曲は、区切りより後ろの英語の曲名。
 * どれもなければタイトル全体。
 */
fun Track.shortTitle(english: Boolean): String {
    if (english) {
        titleEnAfterComposer()?.let { en ->
            MOVEMENT_MARK.find(en)?.let { return it.groupValues[1].trim() }
            if (movement == null) return en
        }
    }
    return movement ?: displayTitle(english)
}

/**
 * 曲が属する作品の名前（ホームの「新着」など、楽章名だけでは何の曲か分からない所に出す）。
 * 英語は `titleEn` の作曲家の区切りより後ろから楽章の印の前まで（`Piano Sonata No.14 … "Moonlight"`）。
 * なければ作品の `title`、作品がなければ曲のタイトル。
 */
fun Track.workTitle(english: Boolean, work: Work?): String {
    if (english) {
        titleEnAfterComposer()?.let { en ->
            val name = MOVEMENT_MARK.find(en)?.let { en.substring(0, it.range.first) } ?: en
            name.trim().trimEnd(':', '：').trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
    }
    return work?.title ?: displayTitle(english)
}

/** `titleEn` の作曲家の区切り（最初の `:` か `：`）より後ろ。 */
private fun Track.titleEnAfterComposer(): String? = titleEn?.let { en ->
    en.substring(en.indexOfFirst { it == ':' || it == '：' } + 1).trim().takeIf { it.isNotEmpty() }
}

/** 楽章の印。ローマ数字か数字の直後に「.」か「:」と空白。前は行頭・空白・コロン（「No.9 in」「Op.27-2」は当たらない）。 */
private val MOVEMENT_MARK = Regex("""(?:^|[\s:：])((?:[IVX]+|\d+)[.:]\s.*)$""")
