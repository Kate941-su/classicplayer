package app.cmsl.classical.core.playback

import app.cmsl.classical.core.model.QueueSnapshot

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

    suspend fun loadQueue(): QueueSnapshot?

    suspend fun saveQueue(snapshot: QueueSnapshot)

    /** 曲の再生が始まった（履歴に記録する）。 */
    suspend fun onTrackStarted(trackId: String)

    interface Provider {
        val playbackEnvironment: PlaybackEnvironment
    }
}

/** ロック画面・通知に出す情報と、音源の URL。 */
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
