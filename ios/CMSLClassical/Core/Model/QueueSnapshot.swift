/// 再生キューと位置（SPEC §5「状態の復元」）。永続化するのは track.id だけ。
struct QueueSnapshot: Codable, Equatable, Sendable {
    var trackIds: [String]
    var index: Int
    var positionMs: Int64
    var shuffle: Bool = false
    var repeatMode: RepeatMode = .off
}

enum RepeatMode: String, Codable, Sendable, CaseIterable {
    case off = "Off", all = "All", one = "One"
}
