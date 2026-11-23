import AVFoundation
import Foundation
import Testing
@testable import CMSLClassical

/// 再現できる乱数（SplitMix64）。
struct SeededGenerator: RandomNumberGenerator {
    var state: UInt64

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

func makeTrack(_ id: String) -> Track {
    Track(
        id: id, title: "t\(id)", composerId: "c", workId: "w", genreId: "g",
        audioUrl: "https://classical-sound.up.seesaa.net/image/\(id).mp3", fileName: "\(id).mp3",
        articleUrl: "https://classical-sound.seesaa.net/article/1.html"
    )
}

/// Android の `QueueBuilderTest` と同じケース。
struct QueueBuilderTests {
    let work = ["1", "2", "3", "4"].map(makeTrack)

    @Test func workStartsAtFirstMovement() {
        #expect(QueueBuilder.fromWork(work) == PlayQueue(trackIds: ["1", "2", "3", "4"], startIndex: 0))
    }

    @Test func workFromGivenTrackToEnd() {
        #expect(QueueBuilder.fromWork(work, startTrackId: "3") == PlayQueue(trackIds: ["3", "4"], startIndex: 0))
    }

    @Test func unknownStartTrackStartsAtBeginning() {
        #expect(QueueBuilder.fromWork(work, startTrackId: "x").trackIds == ["1", "2", "3", "4"])
    }

    @Test func listKeepsOrderFromIndex() {
        #expect(QueueBuilder.fromList(work, startIndex: 2) == PlayQueue(trackIds: ["1", "2", "3", "4"], startIndex: 2))
        #expect(QueueBuilder.fromList(work, startIndex: 99).startIndex == 3)
        #expect(QueueBuilder.fromList([], startIndex: 1) == PlayQueue(trackIds: []))
    }

    @Test func shuffleReordersSameTracks() {
        let many = (1...30).map { makeTrack(String($0)) }
        var g1 = SeededGenerator(state: 42), g2 = SeededGenerator(state: 42)
        let q = QueueBuilder.shuffled(many, using: &g1)
        #expect(Set(q.trackIds) == Set(many.map(\.id)))
        #expect(q.trackIds.count == 30)
        #expect(q.trackIds != many.map(\.id))
        #expect(q == QueueBuilder.shuffled(many, using: &g2))
    }

    @Test func fixtureWorkQueueIsInMovementOrder() throws {
        let index = CatalogIndex(try Fixtures.sample())
        let queue = QueueBuilder.fromWork(index.tracks(ofWork: CatalogIndexTests.seventh), startTrackId: "493005331")
        #expect(queue.trackIds == ["493005331", "222058571", "499616036-1"])
    }
}

/// Android の `PlaybackPolicyTest` と同じケース。
struct PlaybackPolicyTests {
    @Test func stopsAfterThreeConsecutiveErrors() {
        var p = ErrorSkipPolicy()
        #expect(p.onError(hasNext: true) == .skip)
        #expect(p.onError(hasNext: true) == .skip)
        #expect(p.onError(hasNext: true) == .stop)
    }

    @Test func readyResetsCount() {
        var p = ErrorSkipPolicy()
        _ = p.onError(hasNext: true)
        _ = p.onError(hasNext: true)
        p.onReady()
        #expect(p.onError(hasNext: true) == .skip)
    }

    @Test func stopsWhenNoNext() {
        var p = ErrorSkipPolicy()
        #expect(p.onError(hasNext: false) == .stop)
    }

    @Test func repeatCyclesOffAllOne() {
        #expect(RepeatMode.off.next == .all)
        #expect(RepeatMode.all.next == .one)
        #expect(RepeatMode.one.next == .off)
    }

    @Test func restoreDropsMissingTracksAndKeepsCurrent() throws {
        let s = QueueSnapshot(trackIds: ["a", "gone", "b", "c"], index: 2, positionMs: 30_000, shuffle: true)
        let r = try #require(s.restorable { $0 != "gone" })
        #expect(r.trackIds == ["a", "b", "c"])
        #expect(r.index == 1)
        #expect(r.positionMs == 30_000)
        #expect(r.shuffle)
    }

    @Test func restoreStartsAtNextRemainingWhenCurrentIsGone() throws {
        let s = QueueSnapshot(trackIds: ["a", "gone", "b"], index: 1, positionMs: 30_000)
        let r = try #require(s.restorable { $0 != "gone" })
        #expect(r.trackIds == ["a", "b"])
        #expect(r.index == 1)
        #expect(r.positionMs == 0)
    }

