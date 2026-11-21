import Foundation
import Testing
@testable import CMSLClassical

/// 偽のダウンローダー。URL ごとに結果を決め、呼ばれた順番を記録する。`gate` を使うと完了を止めておける。
final class FakeDownloader: Downloader, @unchecked Sendable {
    private let lock = NSLock()
    private var calls: [URL] = []
    private var concurrent = 0
    private(set) var maxConcurrent = 0
    var failing: Set<String> = []
    var empty: Set<String> = []
    var gate: AsyncStream<Void>?

    var requested: [URL] { lock.withLock { calls } }

    func download(from url: URL, wifiOnly: Bool, progress: @escaping @Sendable (Double) -> Void) async throws -> URL {
        lock.withLock {
            calls.append(url)
            concurrent += 1
            maxConcurrent = max(maxConcurrent, concurrent)
        }
        defer { lock.withLock { concurrent -= 1 } }
        progress(0.5)
        if let gate { for await _ in gate { break } }
        try Task.checkCancellation()
        let name = url.deletingPathExtension().lastPathComponent
        if failing.contains(name) { throw URLError(.notConnectedToInternet) }
        let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try Data(empty.contains(name) ? [] : Array("ID3 \(name)".utf8)).write(to: file)
        return file
    }
}

@MainActor
struct DownloadStoreTests {
    let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    let downloader = FakeDownloader()
    let defaults = UserDefaults(suiteName: "dl-\(UUID().uuidString)")!

    func store() -> DownloadStore { DownloadStore(directory: dir, downloader: downloader, defaults: defaults) }

    @Test func downloadsOneAtATimeInOrder() async throws {
        let s = store()
        s.enqueue(["1", "2", "3"].map(makeTrack))
        #expect(s.isPending("2"))
        await s.waitUntilIdle()
        #expect(downloader.requested.map { $0.lastPathComponent } == ["1.mp3", "2.mp3", "3.mp3"])
        #expect(downloader.maxConcurrent == 1)
        #expect(s.isDownloaded("1") && s.isDownloaded("3"))
        let file = try #require(s.localURL(for: "2"))
        #expect(file.isFileURL)
        #expect(try String(contentsOf: file, encoding: .utf8) == "ID3 2")
        #expect(s.totalBytes == 15)
        let excluded = try dir.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup
        #expect(excluded == true)
    }

    @Test func skipsAlreadyDownloadedOrQueued() async {
        let s = store()
        s.enqueue([makeTrack("1")])
        s.enqueue([makeTrack("1")])
        await s.waitUntilIdle()
        s.enqueue([makeTrack("1")])
        await s.waitUntilIdle()
        #expect(downloader.requested.count == 1)
    }

    @Test func failedAndEmptyDownloadsStayUndownloaded() async {
        downloader.failing = ["2"]
        downloader.empty = ["3"]
        let s = store()
        s.enqueue(["1", "2", "3"].map(makeTrack))
        await s.waitUntilIdle()
        #expect(s.isDownloaded("1"))
        #expect(!s.isDownloaded("2"))
        #expect(!s.isDownloaded("3"))
        #expect(s.lastFailure == "3")
        #expect(!s.isPending("2"))
    }

    @Test func rejectsAudioOutsideAllowedHosts() async {
        let s = store()
        var track = makeTrack("1")
        track.audioUrl = "https://example.com/1.mp3"
        s.enqueue([track])
        await s.waitUntilIdle()
        #expect(downloader.requested.isEmpty)
    }

    @Test func removeDeletesFileAndCancelsQueued() async throws {
        let (gate, open) = AsyncStream<Void>.makeStream()
        downloader.gate = gate
        let s = store()
        s.enqueue(["1", "2"].map(makeTrack))
        s.remove("2") // 待ち列から外す
        open.yield()
        open.finish()
        await s.waitUntilIdle()
        #expect(s.isDownloaded("1"))
        #expect(!s.isDownloaded("2"))
        let file = try #require(s.localURL(for: "1"))
        s.remove("1")
        #expect(!s.isDownloaded("1"))
        #expect(!FileManager.default.fileExists(atPath: file.path))
    }

    @Test func cancellingActiveDownloadKeepsNothing() async {
        let (gate, open) = AsyncStream<Void>.makeStream()
        downloader.gate = gate
        let s = store()
        s.enqueue([makeTrack("1")])
        while s.active == nil { await Task.yield() }
        s.remove("1")
        open.finish()
        await s.waitUntilIdle()
        #expect(!s.isDownloaded("1"))
        #expect(s.lastFailure == nil)
    }

    @Test func pruneDeletesTracksMissingFromCatalog() async {
        let s = store()
        s.enqueue(["1", "replaced", "gone"].map(makeTrack))
        await s.waitUntilIdle()
        s.prune { $0 == "1" }
        #expect(Set(s.downloaded.keys) == ["1"])
    }

    @Test func rescansFilesOnLaunchAndKeepsSetting() async {
        let s = store()
        s.wifiOnly = false
        s.enqueue([makeTrack("1")])
        await s.waitUntilIdle()
        let reopened = store()
        #expect(reopened.isDownloaded("1"))
        #expect(reopened.wifiOnly == false)
        #expect(DownloadStore(directory: dir, downloader: downloader, defaults: UserDefaults(suiteName: "x-\(UUID())")!).wifiOnly)
    }
}

/// ダウンロード済みの曲は端末のファイルで再生する（SPEC §5.1）。
@MainActor
struct OfflinePlaybackTests {
    @Test func downloadedTrackPlaysFromFile() async throws {
        let engine = FakeEngine()
        let suite = "app-\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suite))
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let seed = try Fixtures.data("catalog.sample.json")
        let model = AppModel(
            store: CatalogStore(
                seed: { seed }, directory: dir, client: FakeServer(),
                manifestURL: URL(string: "https://example.github.io/v1/manifest.json")!
            ),
            library: LibraryStore(container: try LibraryStore.makeContainer(inMemory: true)),
            recentSearches: RecentSearchStore(defaults: defaults),
            queueStore: QueueStore(suiteName: suite),
            downloads: DownloadStore(directory: dir.appendingPathComponent("Downloads"), downloader: FakeDownloader(), defaults: defaults),
            portraitStore: PortraitStore(
                bundled: { _ in nil }, cacheDirectory: dir.appendingPathComponent("portraits"), client: FakeServer(),
                baseURL: URL(string: "https://example.github.io/v1/catalog.json")!
            ),
            engine: engine, english: false, refreshOnStart: false
        )
        let index = try #require(await model.waitForIndex())
        let work = index.tracks(ofWork: CatalogIndexTests.fate)
        model.downloads.enqueue([work[1]])
        await model.downloads.waitUntilIdle()

        await model.playback.play(QueueBuilder.fromWork(work))
        #expect(engine.current?.isFileURL == false) // 第1楽章はストリーミング
        #expect(engine.next?.isFileURL == true) // 先読みした第2楽章はファイル

        // 再生中にダウンロードを消すと、先読みもストリーミングに戻る
        model.downloads.remove(work[1].id)
        for _ in 0..<50 where engine.next?.isFileURL == true { await Task.yield() }
        #expect(engine.next?.absoluteString == work[1].audioUrl)
    }
}
