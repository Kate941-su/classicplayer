import Foundation
import Testing
@testable import CMSLClassical

/// Android の `CatalogRepositoryTest` と同じケース。
struct CatalogStoreTests {
    let dir: URL
    let sampleBytes: Data
    let sampleCount: Int
    let seedBytes: Data
    let server = FakeServer()
    let clock = TestClock(Date(timeIntervalSince1970: 1_796_083_200)) // 2026-12-01T00:00:00Z
    let manifestURL = URL(string: "https://example.github.io/classicplayer/v1/manifest.json")!

    init() throws {
        dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        sampleBytes = try Fixtures.data("catalog.sample.json")
        let catalog = try CatalogJSON.decodeCatalog(sampleBytes)
        sampleCount = catalog.tracks.count
        // シードは「サンプルから 1 曲だけの版」にして、ダウンロード版と区別できるようにする
        let track = catalog.tracks[0]
        var one = catalog
        one.genres = catalog.genres.filter { $0.id == track.genreId }.map { var g = $0; g.trackCount = 1; return g }
        one.composers = catalog.composers.filter { $0.id == track.composerId }.map { var c = $0; c.trackCount = 1; return c }
        one.works = catalog.works.filter { $0.id == track.workId }
        one.tracks = [track]
        one.replacedTracks = catalog.replacedTracks.filter { $0.value == track.id }
        seedBytes = try JSONEncoder().encode(one)
    }

    func store() -> CatalogStore {
        let seed = seedBytes, clock = clock
        return CatalogStore(seed: { seed }, directory: dir, client: server, manifestURL: manifestURL, now: { clock.now })
    }

    func manifest(
        _ version: String, bytes: Data? = nil, schemaVersion: Int = 2, sha: String? = nil, catalogUrl: String = "catalog.json"
    ) -> Data {
        let hash = sha ?? CatalogStore.sha256(bytes ?? sampleBytes)
        return Data("""
        {"schemaVersion": \(schemaVersion), "catalogVersion": "\(version)", "catalogUrl": "\(catalogUrl)",
         "sha256": "\(hash)", "trackCount": \(sampleCount),
         "generatedAt": "2026-12-01T03:17:00+00:00", "minAppSchema": 2}
        """.utf8)
    }

    func enqueueManifest(_ body: Data, etag: String = "\"m1\"") {
        server.enqueue(.init(status: 200, body: body, headers: ["ETag": etag]))
    }

    func enqueueCatalog(_ bytes: Data) { server.enqueue(.init(status: 200, body: bytes)) }

    @Test func firstLaunchReadsSeed() async throws {
        let state = try await store().load()
        #expect(state.source == .seed)
        #expect(state.index.catalog.tracks.count == 1)
        #expect(state.catalogVersion == nil)
        #expect(server.requests.isEmpty)
    }

    @Test func fetchesNewVersionAndUsesLocalAfterRestart() async throws {
        let s = store()
        enqueueManifest(manifest("2026.12.01"))
        enqueueCatalog(sampleBytes)
        #expect(await s.refresh() == .updated(catalogVersion: "2026.12.01"))
        let state = try #require(await s.state)
        #expect(state.source == .downloaded)
        #expect(state.index.catalog.tracks.count == sampleCount)
        #expect(server.requests.map { $0.url!.path } == ["/classicplayer/v1/manifest.json", "/classicplayer/v1/catalog.json"])

        let restarted = try await store().load()
        #expect(restarted.source == .downloaded)
        #expect(restarted.catalogVersion == "2026.12.01")
        #expect(restarted.index.catalog.tracks.count == sampleCount)
    }

    @Test func skipsWithin24HoursUnlessForced() async throws {
        let s = store()
        enqueueManifest(manifest("2026.12.01"))
        enqueueCatalog(sampleBytes)
        _ = await s.refresh()

        clock.advance(hours: 23)
        #expect(await s.refresh() == .skipped)
        #expect(server.requests.count == 2)

        server.enqueue(.init(status: 304, body: Data()))
        #expect(await s.refresh(force: true) == .notModified)
        #expect(server.requests.last?.value(forHTTPHeaderField: "If-None-Match") == "\"m1\"")

        clock.advance(hours: 25)
        enqueueManifest(manifest("2026.12.01"), etag: "\"m2\"")
        #expect(await s.refresh() == .upToDate)
        #expect(server.requests.count == 4)
    }

    @Test func keepsOldVersionWhenSHA256Mismatches() async throws {
        let s = store()
        enqueueManifest(manifest("2026.12.01", sha: String(repeating: "0", count: 64)))
        enqueueCatalog(sampleBytes)
        guard case .failed = await s.refresh() else { Issue.record("失敗するはず"); return }
        #expect(await s.state?.source == .seed)
        #expect(!FileManager.default.fileExists(atPath: dir.appendingPathComponent(CatalogStore.catalogFile).path))
    }