    @Test func restoreNothingWhenAllGone() {
        #expect(QueueSnapshot(trackIds: ["x"], index: 0, positionMs: 0).restorable { _ in false } == nil)
    }

    @Test func snapshotJSONMatchesAndroid() throws {
        let json = try JSONEncoder().encode(QueueSnapshot(trackIds: ["a"], index: 0, positionMs: 5, repeatMode: .all))
        let object = try #require(try JSONSerialization.jsonObject(with: json) as? [String: Any])
        #expect(object["repeatMode"] as? String == "All")
    }
}

struct PlaybackQueueTests {
    @Test func nextAndPreviousFollowRepeat() {
        var q = PlaybackQueue(trackIds: ["a", "b", "c"], startIndex: 2)
        #expect(q.nextKey(automatic: true) == nil)
        q.repeatMode = .all
        #expect(q.item(key: q.nextKey(automatic: true)!)?.trackId == "a")
        q.repeatMode = .one
        #expect(q.nextKey(automatic: true) == q.currentKey)
        #expect(q.item(key: q.nextKey(automatic: false)!)?.trackId == "a")
        q.repeatMode = .off
        #expect(q.item(key: q.previousKey()!)?.trackId == "b")
    }

    @Test func shuffleKeepsCurrentFirstAndVisitsAll() {
        var q = PlaybackQueue(trackIds: (1...10).map(String.init), startIndex: 4)
        var g = SeededGenerator(state: 7)
        q.setShuffle(true, using: &g)
        var visited = [q.currentTrackId!]
        while let key = q.nextKey(automatic: true) {
            q.setCurrent(key: key)
            visited.append(q.currentTrackId!)
        }
        #expect(visited.first == "5")
        #expect(Set(visited).count == 10)
        #expect(q.trackIds == (1...10).map(String.init)) // 表示の並びは変えない
    }

    @Test func insertNextAppendMoveRemove() {
        var q = PlaybackQueue(trackIds: ["a", "b", "c"], startIndex: 0)
        q.insertNext("x")
        q.append("y")
        #expect(q.trackIds == ["a", "x", "b", "c", "y"])
        q.move(from: 4, to: 0)
        #expect(q.trackIds == ["y", "a", "x", "b", "c"])
        #expect(q.currentTrackId == "a")
        q.remove(at: 1) // 今の曲を外すと次の曲へ
        #expect(q.currentTrackId == "x")
        q.remove(at: 3)
        q.remove(at: 2)
        q.remove(at: 1) // 最後の曲を外すと前の曲へ
        #expect(q.trackIds == ["y"])
        #expect(q.currentTrackId == "y")
        q.remove(at: 0)
        #expect(q.isEmpty)
        #expect(q.currentKey == nil)
    }

    @Test func sameTrackTwiceIsDistinct() {
        var q = PlaybackQueue(trackIds: ["a"], startIndex: 0)
        q.append("a")
        q.setCurrent(key: q.nextKey(automatic: false)!)
        #expect(q.currentIndex == 1)
    }
}

@MainActor
struct AssetPreparerTests {
    final class Calls { var loads: [URL] = []; var downloads = 0 }
    let calls = Calls()
    let url = URL(string: "https://classical-sound.up.seesaa.net/image/a.mp3")!
    let file = URL(filePath: "/nonexistent/a.mp3")

    /// `knownAfter` 回目から長さが分かる配信元。
    private func preparer(knownAfter: Int) -> AssetPreparer {
        AssetPreparer(
            attempts: 3, retryDelay: .milliseconds(1),
            loadDuration: { [calls] asset in
                calls.loads.append(asset.url)
                return asset.url.isFileURL || calls.loads.count >= knownAfter ? 120 : .nan
            },
            download: { [calls, file] _ in
                calls.downloads += 1
                return file
            }
        )
    }

    @Test func retriesUntilDurationIsKnown() async throws {
        let prepared = try await preparer(knownAfter: 2).prepare(url)
        #expect(prepared.duration == 120)
        #expect(prepared.asset.url == url)
        #expect(calls.loads.count == 2)
        #expect(calls.downloads == 0)
    }

    @Test func downloadsWhenDurationStaysUnknown() async throws {
        let prepared = try await preparer(knownAfter: .max).prepare(url)
        #expect(prepared.duration == 120)
        #expect(prepared.asset.url == file)
        #expect(calls.loads.count == 4)
        #expect(calls.downloads == 1)
    }

    @Test func failsWhenAssetCannotBeLoaded() async {
        var failing = preparer(knownAfter: 1)
        failing.loadDuration = { _ in throw URLError(.notConnectedToInternet) }
        await #expect(throws: URLError.self) { try await failing.prepare(url) }
    }
}
