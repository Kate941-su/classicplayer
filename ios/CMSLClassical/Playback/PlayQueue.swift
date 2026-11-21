/// 再生を始めるときのキュー。`startIndex` の曲から再生する。
struct PlayQueue: Equatable, Sendable {
    var trackIds: [String]
    var startIndex: Int = 0
}

/// 画面の操作からキューを作る（SPEC §4.4・§4.5・§5）。曲の並びは呼び出し側（CatalogIndex）で決まっている。
enum QueueBuilder {
    /// 作品を通して再生。`startTrackId` を指定すると、その曲から作品の最後まで（SPEC §4.5）。
    static func fromWork(_ tracksInOrder: [Track], startTrackId: String? = nil) -> PlayQueue {
        let start = startTrackId.flatMap { id in tracksInOrder.firstIndex { $0.id == id } } ?? 0
        return PlayQueue(trackIds: tracksInOrder.dropFirst(start).map(\.id))
    }

    /// 一覧（お気に入り・履歴・検索結果・作曲家のすべて）をそのまま、`startIndex` の曲から。
    static func fromList(_ tracks: [Track], startIndex: Int = 0) -> PlayQueue {
        PlayQueue(trackIds: tracks.map(\.id), startIndex: min(max(startIndex, 0), max(tracks.count - 1, 0)))
    }

    /// シャッフル（おまかせ再生・ジャンルのシャッフル）。
    static func shuffled(_ tracks: [Track], using generator: inout some RandomNumberGenerator) -> PlayQueue {
        PlayQueue(trackIds: tracks.map(\.id).shuffled(using: &generator))
    }

    static func shuffled(_ tracks: [Track]) -> PlayQueue {
        var generator = SystemRandomNumberGenerator()
        return shuffled(tracks, using: &generator)
    }
}

/// 再生できない曲が続いたら止める（SPEC §5「エラー」）。
struct ErrorSkipPolicy {
    enum Decision { case skip, stop }

    let maxConsecutiveErrors: Int
    private var consecutive = 0

    init(maxConsecutiveErrors: Int = 3) { self.maxConsecutiveErrors = maxConsecutiveErrors }

    mutating func onError(hasNext: Bool) -> Decision {
        consecutive += 1
        if hasNext, consecutive < maxConsecutiveErrors { return .skip }
        consecutive = 0
        return .stop
    }

    /// 再生の準備ができたら数え直す。
    mutating func onReady() { consecutive = 0 }
}

extension RepeatMode {
    var next: RepeatMode {
        switch self {
        case .off: .all
        case .all: .one
        case .one: .off
        }
    }
}

extension QueueSnapshot {
    /// 保存しておいたキューを、今のカタログで復元できる形にする。カタログから消えた曲は外し、
    /// 位置は「再生していた曲」に合わせる（その曲が消えていたら、次に残っている曲の先頭）。
    func restorable(isAvailable: (String) -> Bool) -> QueueSnapshot? {
        let kept = trackIds.enumerated().filter { isAvailable($0.element) }
        if kept.isEmpty { return nil }
        let current = kept.firstIndex { $0.offset == index }
        let newIndex = current ?? kept.firstIndex { $0.offset > index } ?? 0
        var result = self
        result.trackIds = kept.map(\.element)
        result.index = newIndex
        result.positionMs = current != nil ? max(positionMs, 0) : 0
        return result
    }
}
