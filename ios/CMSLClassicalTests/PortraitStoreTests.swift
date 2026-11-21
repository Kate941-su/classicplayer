import Foundation
import Testing
@testable import CMSLClassical

/// 作曲家の肖像画（PLAN 2.10、SPEC §4.10）。Android の `PortraitRepositoryTest` と同じケース。
struct PortraitStoreTests {
    let jpeg = Data("portrait-v1".utf8)
    let jpeg2 = Data("portrait-v2".utf8)
    let id = "l-v-beethoven"
    let server = FakeServer()
    let clock = TestClock(Date(timeIntervalSince1970: 1_796_083_200))
    let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    let baseURL = URL(string: "https://example.github.io/classicplayer/v1/catalog.json")!

    func portrait(_ data: Data) -> Portrait {
        Portrait(
            url: "portraits/\(id).jpg", sha256: CatalogStore.sha256(data), license: "Public domain",
            sourceUrl: "https://commons.wikimedia.org/wiki/File:Beethoven.jpg"
        )
    }

    func store(bundled: [String: Data] = [:], baseURL: URL? = nil) -> PortraitStore {
        let clock = clock
        return PortraitStore(
            bundled: { bundled[$0] }, cacheDirectory: dir, client: server, baseURL: baseURL ?? self.baseURL,
            now: { clock.now }
        )
    }

    func ok(_ data: Data) -> FakeServer.Response { .init(status: 200, body: data) }

    @Test func bundledPortraitNeedsNoNetwork() async {
        let data = await store(bundled: ["portraits/\(id).jpg": jpeg]).load(composerId: id, portrait: portrait(jpeg))
        #expect(data == jpeg)
        #expect(server.requests.isEmpty)
    }

    @Test func mismatchedBundleFetchesAndCaches() async {
        server.enqueue(ok(jpeg2))
        let data = await store(bundled: ["portraits/\(id).jpg": jpeg]).load(composerId: id, portrait: portrait(jpeg2))
        #expect(data == jpeg2)
        #expect(server.requests.first?.url?.absoluteString == "https://example.github.io/classicplayer/v1/portraits/\(id).jpg")
        #expect((try? Data(contentsOf: dir.appendingPathComponent("\(id).jpg"))) == jpeg2)
        // 作り直しても（再起動）キャッシュを使う
        #expect(await store().load(composerId: id, portrait: portrait(jpeg2)) == jpeg2)
        #expect(server.requests.count == 1)
    }

    @Test func changedHashRefetchesAndOverwrites() async {
        server.enqueue(ok(jpeg))
        server.enqueue(ok(jpeg2))
        let s = store()
        #expect(await s.load(composerId: id, portrait: portrait(jpeg)) == jpeg)
        #expect(await s.load(composerId: id, portrait: portrait(jpeg2)) == jpeg2)
        #expect(server.requests.count == 2)
        #expect((try? Data(contentsOf: dir.appendingPathComponent("\(id).jpg"))) == jpeg2)
    }

    @Test func failureReturnsNilAndWaitsBeforeRetrying() async {
        server.enqueue(.init(status: 404, body: Data()))
        let s = store()
        #expect(await s.load(composerId: id, portrait: portrait(jpeg)) == nil)
        #expect(await s.load(composerId: id, portrait: portrait(jpeg)) == nil)
        #expect(server.requests.count == 1)
        clock.advance(hours: PortraitStore.retryAfter / 3600 + 0.01)
        server.enqueue(ok(jpeg))
        #expect(await s.load(composerId: id, portrait: portrait(jpeg)) == jpeg)
        #expect(server.requests.count == 2)
    }

    @Test func mismatchedResponseIsNotCached() async {
        server.enqueue(ok(Data("broken".utf8)))
        #expect(await store().load(composerId: id, portrait: portrait(jpeg)) == nil)
        #expect(!FileManager.default.fileExists(atPath: dir.appendingPathComponent("\(id).jpg").path))
    }

    @Test func offlineReturnsNil() async {
        server.enqueueError(URLError(.notConnectedToInternet))
        #expect(await store().load(composerId: id, portrait: portrait(jpeg)) == nil)
    }

    @Test func networkFalseOnlyLooksLocally() async {
        let s = store()
        #expect(await s.load(composerId: id, portrait: portrait(jpeg), network: false) == nil)
        #expect(server.requests.isEmpty)
        server.enqueue(ok(jpeg))
        _ = await s.load(composerId: id, portrait: portrait(jpeg))
        #expect(await s.load(composerId: id, portrait: portrait(jpeg), network: false) == jpeg)
    }

    @Test func otherPathsAndHostsAreNotFetched() async {
        var other = portrait(jpeg)
        other.url = "portraits/other.jpg"
        #expect(await store().load(composerId: id, portrait: other) == nil)
        other.url = "https://example.com/portraits/\(id).jpg"
        #expect(await store().load(composerId: id, portrait: other) == nil)
        let http = store(baseURL: URL(string: "http://example.github.io/v1/catalog.json")!)
        #expect(await http.load(composerId: id, portrait: portrait(jpeg)) == nil)
        #expect(server.requests.isEmpty)
    }
}

/// 画像のクレジットと、画面・Now Playing が使う `Portraits`。
@MainActor
struct PortraitsTests {
    /// リポジトリの site/v1/portraits/（同梱の代わり）。
    nonisolated static let sitePortraits = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("site/v1")

    func portraits(_ catalog: Catalog, bundled: Bool) -> Portraits {
        let store = PortraitStore(
            bundled: { bundled ? try? Data(contentsOf: Self.sitePortraits.appendingPathComponent($0)) : nil },
            cacheDirectory: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString),
            client: FakeServer(), baseURL: URL(string: "https://example.github.io/v1/catalog.json")!
        )
        let index = CatalogIndex(catalog)
        return Portraits(store: store) { index.composer($0)?.portrait }
    }

    @Test func portraitImageWhenAvailable() async throws {
        let p = portraits(try Fixtures.sample(), bundled: true)
        #expect(p.cachedImage("l-v-beethoven") == nil)
        let image = try #require(await p.image("l-v-beethoven"))
        #expect(image.size.width > 0)
        #expect(p.cachedImage("l-v-beethoven") === image)
    }

    @Test func noImageWhenFetchFailsOrNoPortrait() async throws {
        var catalog = try Fixtures.sample()
        #expect(await portraits(catalog, bundled: false).image("l-v-beethoven") == nil)
        let index = try #require(catalog.composers.firstIndex { $0.id == "l-v-beethoven" })
        catalog.composers[index].portrait = nil
        let p = portraits(catalog, bundled: true)
        #expect(p.portrait(of: "l-v-beethoven") == nil)
        #expect(await p.image("l-v-beethoven") == nil)
    }

    @Test func creditsAreSortedByNameAndSkipComposersWithoutPortrait() throws {
        var catalog = try Fixtures.sample()
        let index = try #require(catalog.composers.firstIndex { $0.id == "l-v-beethoven" })
        catalog.composers[index].portrait = nil
        let credits = ImageCreditsView.credits(catalog.composers, english: true)
        let names = credits.map { $0.composer.displayName(english: true) }
        #expect(!credits.contains { $0.composer.id == "l-v-beethoven" })
        #expect(names.count == catalog.composers.count - 1)
        #expect(names == names.sorted { $0.localizedStandardCompare($1) == .orderedAscending })
    }
}
