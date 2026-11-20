package app.cmsl.classical.core.model

/**
 * 英語 UI では `nameEn` / `titleEn` を優先し、なければ日本語にフォールバックする（SPEC §4.11）。
 */
fun Genre.displayName(english: Boolean): String = if (english) nameEn ?: name else name

fun Composer.displayName(english: Boolean): String = if (english) nameEn ?: name else name

fun Track.displayTitle(english: Boolean): String = if (english) titleEn ?: title else title

/**
 * 作品画面の行に出す短い名前。日本語は楽章名、英語は `titleEn` の最後のコロンより後ろ（「I. Allegro」など）。
 * どちらもなければタイトル全体。
 */
fun Track.shortTitle(english: Boolean): String {
    if (english) {
        titleEn?.substringAfterLast(':', "")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return movement ?: displayTitle(english)
}
