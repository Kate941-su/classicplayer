import Foundation
import Observation

/// 最近の検索（新しい順、最大 10 件。SPEC §4.6）。UserDefaults に保存する（SPEC §6）。
@MainActor
@Observable
final class RecentSearchStore {
    static let max = 10
    private static let key = "recentSearches"

    private(set) var recent: [String]
    @ObservationIgnored private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        recent = defaults.stringArray(forKey: Self.key) ?? []
    }

    func add(_ query: String) {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return }
        save(Array(([q] + recent.filter { $0 != q }).prefix(Self.max)))
    }

    func remove(_ query: String) { save(recent.filter { $0 != query }) }

    func clear() { save([]) }

    private func save(_ list: [String]) {
        recent = list
        defaults.set(list, forKey: Self.key)
    }
}

/// 再生キューと位置（SPEC §5「状態の復元」）。UserDefaults に JSON で保存する（SPEC §6）。
struct QueueStore: Sendable {
    private static let key = "queue"
    private let suiteName: String?

    init(suiteName: String? = nil) { self.suiteName = suiteName }

    private var defaults: UserDefaults { suiteName.flatMap(UserDefaults.init(suiteName:)) ?? .standard }

    func load() -> QueueSnapshot? {
        guard let data = defaults.data(forKey: Self.key) else { return nil }
        return try? JSONDecoder().decode(QueueSnapshot.self, from: data)
    }

    func save(_ snapshot: QueueSnapshot) {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults.set(data, forKey: Self.key)
    }

    func clear() { defaults.removeObject(forKey: Self.key) }
}
