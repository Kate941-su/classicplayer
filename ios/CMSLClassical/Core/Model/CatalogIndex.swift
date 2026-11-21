import Foundation

/// カタログを画面から引きやすい形にした索引。生成は 1 回（カタログの差し替え時）だけ。
final class CatalogIndex: Sendable {
    let catalog: Catalog
    let genres: [Genre]

    private let genreById: [String: Genre]
    private let composerById: [String: Composer]
    private let workById: [String: Work]
    private let trackById: [String: Track]
    /// 作品内の曲（楽章番号 → 公開日時 → タイトル → ID の順）。
    private let tracksByWork: [String: [Track]]
    private let worksByComposer: [String: [Work]]

    init(_ catalog: Catalog) {
        self.catalog = catalog
        genres = catalog.genres.sorted { ($0.order, $0.id) < ($1.order, $1.id) }
        genreById = Dictionary(catalog.genres.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        composerById = Dictionary(catalog.composers.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        workById = Dictionary(catalog.works.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        trackById = Dictionary(catalog.tracks.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        tracksByWork = Dictionary(grouping: catalog.tracks, by: \.workId)
            .mapValues { $0.sorted(by: CatalogIndex.trackInWorkOrder) }
        worksByComposer = Dictionary(grouping: catalog.works, by: \.composerId)
            .mapValues { $0.sorted { ($0.title, $0.id) < ($1.title, $1.id) } }
    }

    func genre(_ id: String) -> Genre? { genreById[id] }
    func composer(_ id: String) -> Composer? { composerById[id] }
    func work(_ id: String) -> Work? { workById[id] }
    func track(_ id: String) -> Track? { trackById[id] }

    /// 保存してある曲 ID を今のカタログの ID にする。新しい録音に置き換えた曲は置き換え先（SPEC §3.2）。
    /// カタログにない ID はそのまま返す（お気に入りでグレー表示するため）。
    func currentTrackId(_ id: String) -> String {
        if trackById[id] != nil { return id }
        if let new = catalog.replacedTracks[id], trackById[new] != nil { return new }
        return id
    }

    /// 保存してある ID のうち、置き換えたもの（古い ID → 新しい ID）。
    func replacements(for ids: some Sequence<String>) -> [String: String] {
        var result: [String: String] = [:]
        for id in ids where trackById[id] == nil {
            let new = currentTrackId(id)
            if new != id { result[id] = new }
        }
        return result
    }

    func tracks(ofWork workId: String) -> [Track] { tracksByWork[workId] ?? [] }

    /// 作曲家の作品（genreId を指定するとそのジャンルの作品だけ）。
    func works(of composerId: String, genreId: String? = nil) -> [Work] {
        (worksByComposer[composerId] ?? []).filter { genreId == nil || $0.genreId == genreId }
    }

    /// 作曲家の全曲（作品順、楽章順）。「すべて再生」に使う（SPEC §4.4）。
    func tracks(of composerId: String, genreId: String? = nil) -> [Track] {
        works(of: composerId, genreId: genreId).flatMap { tracks(ofWork: $0.id) }
    }

    func tracks(ofGenre genreId: String) -> [Track] { catalog.tracks.filter { $0.genreId == genreId } }

    /// ジャンルに曲がある作曲家と、そのジャンルでの曲数。
    func composers(ofGenre genreId: String) -> [ComposerCount] {
        var counts: [String: Int] = [:]
        for t in tracks(ofGenre: genreId) { counts[t.composerId, default: 0] += 1 }
        return counts.compactMap { id, n in composerById[id].map { ComposerCount(composer: $0, trackCount: n) } }
    }

    /// 公開日時が新しい順（日時のない曲は含めない）。SPEC §4.2 の「新着」。
    func newest(limit: Int) -> [Track] {
        let dated = catalog.tracks.compactMap { t in t.publishedAt.map { (t, instantKey($0)) } }
        return dated.sorted { a, b in a.1 != b.1 ? a.1 > b.1 : a.0.id < b.0.id }
            .prefix(limit)
            .map(\.0)
    }

    static func trackInWorkOrder(_ a: Track, _ b: Track) -> Bool {
        let ma = Movement.number(a.movement), mb = Movement.number(b.movement)
        if ma != mb { return compareNilsLast(ma, mb) }
        let pa = a.publishedAt.map(instantKey), pb = b.publishedAt.map(instantKey)
        if pa != pb { return compareNilsLast(pa, pb) }
        if a.title != b.title { return a.title < b.title }
        return a.id < b.id
    }

    private static func compareNilsLast<T: Comparable>(_ a: T?, _ b: T?) -> Bool {
        switch (a, b) {
        case let (x?, y?): x < y
        case (.some, nil): true
        default: false
        }
    }
}

struct ComposerCount: Equatable, Sendable {
    var composer: Composer
    var trackCount: Int
}

/// RFC 3339 の日時を、タイムゾーンをそろえて比較できるキーにする（不正な値は文字列のまま比べる）。
func instantKey(_ rfc3339: String) -> String {
    guard let date = parseRFC3339(rfc3339) else { return rfc3339 }
    return date.formatted(Date.ISO8601FormatStyle(includingFractionalSeconds: true))
}

func parseRFC3339(_ text: String) -> Date? {
    if let date = try? Date(text, strategy: .iso8601) { return date }
    return try? Date(text, strategy: Date.ISO8601FormatStyle(includingFractionalSeconds: true))
}
