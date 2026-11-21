import Foundation
import Observation

/// スリープタイマー（SPEC §5.2）。時間になったら音量を下げてから一時停止し、音量を戻す。
/// 「この曲の終わりまで」は、今の曲が終わったところで止める（次の曲に進まない）。保存はしない。
@MainActor
@Observable
final class SleepTimer {
    enum Mode: Equatable {
        case off
        case until(Date)
        case endOfTrack
    }

    static let choices = [15, 30, 45, 60, 90]

    private(set) var mode: Mode = .off

    @ObservationIgnored private let playback: PlaybackController
    @ObservationIgnored private let fadeDuration: Duration
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private var task: Task<Void, Never>?
    /// 設定し直したら増やす（前のタイマーが後から状態を変えないように）。
    @ObservationIgnored private var generation = 0

    init(playback: PlaybackController, fadeDuration: Duration = .seconds(10), now: @escaping () -> Date = Date.init) {
        self.playback = playback
        self.fadeDuration = fadeDuration
        self.now = now
        playback.onStoppedAtEndOfTrack = { [weak self] in self?.mode = .off }
    }

    /// 残り時間（時間で止めるときだけ）。
    func remaining(at date: Date) -> TimeInterval? {
        guard case let .until(end) = mode else { return nil }
        return max(end.timeIntervalSince(date), 0)
    }

    func start(minutes: Int) { start(after: .seconds(minutes * 60)) }

    func start(after duration: Duration) {
        cancel()
        let seconds = Double(duration.components.seconds) + Double(duration.components.attoseconds) / 1e18
        mode = .until(now().addingTimeInterval(seconds))
        let current = generation
        task = Task { [weak self] in
            try? await Task.sleep(for: duration)
            guard !Task.isCancelled else { return }
            await self?.fire(current)
        }
    }

    func stopAtEndOfTrack() {
        cancel()
        mode = .endOfTrack
        playback.stopsAtEndOfTrack = true
    }

    func cancel() {
        generation += 1
        task?.cancel()
        task = nil
        playback.stopsAtEndOfTrack = false
        mode = .off
    }

    /// 時間になった: 再生中なら音量を下げてから一時停止し、音量を戻す。止まっていれば何もしない。
    private func fire(_ token: Int) async {
        defer {
            if generation == token {
                task = nil
                mode = .off
            }
        }
        guard playback.isPlaying else { return }
        let original = playback.volume
        let steps = 20
        for step in 1...steps {
            try? await Task.sleep(for: fadeDuration / steps)
            if Task.isCancelled {
                playback.volume = original
                return
            }
            playback.volume = original * Float(steps - step) / Float(steps)
        }
        playback.pause()
        playback.volume = original
    }
}
