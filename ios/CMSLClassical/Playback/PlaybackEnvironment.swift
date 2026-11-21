import Foundation

/// 再生がアプリから受け取るもの（Android の `PlaybackEnvironment` と同じ役割）。再生はカタログや DB を直接知らない。
@MainActor
protocol PlaybackEnvironment: AnyObject {
    /// track.id から再生に必要な情報を作る（カタログの読み込みを待ってよい）。見つからない ID は結果に含めない。
    /// 新しい録音に置き換えた曲は置き換え先を返す（保存したキューの読み替え。SPEC §6）。
    func resolve(_ trackIds: [String]) async -> [PlayableTrack]

    func loadQueue() async -> QueueSnapshot?

    func saveQueue(_ snapshot: QueueSnapshot) async

    /// 曲の再生が始まった（履歴に記録する）。
    func trackStarted(_ trackId: String) async
}

/// ロック画面に出す情報と、音源の URL。
struct PlayableTrack: Equatable, Sendable {
    /// 置き換え後の ID（`requestedId` と違うことがある）。
    var id: String
    var requestedId: String
    var audioURL: URL
    var title: String
    var composer: String
    var workTitle: String
    var artworkPNG: Data?
}
