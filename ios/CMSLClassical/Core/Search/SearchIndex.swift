/// 端末内のカタログを検索する（SPEC §4.6）。照合と並び順は `docs/search-normalization.md`。
/// キーの正規化はカタログの差し替え時に 1 回だけ行う。
final class SearchIndex: Sendable {
    static let maxComposers = 20
    static let maxWorks = 50
    static let maxTracks = 100

    private struct Entry<T: Sendable>: Sendable {
        let item: T
        let primary: [String]
        let secondary: [String]
        let order: Int
    }

    private let composers: [Entry<Composer>]
    private let works: [Entry<Work>]
    private let tracks: [Entry<Track>]

    init(_ catalog: CatalogIndex) {
        func keys(_ texts: String?...) -> [String] {
            texts.compactMap { $0 }.map(SearchNormalizer.normalize).filter { !$0.isEmpty }
        }
        func composerKeys(_ id: String) -> [String] {
            catalog.composer(id).map { keys($0.name, $0.nameEn) } ?? []
        }
        composers = catalog.catalog.composers
            .sorted { ($1.trackCount, $0.name) < ($0.trackCount, $1.name) }
            .enumerated()
            .map { Entry(item: $1, primary: keys($1.name, $1.nameEn), secondary: [], order: $0) }
        let sortedWorks = catalog.catalog.works.sorted { ($0.title, $0.id) < ($1.title, $1.id) }
        works = sortedWorks.enumerated().map { i, w in
            Entry(item: w, primary: keys(w.title), secondary: composerKeys(w.composerId), order: i)
        }
        tracks = sortedWorks.flatMap { catalog.tracks(ofWork: $0.id) }.enumerated().map { i, t in
            Entry(
                item: t, primary: keys(t.title, t.titleEn),
                secondary: keys(catalog.work(t.workId)?.title) + composerKeys(t.composerId), order: i
            )
        }
    }

    func search(_ query: String) -> SearchResults {
        let terms = SearchNormalizer.terms(query)
        if terms.isEmpty { return .empty }
        return SearchResults(
            composers: Self.match(composers, terms, Self.maxComposers),
            works: Self.match(works, terms, Self.maxWorks),
            tracks: Self.match(tracks, terms, Self.maxTracks)
        )
    }

    private static func match<T>(_ entries: [Entry<T>], _ terms: [String], _ limit: Int) -> [T] {
        entries.compactMap { e in rank(e, terms).map { ($0, e) } }
            .sorted { ($0.0, $0.1.order) < ($1.0, $1.1.order) }
            .prefix(limit)
            .map(\.1.item)
    }

    /// 0: すべての語が主キーの先頭、1: 主キーのどれか、2: 副キーを含めて一致。一致しなければ nil。
    private static func rank<T>(_ e: Entry<T>, _ terms: [String]) -> Int? {
        if terms.allSatisfy({ t in e.primary.contains { $0.hasPrefix(t) } }) { return 0 }
        if terms.allSatisfy({ t in e.primary.contains { $0.contains(t) } }) { return 1 }
        if terms.allSatisfy({ t in e.primary.contains { $0.contains(t) } || e.secondary.contains { $0.contains(t) } }) {
            return 2
        }
        return nil
    }
}

struct SearchResults: Equatable, Sendable {
    var composers: [Composer]
    var works: [Work]
    var tracks: [Track]

    var isEmpty: Bool { composers.isEmpty && works.isEmpty && tracks.isEmpty }

    static let empty = SearchResults(composers: [], works: [], tracks: [])
}
