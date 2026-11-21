package app.cmsl.classical.core.playback

import app.cmsl.classical.core.model.QueueSnapshot
import app.cmsl.classical.core.model.RepeatMode
import app.cmsl.classical.core.model.Track
import kotlin.random.Random

/** 再生を始めるときのキュー。`startIndex` の曲から再生する。 */
data class PlayQueue(val trackIds: List<String>, val startIndex: Int = 0) {
    init {
        require(trackIds.isEmpty() || startIndex in trackIds.indices) { "startIndex が範囲外: $startIndex" }
    }
}

/** 画面の操作からキューを作る（SPEC §4.4・§4.5・§5）。曲の並びは呼び出し側（CatalogIndex）で決まっている。 */
object QueueBuilder {
    /** 作品を通して再生。`startTrackId` を指定すると、その曲から作品の最後まで（SPEC §4.5）。 */
    fun fromWork(tracksInOrder: List<Track>, startTrackId: String? = null): PlayQueue {
        val start = startTrackId?.let { id -> tracksInOrder.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
        return PlayQueue(tracksInOrder.drop(start).map { it.id })
    }

    /** 一覧（お気に入り・履歴・検索結果・作曲家のすべて）をそのまま、`startIndex` の曲から。 */
    fun fromList(tracks: List<Track>, startIndex: Int = 0): PlayQueue =
        PlayQueue(tracks.map { it.id }, startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)))

    /** シャッフル（おまかせ再生・ジャンルのシャッフル）。 */
    fun shuffled(tracks: List<Track>, random: Random = Random.Default): PlayQueue =
        PlayQueue(tracks.map { it.id }.shuffled(random))
}

/** 再生できない曲が続いたら止める（SPEC §5「エラー」）。 */
class ErrorSkipPolicy(private val maxConsecutiveErrors: Int = 3) {
    private var consecutive = 0

    enum class Decision { Skip, Stop }

    fun onError(hasNext: Boolean): Decision {
        consecutive++
        return if (hasNext && consecutive < maxConsecutiveErrors) Decision.Skip else Decision.Stop.also { consecutive = 0 }
    }

    /** 再生の準備ができたら数え直す。 */
    fun onReady() {
        consecutive = 0
    }
}

fun RepeatMode.next(): RepeatMode = when (this) {
    RepeatMode.Off -> RepeatMode.All
    RepeatMode.All -> RepeatMode.One
    RepeatMode.One -> RepeatMode.Off
}

/**
 * 保存しておいたキューを、今のカタログで復元できる形にする。カタログから消えた曲は外し、
 * 位置は「再生していた曲」に合わせる（その曲が消えていたら、次に残っている曲の先頭）。
 */
fun QueueSnapshot.restorable(isAvailable: (String) -> Boolean): QueueSnapshot? {
    val kept = trackIds.withIndex().filter { isAvailable(it.value) }
    if (kept.isEmpty()) return null
    val current = kept.indexOfFirst { it.index == index }
    val newIndex = if (current >= 0) current else kept.indexOfFirst { it.index > index }.takeIf { it >= 0 } ?: 0
    return copy(
        trackIds = kept.map { it.value },
        index = newIndex,
        positionMs = if (current >= 0) positionMs.coerceAtLeast(0) else 0,
    )
}
