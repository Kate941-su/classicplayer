/// 再生中のキュー（並び・シャッフルの順・今の曲・リピート）。AVQueuePlayer はシャッフルとリピートを持たないので、
/// 次に再生する曲はここで決める。同じ曲が 2 回入ってもよいように、項目は `key` で区別する。
struct PlaybackQueue: Equatable, Sendable {
    struct Item: Equatable, Hashable, Sendable {
        let key: Int
        let trackId: String
    }

    /// 表示の順（キュー画面の並び）。
    private(set) var items: [Item] = []
    /// シャッフル中の再生順（item の key）。シャッフルしていなければ items の順。
    private var shuffleOrder: [Int] = []
    private(set) var currentKey: Int?
    private(set) var shuffle = false
    var repeatMode: RepeatMode = .off
    private var nextKey = 0

    init() {}

    init(trackIds: [String], startIndex: Int) {
        items = trackIds.enumerated().map { Item(key: $0.offset, trackId: $0.element) }
        nextKey = items.count
        currentKey = items.indices.contains(startIndex) ? items[startIndex].key : items.first?.key
    }

    var isEmpty: Bool { items.isEmpty }
    var trackIds: [String] { items.map(\.trackId) }
    var currentIndex: Int? { currentKey.flatMap(index(of:)) }
    var currentTrackId: String? { currentIndex.map { items[$0].trackId } }

    private var playOrder: [Int] { shuffle ? shuffleOrder : items.map(\.key) }

    func index(of key: Int) -> Int? { items.firstIndex { $0.key == key } }
    func item(key: Int) -> Item? { items.first { $0.key == key } }

    /// 次に再生する曲。`automatic` は曲が終わって進むとき（1 曲リピートなら同じ曲）。
    func nextKey(automatic: Bool) -> Int? {
        guard let currentKey, let position = playOrder.firstIndex(of: currentKey) else { return nil }
        if automatic, repeatMode == .one { return currentKey }
        if position + 1 < playOrder.count { return playOrder[position + 1] }
        return repeatMode == .off ? nil : playOrder.first
    }

    func previousKey() -> Int? {
        guard let currentKey, let position = playOrder.firstIndex(of: currentKey) else { return nil }
        if position > 0 { return playOrder[position - 1] }
        return repeatMode == .off ? nil : playOrder.last
    }

    mutating func setCurrent(key: Int) {
        if index(of: key) != nil { currentKey = key }
    }

    mutating func setShuffle(_ enabled: Bool, using generator: inout some RandomNumberGenerator) {
        shuffle = enabled
        guard enabled else { shuffleOrder = []; return }
        // 今の曲を先頭に、残りを混ぜる
        var rest = items.map(\.key).filter { $0 != currentKey }
        rest.shuffle(using: &generator)
        shuffleOrder = (currentKey.map { [$0] } ?? []) + rest
    }

    /// 今の曲の次に入れる。
    mutating func insertNext(_ trackId: String) {
        let item = makeItem(trackId)
        guard let currentKey, let index = currentIndex else { append(item); return }
        items.insert(item, at: index + 1)
        if shuffle, let position = shuffleOrder.firstIndex(of: currentKey) {
            shuffleOrder.insert(item.key, at: position + 1)
        }
    }

    mutating func append(_ trackId: String) { append(makeItem(trackId)) }

    private mutating func append(_ item: Item) {
        items.append(item)
        if shuffle { shuffleOrder.append(item.key) }
        if currentKey == nil { currentKey = item.key }
    }

    mutating func move(from: Int, to: Int) {
        guard items.indices.contains(from), items.indices.contains(to), from != to else { return }
        let item = items.remove(at: from)
        items.insert(item, at: to)
    }

    /// 項目を外す。今の曲を外したら、次の曲（なければ前の曲）が今の曲になる。
    mutating func remove(at index: Int) {
        guard items.indices.contains(index) else { return }
        let key = items[index].key
        if key == currentKey {
            let order = playOrder
            let position = order.firstIndex(of: key) ?? 0
            currentKey = position + 1 < order.count ? order[position + 1] : (position > 0 ? order[position - 1] : nil)
        }
        items.remove(at: index)
        shuffleOrder.removeAll { $0 == key }
    }

    private mutating func makeItem(_ trackId: String) -> Item {
        defer { nextKey += 1 }
        return Item(key: nextKey, trackId: trackId)
    }
}
