import Foundation
import Observation
import SwiftData

/// お気に入り（SPEC §6）。保存するのは track.id と追加日時だけ。
@Model
final class FavoriteRecord {
    @Attribute(.unique) var trackId: String
    var addedAt: Date

    init(trackId: String, addedAt: Date) {
        self.trackId = trackId
        self.addedAt = addedAt
    }
}

/// 再生履歴（SPEC §6）。同じ曲は 1 件にまとめ、最大 100 件。
@Model
final class HistoryRecord {
    @Attribute(.unique) var trackId: String
    var playedAt: Date

    init(trackId: String, playedAt: Date) {
        self.trackId = trackId
        self.playedAt = playedAt
    }
}

struct LibraryEntry: Equatable, Hashable, Sendable {
    var trackId: String
    var date: Date
}

/// お気に入りと再生履歴（SwiftData）。Android の `LibraryDatabase` と同じ規則。
/// 画面は `favorites`・`history`・`favoriteIds` を見る（変更のたびに読み直す）。
@MainActor
@Observable
final class LibraryStore {
    static let maxHistory = 100

    /// 追加日の新しい順。カタログから消えた曲も残す（画面でグレー表示する）。
    private(set) var favorites: [LibraryEntry] = []
    private(set) var favoriteIds: Set<String> = []
    /// 再生日時の新しい順。
    private(set) var history: [LibraryEntry] = []

    @ObservationIgnored private let context: ModelContext

    init(container: ModelContainer) {
        context = ModelContext(container)
        reload()
    }

    static func makeContainer(inMemory: Bool = false) throws -> ModelContainer {
        let schema = Schema([FavoriteRecord.self, HistoryRecord.self])
        let config = ModelConfiguration("library", schema: schema, isStoredInMemoryOnly: inMemory)
        return try ModelContainer(for: schema, configurations: [config])
    }

    func isFavorite(_ trackId: String) -> Bool { favoriteIds.contains(trackId) }

    /// すでにあれば追加日を変えない。
    func addFavorite(_ trackId: String, at date: Date = .now) {
        guard favorite(trackId) == nil else { return }
        context.insert(FavoriteRecord(trackId: trackId, addedAt: date))
        commit()
    }

    func removeFavorite(_ trackId: String) {
        if let record = favorite(trackId) { context.delete(record) }
        commit()
    }

    /// 追加したら true。
    @discardableResult
    func toggleFavorite(_ trackId: String, at date: Date = .now) -> Bool {
        if favorite(trackId) != nil {
            removeFavorite(trackId)
            return false
        }
        addFavorite(trackId, at: date)
        return true
    }

    /// 同じ曲は 1 件にまとめて日時を更新し、最大 100 件に保つ。
    func recordPlayed(_ trackId: String, at date: Date = .now) {
        if let record = historyRecord(trackId) {
            record.playedAt = date
        } else {
            context.insert(HistoryRecord(trackId: trackId, playedAt: date))
        }
        let all = (try? context.fetch(Self.historyDescriptor)) ?? []
        for record in all.dropFirst(Self.maxHistory) { context.delete(record) }
        commit()
    }

    func clearHistory() {
        try? context.delete(model: HistoryRecord.self)
        commit()
    }

    /// 新しい録音に置き換えた曲の ID を読み替える（SPEC §6）。日時はそのまま。置き換え先がすでにあれば古い方を消す。
    func replaceIds(_ replacements: [String: String]) {
        guard !replacements.isEmpty else { return }
        for (old, new) in replacements {
            if let record = favorite(old) {
                if favorite(new) != nil { context.delete(record) } else { record.trackId = new }
            }
            if let record = historyRecord(old) {
                if historyRecord(new) != nil { context.delete(record) } else { record.trackId = new }
            }
        }
        commit()
    }

    var allTrackIds: Set<String> { favoriteIds.union(history.map(\.trackId)) }

    // MARK: - 内部

    private static var favoritesDescriptor: FetchDescriptor<FavoriteRecord> {
        FetchDescriptor(sortBy: [SortDescriptor(\.addedAt, order: .reverse), SortDescriptor(\.trackId)])
    }

    private static var historyDescriptor: FetchDescriptor<HistoryRecord> {
        FetchDescriptor(sortBy: [SortDescriptor(\.playedAt, order: .reverse), SortDescriptor(\.trackId)])
    }

    private func favorite(_ trackId: String) -> FavoriteRecord? {
        var descriptor = FetchDescriptor<FavoriteRecord>(predicate: #Predicate { $0.trackId == trackId })
        descriptor.fetchLimit = 1
        return try? context.fetch(descriptor).first
    }

    private func historyRecord(_ trackId: String) -> HistoryRecord? {
        var descriptor = FetchDescriptor<HistoryRecord>(predicate: #Predicate { $0.trackId == trackId })
        descriptor.fetchLimit = 1
        return try? context.fetch(descriptor).first
    }

    private func commit() {
        try? context.save()
        reload()
    }

    private func reload() {
        favorites = ((try? context.fetch(Self.favoritesDescriptor)) ?? []).map {
            LibraryEntry(trackId: $0.trackId, date: $0.addedAt)
        }
        favoriteIds = Set(favorites.map(\.trackId))
        var historyDescriptor = Self.historyDescriptor
        historyDescriptor.fetchLimit = Self.maxHistory
        history = ((try? context.fetch(historyDescriptor)) ?? []).map {
            LibraryEntry(trackId: $0.trackId, date: $0.playedAt)
        }
    }
}
