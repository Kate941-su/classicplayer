import Foundation
import SwiftData
import Testing
@testable import CMSLClassical

/// Android の `LibraryDatabaseTest`・`PreferencesStoresTest` と同じケース。
@MainActor
struct LibraryStoreTests {
    let store: LibraryStore

    init() throws { store = LibraryStore(container: try LibraryStore.makeContainer(inMemory: true)) }

    func date(_ seconds: TimeInterval) -> Date { Date(timeIntervalSince1970: seconds) }

    @Test func favoritesAreNewestFirstAndReAddingKeepsDate() {
        store.addFavorite("a", at: date(1))
        store.addFavorite("b", at: date(2))
        store.addFavorite("a", at: date(3))
        #expect(store.favorites.map(\.trackId) == ["b", "a"])
        #expect(store.favorites.last?.date == date(1))
        #expect(store.favoriteIds == ["a", "b"])
    }

    @Test func replacesIdsAndDropsOldWhenNewExists() {
        store.addFavorite("old1", at: date(1))
        store.addFavorite("old2", at: date(2))
        store.addFavorite("new2", at: date(3))
        store.recordPlayed("old1", at: date(4))
        store.recordPlayed("old2", at: date(5))
        store.recordPlayed("new2", at: date(6))
        store.replaceIds(["old1": "new1", "old2": "new2", "missing": "x"])
        #expect(store.favorites == [
            LibraryEntry(trackId: "new2", date: date(3)), LibraryEntry(trackId: "new1", date: date(1)),
        ])
        #expect(store.history == [
            LibraryEntry(trackId: "new2", date: date(6)), LibraryEntry(trackId: "new1", date: date(4)),
        ])
    }

    @Test func toggleFavorite() {
        #expect(store.toggleFavorite("a"))
        #expect(store.isFavorite("a"))
        #expect(!store.toggleFavorite("a"))
        #expect(!store.isFavorite("a"))
    }

    @Test func historyMergesSameTrackNewestFirst() {
        store.recordPlayed("a", at: date(1))
        store.recordPlayed("b", at: date(2))
        store.recordPlayed("a", at: date(3))
        #expect(store.history.map(\.trackId) == ["a", "b"])
        #expect(store.history.first?.date == date(3))
    }

    @Test func historyKeepsAtMost100() {
        for i in 0..<105 { store.recordPlayed("t\(i)", at: date(TimeInterval(i))) }
        #expect(store.history.count == 100)
        #expect(store.history.first?.trackId == "t104")
        #expect(store.history.last?.trackId == "t5")
    }

    @Test func persistsAcrossStores() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).store")
        let config = { try ModelContainerFactory.make(url: url) }
        LibraryStore(container: try config()).addFavorite("a")
        #expect(LibraryStore(container: try config()).favoriteIds == ["a"])
    }
}

enum ModelContainerFactory {
    static func make(url: URL) throws -> ModelContainer {
        let schema = Schema([FavoriteRecord.self, HistoryRecord.self])
        return try ModelContainer(for: schema, configurations: [ModelConfiguration(schema: schema, url: url)])
    }
}

@MainActor
struct PreferenceStoresTests {
    let suite = "test-\(UUID().uuidString)"

    @Test func recentSearchesNewestFirstUniqueMax10() throws {
        let defaults = try #require(UserDefaults(suiteName: suite))
        let store = RecentSearchStore(defaults: defaults)
        for i in 0..<12 { store.add("q\(i)") }
        store.add(" q5 ")
        store.add("  ")
        #expect(store.recent.count == 10)
        #expect(store.recent.first == "q5")
        #expect(store.recent.filter { $0 == "q5" }.count == 1)
        store.remove("q5")
        #expect(!store.recent.contains("q5"))
        #expect(RecentSearchStore(defaults: defaults).recent == store.recent)
        store.clear()
        #expect(store.recent.isEmpty)
    }

    @Test func savesAndLoadsQueue() {
        let store = QueueStore(suiteName: suite)
        #expect(store.load() == nil)
        let snapshot = QueueSnapshot(trackIds: ["a", "b"], index: 1, positionMs: 1234, shuffle: true, repeatMode: .one)
        store.save(snapshot)
        #expect(QueueStore(suiteName: suite).load() == snapshot)
        store.clear()
        #expect(store.load() == nil)
    }
}
