import AVFoundation
import UIKit
import os

private let log = Logger(subsystem: "jp.vntech.classic", category: "playback")

/// 音を鳴らす部分（テストではフェイクに差し替える）。今の曲と、先読みする次の曲だけを持つ。
@MainActor
protocol AudioEngine: AnyObject {
    var delegate: (any AudioEngineDelegate)? { get set }
    var currentTime: TimeInterval { get }
    /// 不明なら nil。
    var duration: TimeInterval? { get }
    /// 0〜1（スリープタイマーで音量を下げるときだけ使う。端末の音量とは別）。
    var volume: Float { get set }

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

/// 長さが分かった状態のアセットを用意する。
///
/// seesaa の配信元（Cloudflare）は、キャッシュにない曲の最初のリクエストで Range に対応しない（206 ではなく 200 を返す）。
/// そのとき AVFoundation の長さは indefinite のままで、シークもできない。新しいアセットで読み直せばキャッシュから
/// 返って長さが分かるので、分かるまで繰り返す。それでも分からなければ一時ファイルに取得して読む。
@MainActor
struct AssetPreparer {
    struct Prepared {
        var asset: AVURLAsset
        /// 一時ファイルからも読めなかったときだけ nil。
        var duration: TimeInterval?
    }

    var attempts = 6
    var retryDelay: Duration = .seconds(1)
    var loadDuration: @MainActor (AVURLAsset) async throws -> TimeInterval = { try await $0.load(.duration).seconds }
    var download: @MainActor (URL) async throws -> URL = { try await AssetPreparer.downloadToTemporaryFile($0) }

    func prepare(_ url: URL) async throws -> Prepared {
        if url.isFileURL { return try await load(url) }
        for attempt in 0..<attempts {
            if attempt > 0 { try await Task.sleep(for: retryDelay) }
            let prepared = try await load(url)
            if prepared.duration != nil { return prepared }
            prepared.asset.cancelLoading()
        }
        log.info("長さが分からないので一時ファイルに取得する")
        let file = try await download(url)
        do {
            try Task.checkCancellation()
            return try await load(file)
        } catch {
            try? FileManager.default.removeItem(at: file)
            throw error
        }
    }

    private func load(_ url: URL) async throws -> Prepared {
        let asset = AVURLAsset(url: url)
        let seconds = try await loadDuration(asset)
        try Task.checkCancellation()
        return Prepared(asset: asset, duration: seconds.isFinite && seconds > 0 ? seconds : nil)
    }

    static let temporaryDirectory = FileManager.default.temporaryDirectory.appending(path: "playback")

    static func removeTemporaryFiles() {
        try? FileManager.default.removeItem(at: temporaryDirectory)
    }

    private static func downloadToTemporaryFile(_ url: URL) async throws -> URL {
        let (file, response) = try await URLSession.shared.download(from: url)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else {
            try? FileManager.default.removeItem(at: file)
            throw URLError(.badServerResponse)
        }
        try FileManager.default.createDirectory(at: temporaryDirectory, withIntermediateDirectories: true)
        let target = temporaryDirectory.appending(path: UUID().uuidString + ".mp3")
        try FileManager.default.moveItem(at: file, to: target)
        return target
    }
}

/// AVQueuePlayer で鳴らす。キューには「今の曲」と「次の曲」の 2 つだけを入れ、残りはコントローラーが決める。
/// どちらも長さが分かってからプレイヤーに入れる（それまでは読み込み中として扱い、再生を始めない）。
@MainActor
final class AVQueueAudioEngine: AudioEngine {
    /// 今の曲・次の曲。準備ができるまで `item` は nil。
    private final class Slot {
        let url: URL
        /// 準備ができたら頭出しする位置。
        var startPosition: TimeInterval
        var item: AVPlayerItem?
        var duration: TimeInterval?
        var failed = false
        /// プレイヤーの今の曲になったことがある（曲の終わりと、まだ入れていない状態を区別する）。
        var becameCurrent = false
        var task: Task<Void, Never>?

        init(url: URL, startPosition: TimeInterval = 0) {
            self.url = url
            self.startPosition = startPosition
        }
    }

