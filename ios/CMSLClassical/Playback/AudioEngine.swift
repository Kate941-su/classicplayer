import AVFoundation
import os

private let log = Logger(subsystem: "app.cmsl.classical", category: "playback")

/// 音を鳴らす部分（テストではフェイクに差し替える）。今の曲と、先読みする次の曲だけを持つ。
@MainActor
protocol AudioEngine: AnyObject {
    var delegate: (any AudioEngineDelegate)? { get set }
    var currentTime: TimeInterval { get }
    /// 不明なら nil。
    var duration: TimeInterval? { get }

    /// 今の曲を差し替える（再生はしない）。`next` は終わったら続けて鳴らす曲（ギャップレスのための先読み）。
    func load(current: URL, next: URL?, at position: TimeInterval)
    /// 先読みする次の曲だけを差し替える。
    func setNext(_ next: URL?)
    func play()
    func pause()
    func seek(to position: TimeInterval)
    /// 曲を外して止める。
    func stop()
}

@MainActor
protocol AudioEngineDelegate: AnyObject {
    /// 今の曲が終わり、先読みしていた次の曲に進んだ。
    func audioEngineDidAdvance()
    /// 最後の曲が終わった（次の曲がなかった）。
    func audioEngineDidFinish()
    /// 今の曲を再生できない（404・通信エラーなど）。
    func audioEngineDidFail()
    /// 再生の準備ができた（エラーの連続を数え直す）。
    func audioEngineDidBecomeReady()
    /// 再生中か・読み込み中かが変わった。
    func audioEngineStatusChanged(isPlaying: Bool, isBuffering: Bool)
    func audioEngineTimeChanged()
}

/// AVQueuePlayer で鳴らす。キューには「今の曲」と「次の曲」の 2 つだけを入れ、残りはコントローラーが決める。
@MainActor
final class AVQueueAudioEngine: AudioEngine {
    weak var delegate: (any AudioEngineDelegate)?
    private let player = AVQueuePlayer()
    private var currentItem: AVPlayerItem?
    private var nextItem: AVPlayerItem?
    private var observations: [NSKeyValueObservation] = []
    private var itemObservations: [NSKeyValueObservation] = []
    private var timeObserver: Any?
    private var playRequest = 0

    init() {
        player.actionAtItemEnd = .advance
        observations.append(player.observe(\.currentItem, options: [.new]) { [weak self] player, _ in
            let item = player.currentItem
            Task { @MainActor in self?.currentItemChanged(item) }
        })
        observations.append(player.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            let status = player.timeControlStatus
            Task { @MainActor in self?.timeControlChanged(status) }
        })
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.delegate?.audioEngineTimeChanged() }
        }
    }

    var currentTime: TimeInterval {
        let seconds = player.currentTime().seconds
        return seconds.isFinite ? max(seconds, 0) : 0
    }

    var duration: TimeInterval? {
        guard let seconds = player.currentItem?.duration.seconds, seconds.isFinite, seconds > 0 else { return nil }
        return seconds
    }

    func load(current: URL, next: URL?, at position: TimeInterval) {
        player.removeAllItems()
        let item = makeItem(current)
        currentItem = item
        nextItem = nil
        player.insert(item, after: nil)
        setNext(next)
        if position > 0 {
            item.seek(to: CMTime(seconds: position, preferredTimescale: 600), completionHandler: nil)
        }
        observeStatus(of: item)
    }

    func setNext(_ next: URL?) {
        if let nextItem, player.items().contains(nextItem) { player.remove(nextItem) }
        nextItem = nil
        guard let next, let currentItem else { return }
        let item = makeItem(next)
        nextItem = item
        player.insert(item, after: currentItem)
    }

    func play() {
        playRequest += 1
        let request = playRequest
        Task {
            // 再生の前にセッションを有効にする（ほかのアプリの音を止める）。時間がかかることがあるのでメインスレッドの外で
            await Self.activateSession()
            // 待っている間に一時停止や別の再生が入ったら、この再生はしない
            guard request == playRequest else { return }
            player.play()
        }
    }

    private nonisolated static func activateSession() async {
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    func pause() {
        playRequest += 1
        player.pause()
    }

    func seek(to position: TimeInterval) {
        player.seek(to: CMTime(seconds: position, preferredTimescale: 600))
    }

    func stop() {
        playRequest += 1
        player.pause()
        player.removeAllItems()
        currentItem = nil
        nextItem = nil
        itemObservations = []
    }

    private func makeItem(_ url: URL) -> AVPlayerItem {
        let item = AVPlayerItem(url: url)
        NotificationCenter.default.addObserver(
            self, selector: #selector(itemFailedToPlayToEnd(_:)),
            name: AVPlayerItem.failedToPlayToEndTimeNotification, object: item
        )
        return item
    }

    private func observeStatus(of item: AVPlayerItem) {
        itemObservations = [item.observe(\.status, options: [.new]) { [weak self] item, _ in
            let status = item.status
            Task { @MainActor in self?.statusChanged(status, of: item) }
        }]
        if item.status != .unknown { statusChanged(item.status, of: item) }
    }

    private func currentItemChanged(_ item: AVPlayerItem?) {
        guard item !== currentItem else { return }
        if let item, item === nextItem {
            currentItem = item
            nextItem = nil
            observeStatus(of: item)
            delegate?.audioEngineDidAdvance()
        } else if item == nil, currentItem != nil {
            currentItem = nil
            itemObservations = []
            delegate?.audioEngineDidFinish()
        }
    }

    private func statusChanged(_ status: AVPlayerItem.Status, of item: AVPlayerItem) {
        guard item === currentItem else { return }
        switch status {
        case .readyToPlay: delegate?.audioEngineDidBecomeReady()
        case .failed:
            log.error("再生できない: \(item.error?.localizedDescription ?? "-", privacy: .public)")
            delegate?.audioEngineDidFail()
        default: break
        }
    }

    @objc private nonisolated func itemFailedToPlayToEnd(_ notification: Notification) {
        guard let object = notification.object as AnyObject? else { return }
        let id = ObjectIdentifier(object)
        Task { @MainActor in
            guard let current = self.currentItem, ObjectIdentifier(current) == id else { return }
            log.error("途中で止まった: \(current.error?.localizedDescription ?? "-", privacy: .public)")
            self.delegate?.audioEngineDidFail()
        }
    }

    private func timeControlChanged(_ status: AVPlayer.TimeControlStatus) {
        delegate?.audioEngineStatusChanged(
            isPlaying: status != .paused, isBuffering: status == .waitingToPlayAtSpecifiedRate
        )
    }
}
