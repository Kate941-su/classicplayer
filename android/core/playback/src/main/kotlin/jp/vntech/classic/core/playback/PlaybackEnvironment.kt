package jp.vntech.classic.core.playback

import jp.vntech.classic.core.model.QueueSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 再生サービスがアプリから受け取るもの。Application が [Provider] を実装して渡す。
 * サービスはカタログや DB を直接知らない。
 */
interface PlaybackEnvironment {
    /** track.id から再生に必要な情報を作る（カタログの読み込みを待ってよい）。見つからない ID は結果に含めない。 */
    suspend fun resolve(trackIds: List<String>): List<PlayableTrack>

    /**
     * 曲のアートワーク（作曲家の肖像画の JPEG）を、必要なら取得して返す。肖像画がない・取れないときは null。
     * [resolve] は通信を待たずに作るので、サービスは曲が切り替わったときにこれで差し替える（SPEC §4.10）。
     */
    suspend fun artwork(trackId: String): ByteArray? = null

    /**
     * 再生に使う URI が変わった曲（ダウンロードが終わった・消えた。SPEC §5.1）。
     * サービスはキューのその曲を [resolve] し直して差し替える（再生中の曲はそのまま）。
     */
    val trackChanges: Flow<String> get() = emptyFlow()

    suspend fun loadQueue(): QueueSnapshot?

    suspend fun saveQueue(snapshot: QueueSnapshot)

    /** 曲の再生が始まった（履歴に記録する）。 */
    suspend fun onTrackStarted(trackId: String)

    interface Provider {
        val playbackEnvironment: PlaybackEnvironment
    }
}

/** ロック画面・通知に出す情報と、音源の URL（ダウンロード済みなら file:// の URI）。 */
class PlayableTrack(
    val id: String,
    val audioUrl: String,
    val title: String,
    val composer: String,
    val workTitle: String,
    val genre: String,
    /** 作曲家の肖像画（JPEG）か、なければ生成アートワーク（PNG）。SPEC §4.10。 */
    val artworkData: ByteArray?,
)
