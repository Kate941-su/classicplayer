import Foundation
import Testing
@testable import CMSLClassical

@MainActor
final class FakeEngine: AudioEngine {
    weak var delegate: (any AudioEngineDelegate)?
    var currentTime: TimeInterval = 0
    var duration: TimeInterval?
    private(set) var current: URL?
    private(set) var next: URL?
    private(set) var playing = false
    private(set) var loads: [(URL, TimeInterval)] = []

    func load(current: URL, next: URL?, at position: TimeInterval) {
        self.current = current
        self.next = next
        currentTime = position
        loads.append((current, position))
    }

    func setNext(_ next: URL?) { self.next = next }

    func play() {
        playing = true
        delegate?.audioEngineStatusChanged(isPlaying: true, isBuffering: false)
    }

    func pause() {
        playing = false
        delegate?.audioEngineStatusChanged(isPlaying: false, isBuffering: false)
    }

    func seek(to position: TimeInterval) { currentTime = position }

    func stop() {
        current = nil
        next = nil
        playing = false
    }

    /// 曲が最後まで鳴った。
    func finishCurrent() {
        if let next {
            current = next
            self.next = nil
            currentTime = 0
            delegate?.audioEngineDidAdvance()
        } else {
            current = nil
            playing = false
            delegate?.audioEngineDidFinish()
        }
    }

    func fail() { delegate?.audioEngineDidFail() }

    var currentId: String? { current?.deletingPathExtension().lastPathComponent }
    var nextId: String? { next?.deletingPathExtension().lastPathComponent }
}

@MainActor
final class FakeEnvironment: PlaybackEnvironment {
    var available: Set<String>
    var replaced: [String: String] = [:]
    var saved: QueueSnapshot?
    var started: [String] = []

    init(available: Set<String>) { self.available = available }

    func resolve(_ trackIds: [String]) async -> [PlayableTrack] {
        trackIds.compactMap { requested in
            let id = replaced[requested] ?? requested
            guard available.contains(id) else { return nil }
            return PlayableTrack(
                id: id, requestedId: requested, audioURL: URL(string: "https://classical-sound.up.seesaa.net/\(id).mp3")!,
                title: id, composer: "c", workTitle: "w", composerId: "c", genreId: "g"
            )
        }
    }

    func loadQueue() async -> QueueSnapshot? { saved }

    func saveQueue(_ snapshot: QueueSnapshot) async { saved = snapshot }

    func trackStarted(_ trackId: String) async { started.append(trackId) }
}

@MainActor
struct PlaybackControllerTests {
    let engine = FakeEngine()
    let env = FakeEnvironment(available: ["1", "2", "3", "4"])
    let controller: PlaybackController

    init() {
        controller = PlaybackController(
            engine: engine, environment: env, random: SeededGenerator(state: 1),
            saveDebounce: .milliseconds(1), saveInterval: .seconds(60)
        )
    }

    @Test func playsWorkInOrderWithNextPreloaded() async {
        await controller.play(PlayQueue(trackIds: ["1", "2", "3"]))
        #expect(engine.currentId == "1")
        #expect(engine.nextId == "2")
        #expect(engine.playing)
        engine.finishCurrent()
        #expect(controller.currentTrackId == "2")
        #expect(engine.nextId == "3")
        engine.finishCurrent()
        #expect(engine.nextId == nil)
        engine.finishCurrent()
        #expect(!controller.isPlaying)
        await Task.yield()
        #expect(env.started == ["1", "2", "3"])
    }

    @Test func skipsUnavailableTracksWhenBuildingQueue() async {
        await controller.play(PlayQueue(trackIds: ["1", "gone", "3"], startIndex: 2))
        #expect(controller.queue.trackIds == ["1", "3"])
        #expect(controller.currentTrackId == "3")
    }