    weak var delegate: (any AudioEngineDelegate)?
    private let player = AVQueuePlayer()
    private let preparer: AssetPreparer
    private var current: Slot?
    private var next: Slot?
    private var observations: [NSKeyValueObservation] = []
    private var itemObservations: [NSKeyValueObservation] = []
    private var timeObserver: Any?
    private var playRequest = 0
    /// 再生を頼まれたが、まだ鳴らし始めていない（曲かセッションの準備を待っている）。
    private var pendingPlay = false
    private var backgroundTask = UIBackgroundTaskIdentifier.invalid

    init(preparer: AssetPreparer = AssetPreparer()) {
        self.preparer = preparer
        AssetPreparer.removeTemporaryFiles()
        player.actionAtItemEnd = .advance
        observations.append(player.observe(\.currentItem, options: [.new]) { [weak self] _, _ in
            Task { @MainActor in self?.currentItemChanged() }
        })
        observations.append(player.observe(\.timeControlStatus, options: [.new]) { [weak self] _, _ in
            Task { @MainActor in self?.notifyStatus() }
        })
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.delegate?.audioEngineTimeChanged() }
        }
    }

    var currentTime: TimeInterval {
        if let current, current.item == nil { return current.startPosition }
        let seconds = player.currentTime().seconds
        return seconds.isFinite ? max(seconds, 0) : 0
    }

    var volume: Float {
        get { player.volume }
        set { player.volume = newValue }
    }

    var duration: TimeInterval? {
        guard let current, let item = current.item else { return nil }
        let seconds = item.duration.seconds
        return seconds.isFinite && seconds > 0 ? seconds : current.duration
    }

    func load(current url: URL, next: URL?, at position: TimeInterval) {
        stop()
        let slot = Slot(url: url, startPosition: position)
        current = slot
        prepare(slot)
        setNext(next)
    }

    func setNext(_ url: URL?) {
        next?.task?.cancel()
        if let item = next?.item, player.items().contains(item) { player.remove(item) }
        next = nil
        guard let url, current != nil else { return }
        let slot = Slot(url: url)
        next = slot
        prepare(slot)
    }

    func play() {
        guard current != nil else { return }
        pendingPlay = true
        notifyStatus()
        startIfReady()
    }

    func pause() {
        cancelPendingPlay()
        player.pause()
        notifyStatus()
    }

    func seek(to position: TimeInterval) {
        if let current, current.item == nil {
            current.startPosition = position
        } else {
            player.seek(to: CMTime(seconds: position, preferredTimescale: 600))
        }
    }

    func stop() {
        cancelPendingPlay()
        player.pause()
        current?.task?.cancel()
        next?.task?.cancel()
        current = nil
        next = nil
        itemObservations = []
        player.removeAllItems()
        AssetPreparer.removeTemporaryFiles()
    }

    // MARK: - 準備

    private func prepare(_ slot: Slot) {
        slot.task = Task { [weak self, preparer] in
            do {
                let prepared = try await preparer.prepare(slot.url)
                guard !Task.isCancelled else { return }
                self?.prepared(prepared, for: slot)
            } catch {
                guard !Task.isCancelled else { return }
                log.error("準備できない: \(error.localizedDescription, privacy: .public)")
                self?.preparationFailed(slot)
            }
        }
    }

    private func prepared(_ prepared: AssetPreparer.Prepared, for slot: Slot) {
        // 今の曲が終わった直後（通知がまだ届いていない）なら、先に次の曲へ進める
        if slot === next, player.currentItem == nil, current?.becameCurrent == true { currentEnded() }
        let item = AVPlayerItem(asset: prepared.asset)
        slot.item = item
        slot.duration = prepared.duration
        NotificationCenter.default.addObserver(
            self, selector: #selector(itemFailedToPlayToEnd(_:)),
            name: AVPlayerItem.failedToPlayToEndTimeNotification, object: item
        )
        if slot === current {
            insertCurrent(slot, item)
        } else if slot === next {
            insertNextIfReady()
        }
    }

    private func insertCurrent(_ slot: Slot, _ item: AVPlayerItem) {
        player.insert(item, after: nil)
        if slot.startPosition > 0 {
            item.seek(to: CMTime(seconds: slot.startPosition, preferredTimescale: 600), completionHandler: nil)
        }
        insertNextIfReady()
        observeStatus(of: item)
        delegate?.audioEngineTimeChanged()
        startIfReady()
    }

    private func insertNextIfReady() {
        guard let item = next?.item, current?.item != nil, !player.items().contains(item) else { return }
        player.insert(item, after: nil)
    }

    private func preparationFailed(_ slot: Slot) {
        slot.failed = true
        // 次の曲なら、その曲に進んだときに知らせる
        guard slot === current else { return }
        cancelPendingPlay()
        delegate?.audioEngineDidFail()
    }

    // MARK: - 再生の開始

    private func startIfReady() {
        guard pendingPlay else { return }
        // 画面ロック中に準備を待つ間（音が出ていない間）、アプリが止められないようにする
        beginBackgroundTask()
        guard current?.item != nil else { return }
        playRequest += 1
        let request = playRequest
        Task {
            // 再生の前にセッションを有効にする（ほかのアプリの音を止める）。時間がかかることがあるのでメインスレッドの外で
            await Self.activateSession()
            // 待っている間に一時停止や別の再生が入ったら、この再生はしない
            guard request == playRequest else { return }
            pendingPlay = false
            player.play()
            endBackgroundTask()
            notifyStatus()
        }
    }

    private func cancelPendingPlay() {
        playRequest += 1
        pendingPlay = false
        endBackgroundTask()
    }

    private nonisolated static func activateSession() async {
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    private func beginBackgroundTask() {
        guard backgroundTask == .invalid else { return }
        backgroundTask = UIApplication.shared.beginBackgroundTask { [weak self] in
            MainActor.assumeIsolated { self?.endBackgroundTask() }
        }
    }

    private func endBackgroundTask() {
        guard backgroundTask != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTask)
        backgroundTask = .invalid
    }

    // MARK: - プレイヤーの変化

    private func observeStatus(of item: AVPlayerItem) {
        itemObservations = [item.observe(\.status, options: [.new]) { [weak self] item, _ in
            let status = item.status
            Task { @MainActor in self?.statusChanged(status, of: item) }
        }]
        if item.status != .unknown { statusChanged(item.status, of: item) }
    }

    private func currentItemChanged() {
        if let item = player.currentItem {
            if item === current?.item {
                current?.becameCurrent = true
            } else if item === next?.item {
                advance()
                observeStatus(of: item)
                current?.becameCurrent = true
                delegate?.audioEngineDidAdvance()
            }
        } else if current?.becameCurrent == true {
            currentEnded()
        }
    }

    /// 今の曲が終わり、プレイヤーに次の曲が入っていない（次の曲がないか、まだ準備中）。
    private func currentEnded() {
        itemObservations = []
        guard let upcoming = next else {
            current = nil
            // キューが空でも AVQueuePlayer は再生待ち（waiting）のままになるので止める
            player.pause()
            delegate?.audioEngineDidFinish()
            return
        }
        advance()
        player.pause()
        pendingPlay = true
        delegate?.audioEngineDidAdvance()
        if upcoming.failed {
            cancelPendingPlay()
            delegate?.audioEngineDidFail()
        } else {
            notifyStatus()
            startIfReady()
        }
    }

    private func advance() {
        current = next
        next = nil
    }

    private func statusChanged(_ status: AVPlayerItem.Status, of item: AVPlayerItem) {
        guard item === current?.item else { return }
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
            guard let item = self.current?.item, ObjectIdentifier(item) == id else { return }
            log.error("途中で止まった: \(item.error?.localizedDescription ?? "-", privacy: .public)")
            self.delegate?.audioEngineDidFail()
        }
    }

    private func notifyStatus() {
        let status = player.timeControlStatus
        delegate?.audioEngineStatusChanged(
            isPlaying: pendingPlay || status != .paused,
            isBuffering: pendingPlay || status == .waitingToPlayAtSpecifiedRate
        )
    }
}