    @Test func rejectsInvalidCatalogs() async throws {
        let s = store()
        enqueueManifest(manifest("2026.12.01"))
        enqueueCatalog(sampleBytes)
        _ = await s.refresh()

        let names = ["dangling-work-ref", "duplicate-track-id", "foreign-audio-domain", "schema-version-3", "trackcount-mismatch"]
        for (i, name) in names.enumerated() {
            let bytes = try Fixtures.data("invalid/\(name).json")
            clock.advance(hours: 25)
            enqueueManifest(manifest("2026.12.0\(i + 2)", bytes: bytes))
            enqueueCatalog(bytes)
            let result = await s.refresh()
            guard case .failed = result else { Issue.record("\(name): \(result)"); continue }
            #expect(await s.state?.catalogVersion == "2026.12.01")
            #expect(await s.state?.index.catalog.tracks.count == sampleCount)
        }
        #expect(try await store().load().catalogVersion == "2026.12.01")
    }

    @Test func doesNotUpdateWhenManifestSchemaIsUnsupported() async throws {
        let s = store()
        enqueueManifest(manifest("2026.12.01", schemaVersion: 3))
        #expect(await s.refresh() == .unsupported)
        #expect(server.requests.count == 1)
        #expect(await s.state?.source == .seed)
    }

    @Test func keepsOldVersionOnNetworkOrServerError() async throws {
        let s = store()
        server.enqueue(.init(status: 500, body: Data()))
        guard case .failed = await s.refresh() else { Issue.record("500 は失敗"); return }
        // 失敗は確認済みにしない（次の起動で再確認する）
        server.enqueueError(URLError(.notConnectedToInternet))
        guard case .failed = await s.refresh() else { Issue.record("通信エラーは失敗"); return }
        #expect(server.requests.count == 2)
        #expect(await s.state?.source == .seed)
    }

    @Test func brokenLocalFallsBackToSeedAndRefetches() async throws {
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try Data("{ broken".utf8).write(to: dir.appendingPathComponent(CatalogStore.catalogFile))
        try Data(#"{"catalogVersion": "2026.12.01"}"#.utf8).write(to: dir.appendingPathComponent(CatalogStore.metaFile))
        let s = store()
        #expect(try await s.load().source == .seed)

        enqueueManifest(manifest("2026.12.01"))
        enqueueCatalog(sampleBytes)
        #expect(await s.refresh() == .updated(catalogVersion: "2026.12.01"))
    }

    @Test func rejectsCatalogURLOnAnotherHost() async throws {
        let s = store()
        enqueueManifest(manifest("2026.12.01", catalogUrl: "https://evil.example.com/catalog.json"))
        guard case .failed = await s.refresh() else { Issue.record("別ホストは拒否"); return }
        #expect(server.requests.count == 1)
    }

    @Test func publishesUpdatesToObservers() async throws {
        let s = store()
        let stream = await s.updates()
        try await s.load()
        enqueueManifest(manifest("2026.12.01"))
        enqueueCatalog(sampleBytes)
        _ = await s.refresh()
        var sources: [CatalogSource] = []
        for await state in stream {
            sources.append(state.source)
            if state.source == .downloaded { break }
        }
        #expect(sources.last == .downloaded)
    }
}

final class FakeServer: HTTPClient, @unchecked Sendable {
    struct Response {
        var status: Int
        var body: Data
        var headers: [String: String] = [:]
    }

    private let lock = NSLock()
    private var queue: [Result<Response, URLError>] = []
    private var recorded: [URLRequest] = []

    var requests: [URLRequest] { lock.withLock { recorded } }

    func enqueue(_ response: Response) { lock.withLock { queue.append(.success(response)) } }

    func enqueueError(_ error: URLError) { lock.withLock { queue.append(.failure(error)) } }

    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let next: Result<Response, URLError> = lock.withLock {
            recorded.append(request)
            return queue.isEmpty ? .failure(URLError(.cannotConnectToHost)) : queue.removeFirst()
        }
        let response = try next.get()
        let http = HTTPURLResponse(url: request.url!, statusCode: response.status, httpVersion: "HTTP/1.1", headerFields: response.headers)!
        return (response.body, http)
    }
}

final class TestClock: @unchecked Sendable {
    private let lock = NSLock()
    private var current: Date

    init(_ start: Date) { current = start }

    var now: Date { lock.withLock { current } }

    func advance(hours: Double) { lock.withLock { current += hours * 3600 } }
}