    @Test func repeatOnePreloadsSameTrackAndRepeatAllWraps() async {
        await controller.play(PlayQueue(trackIds: ["1", "2"], startIndex: 1))
        #expect(engine.nextId == nil)
        controller.cycleRepeatMode() // All
        #expect(engine.nextId == "1")
        controller.cycleRepeatMode() // One
        #expect(engine.nextId == "2")
        controller.skipNext() // 手動の「次へ」は 1 曲リピートでも次の曲
        #expect(controller.currentTrackId == "1")
    }

    @Test func skipsFailedTrackAndStopsAfterThree() async {
        await controller.play(PlayQueue(trackIds: ["1", "2", "3", "4"]))
        engine.fail()
        #expect(controller.notice == .skipped)
        #expect(engine.currentId == "2")
        engine.fail()
        #expect(engine.currentId == "3")
        engine.fail()
        #expect(controller.notice == .stopped)
        #expect(!engine.playing)
    }

    @Test func previousRestartsTrackAfterThreeSeconds() async {
        await controller.play(PlayQueue(trackIds: ["1", "2"], startIndex: 1))
        engine.currentTime = 10
        controller.skipPrevious()
        #expect(controller.currentTrackId == "2")
        #expect(engine.currentTime == 0)
        controller.skipPrevious()
        #expect(controller.currentTrackId == "1")
    }

    @Test func playNextAndAddToQueue() async {
        await controller.play(PlayQueue(trackIds: ["1", "2"]))
        await controller.playNext("3")
        #expect(controller.queue.trackIds == ["1", "3", "2"])
        #expect(engine.nextId == "3")
        await controller.addToQueue("4")
        #expect(controller.queue.trackIds == ["1", "3", "2", "4"])
        controller.removeQueueItem(at: 1)
        #expect(engine.nextId == "2")
        controller.moveQueueItem(from: 2, to: 1)
        #expect(controller.queue.trackIds == ["1", "4", "2"])
        #expect(engine.nextId == "4")
    }

    @Test func removingCurrentLoadsNext() async {
        await controller.play(PlayQueue(trackIds: ["1", "2"]))
        controller.removeQueueItem(at: 0)
        #expect(engine.currentId == "2")
        controller.removeQueueItem(at: 0)
        #expect(controller.queue.isEmpty)
        #expect(engine.currentId == nil)
    }

    @Test func addToEmptyQueueStartsPlaying() async {
        await controller.addToQueue("2")
        #expect(engine.currentId == "2")
        #expect(engine.playing)
    }

    @Test func restoresSavedQueueWithoutPlaying() async {
        env.replaced = ["old": "3"]
        env.saved = QueueSnapshot(trackIds: ["1", "old", "gone"], index: 1, positionMs: 42_000, repeatMode: .all)
        await controller.restore()
        #expect(controller.queue.trackIds == ["1", "3"])
        #expect(controller.currentTrackId == "3")
        #expect(controller.position == 42)
        #expect(controller.queue.repeatMode == .all)
        #expect(engine.loads.isEmpty) // 自動では再生しない（読み込みもしない）
        controller.resume()
        #expect(engine.loads.last?.1 == 42)
        #expect(engine.currentId == "3")
        #expect(engine.nextId == "1")
    }

    @Test func savesSnapshotAfterChanges() async throws {
        await controller.play(PlayQueue(trackIds: ["1", "2"], startIndex: 1))
        engine.currentTime = 12.5
        await controller.saveNow()
        let saved = try #require(env.saved)
        #expect(saved.trackIds == ["1", "2"])
        #expect(saved.index == 1)
        #expect(saved.positionMs == 12_500)
    }

    @Test func shuffleDoesNotChangeCurrentTrack() async {
        await controller.play(PlayQueue(trackIds: ["1", "2", "3", "4"], startIndex: 1))
        controller.setShuffle(true)
        #expect(controller.currentTrackId == "2")
        #expect(controller.queue.shuffle)
        await controller.play(PlayQueue(trackIds: ["1", "2"]))
        #expect(!controller.queue.shuffle) // 新しいキューはシャッフルを切る
    }
}
