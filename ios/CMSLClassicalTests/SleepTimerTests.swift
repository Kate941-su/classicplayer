import Foundation
import Testing
@testable import CMSLClassical

@MainActor
struct SleepTimerTests {
    let engine = FakeEngine()
    let env = FakeEnvironment(available: ["1", "2", "3"])
    let controller: PlaybackController
    let timer: SleepTimer

    init() {
        controller = PlaybackController(engine: engine, environment: env, saveDebounce: .milliseconds(1))
        timer = SleepTimer(playback: controller, fadeDuration: .milliseconds(40))
    }

    private func waitUntil(_ condition: () -> Bool) async {
        for _ in 0..<200 where !condition() { try? await Task.sleep(for: .milliseconds(5)) }
    }

    @Test func pausesAfterFadingAndRestoresVolume() async {
        await controller.play(PlayQueue(trackIds: ["1", "2"]))
        timer.start(after: .milliseconds(20))
        #expect(timer.remaining(at: .now) ?? 0 > 0)
        await waitUntil { !engine.playing }
        #expect(!engine.playing)
        #expect(engine.volume == 1)
        #expect(timer.mode == .off)
        #expect(controller.currentTrackId == "1")
    }

    @Test func doesNothingWhenAlreadyPaused() async {
        await controller.play(PlayQueue(trackIds: ["1"]))
        controller.pause()
        timer.start(after: .milliseconds(10))
        await waitUntil { timer.mode == .off }
        #expect(timer.mode == .off)
        #expect(engine.volume == 1)
    }

    @Test func cancelKeepsPlaying() async throws {
        await controller.play(PlayQueue(trackIds: ["1"]))
        timer.start(after: .milliseconds(20))
        timer.cancel()
        try await Task.sleep(for: .milliseconds(120))
        #expect(engine.playing)
        #expect(timer.mode == .off)
    }

    @Test func restartingReplacesTheOldTimer() async throws {
        await controller.play(PlayQueue(trackIds: ["1"]))
        timer.start(after: .milliseconds(20))
        timer.start(minutes: 30)
        try await Task.sleep(for: .milliseconds(120))
        #expect(engine.playing)
        let remaining = try #require(timer.remaining(at: .now))
        #expect(remaining > 29 * 60)
    }

    @Test func endOfTrackStopsWithoutAdvancing() async {
        await controller.play(PlayQueue(trackIds: ["1", "2", "3"]))
        #expect(engine.nextId == "2")
        timer.stopAtEndOfTrack()
        #expect(engine.nextId == nil) // 次の曲を先読みしない
        engine.finishCurrent()
        #expect(!controller.isPlaying)
        #expect(controller.currentTrackId == "2") // ▶ で次の曲から
        #expect(timer.mode == .off)
        controller.resume()
        #expect(engine.currentId == "2")
        #expect(engine.nextId == "3") // 先読みが戻る
    }

    @Test func cancellingEndOfTrackRestoresPreload() async {
        await controller.play(PlayQueue(trackIds: ["1", "2"]))
        timer.stopAtEndOfTrack()
        timer.cancel()
        #expect(engine.nextId == "2")
    }
}
