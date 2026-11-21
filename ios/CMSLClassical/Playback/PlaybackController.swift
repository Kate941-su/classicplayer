import Foundation
import Observation

/// 再生の窓口（SPEC §5）。画面からの操作・ロック画面の操作・割り込みを受けて、キューとエンジンを動かす。
///
/// - 次の曲は [PlaybackQueue] で決め、エンジンには「今の曲」と「次の曲」だけを渡す（ギャップレスのための先読み）
/// - 再生できない曲は飛ばし、3 曲続けて失敗したら止める（`notice` で画面に知らせる）
/// - キューと位置は少し待ってまとめて保存し、再生中は 15 秒ごとにも保存する。起動時に復元する（自動では再生しない）
@MainActor
@Observable
final class PlaybackController {
    enum Notice: Equatable, Sendable {
        case skipped
        case stopped
    }

    private(set) var queue = PlaybackQueue()
    private(set) var isPlaying = false
    private(set) var isBuffering = false
    private(set) var position: TimeInterval = 0
    /// 不明なら 0。
    private(set) var duration: TimeInterval = 0
    /// 画面に一度だけ出すお知らせ（トースト）。出したら `clearNotice()`。
    private(set) var notice: Notice?
    /// 今の曲の、ロック画面用の情報。
    private(set) var currentTrack: PlayableTrack?
    /// 今の曲が終わったら止める（スリープタイマーの「この曲の終わりまで」）。次の曲を先読みしない。
    var stopsAtEndOfTrack = false {
        didSet {
            guard stopsAtEndOfTrack != oldValue else { return }
            refreshPreload()
        }
    }
    /// 曲の終わりで止まったとき（スリープタイマーが解除するため）。
    @ObservationIgnored var onStoppedAtEndOfTrack: (() -> Void)?

    var currentTrackId: String? { currentTrack?.id ?? queue.currentTrackId }

    @ObservationIgnored private let engine: any AudioEngine
    @ObservationIgnored private weak var environment: (any PlaybackEnvironment)?
    @ObservationIgnored private var resolved: [String: PlayableTrack] = [:]
    @ObservationIgnored private var errorPolicy = ErrorSkipPolicy()
    /// エンジンに読み込んである曲（key）と、先読みしてある曲（key）。
    @ObservationIgnored private var loadedKey: Int?
    @ObservationIgnored private var preloadedKey: Int?
    /// 復元した位置（最初に再生するときに使う）。
    @ObservationIgnored private var pendingPosition: TimeInterval = 0
    @ObservationIgnored private var startedKey: Int?
    @ObservationIgnored private var saveTask: Task<Void, Never>?
    @ObservationIgnored private var periodicSaveTask: Task<Void, Never>?
    @ObservationIgnored private var restored = false
    @ObservationIgnored var onChange: (() -> Void)?
    @ObservationIgnored private var random: AnyRandomNumberGenerator
    @ObservationIgnored private let saveDebounce: Duration
    @ObservationIgnored private let saveInterval: Duration

    init(
        engine: any AudioEngine,
        environment: any PlaybackEnvironment,
        random: any RandomNumberGenerator = SystemRandomNumberGenerator(),
        saveDebounce: Duration = .seconds(1),
        saveInterval: Duration = .seconds(15)
    ) {
        self.engine = engine
        self.environment = environment
        self.random = AnyRandomNumberGenerator(base: random)
        self.saveDebounce = saveDebounce
        self.saveInterval = saveInterval
        engine.delegate = self
    }

    // MARK: - 操作

    func play(_ request: PlayQueue) async {
        guard !request.trackIds.isEmpty else { return }
        await resolve(request.trackIds)
        let ids = request.trackIds.compactMap { resolved[$0]?.id }
        guard !ids.isEmpty else { return }
        let start = request.trackIds.prefix(request.startIndex).filter { resolved[$0] != nil }.count
        let repeatMode = queue.repeatMode
        // 作品順などの並びを崩さないよう、シャッフルを切ってから入れる
        queue = PlaybackQueue(trackIds: ids, startIndex: min(start, ids.count - 1))
        queue.repeatMode = repeatMode
        pendingPosition = 0
        errorPolicy.onReady()
        loadCurrent(at: 0)
        engine.play()
        changed()
    }

    func togglePlayPause() {
        if isPlaying { pause() } else { resume() }
    }

    func resume() {
        guard !queue.isEmpty else { return }
        if loadedKey != queue.currentKey { loadCurrent(at: pendingPosition) }
        pendingPosition = 0
        engine.play()
    }

    func pause() {
        engine.pause()
        scheduleSave()
    }

    func skipNext() {
        guard let key = queue.nextKey(automatic: false) else { return }
        jump(to: key)
    }

    /// 3 秒より後ろなら曲の頭へ、それより前なら前の曲へ（前がなければ頭へ）。
    func skipPrevious() {
        if currentPosition > 3 || queue.previousKey() == nil {
            seek(to: 0)
        } else if let key = queue.previousKey() {
            jump(to: key)
        }
    }

    func seek(to seconds: TimeInterval) {
        let target = max(0, seconds)
        if loadedKey == queue.currentKey, loadedKey != nil {
            engine.seek(to: target)
        } else {
            pendingPosition = target
        }
        position = target
        changed()
        scheduleSave()
    }

    func setShuffle(_ enabled: Bool) {
        queue.setShuffle(enabled, using: &random)
        refreshPreload()
        changed()
        scheduleSave()
    }

    func cycleRepeatMode() {
        queue.repeatMode = queue.repeatMode.next
        refreshPreload()
        changed()
        scheduleSave()
    }

    /// 今の曲の次に入れる。
    func playNext(_ trackId: String) async {
        await insert(trackId) { $0.insertNext($1) }
    }

    /// キューの最後に入れる。
    func addToQueue(_ trackId: String) async {
        await insert(trackId) { $0.append($1) }
    }

    func moveQueueItem(from: Int, to: Int) {
        queue.move(from: from, to: to)
        refreshPreload()
        changed()
        scheduleSave()
    }

    func removeQueueItem(at index: Int) {
        let wasCurrent = queue.items.indices.contains(index) && queue.items[index].key == queue.currentKey
        queue.remove(at: index)
        if queue.isEmpty {
            stopAndClear()
        } else if wasCurrent {
            let playing = isPlaying
            loadCurrent(at: 0)
            if playing { engine.play() }
        } else {
            refreshPreload()
        }
        changed()
        scheduleSave()
    }

    func skipToQueueItem(at index: Int) {
        guard queue.items.indices.contains(index) else { return }
        jump(to: queue.items[index].key, play: true)
    }

    func clearNotice() { notice = nil }

    /// 0〜1。スリープタイマーで音量を下げるときに使う。
    var volume: Float {
        get { engine.volume }
        set { engine.volume = min(max(newValue, 0), 1) }
    }

    /// 曲の再生情報を取り直す（ダウンロードが終わった・消えたとき。URL を選び直す）。
    /// 先読みしている次の曲なら、エンジンの先読みも差し替える。
    func forgetResolved(_ trackId: String) {
        let keys = resolved.filter { $0.key == trackId || $0.value.id == trackId }.map(\.key)
        guard !keys.isEmpty, let environment else { return }
        Task {
            // 取り直してから差し替える（途中で情報がない時間を作らない）
            for track in await environment.resolve(keys) {
                resolved[track.requestedId] = track
                resolved[track.id] = track
            }
            if let key = preloadedKey, queue.item(key: key)?.trackId == trackId {
                preloadedKey = nil
                refreshPreload()
            }
        }
    }

    // MARK: - 復元と保存

    /// 保存しておいたキューと位置を復元する（自動では再生しない。SPEC §5）。最初の 1 回だけ。
    func restore() async {
        guard !restored, queue.isEmpty, let environment else { return }
        restored = true
        guard let saved = await environment.loadQueue() else { return }
        await resolve(saved.trackIds)
        // 置き換えた曲は置き換え先の ID にしてから、消えた曲を外す
        var mapped = saved
        mapped.trackIds = saved.trackIds.map { resolved[$0]?.id ?? $0 }
        guard queue.isEmpty, let snapshot = mapped.restorable(isAvailable: { resolved[$0] != nil }) else { return }
        queue = PlaybackQueue(trackIds: snapshot.trackIds, startIndex: snapshot.index)
        queue.repeatMode = snapshot.repeatMode
        if snapshot.shuffle { queue.setShuffle(true, using: &random) }
        pendingPosition = Double(snapshot.positionMs) / 1000
        position = pendingPosition
        currentTrack = queue.currentTrackId.flatMap { resolved[$0] }
        duration = 0
        changed()
    }

    var snapshot: QueueSnapshot? {
        guard let index = queue.currentIndex else { return nil }
        return QueueSnapshot(
            trackIds: queue.trackIds, index: index, positionMs: Int64(currentPosition * 1000),
            shuffle: queue.shuffle, repeatMode: queue.repeatMode
        )
    }

    func saveNow() async {
        saveTask?.cancel()
        guard let snapshot, let environment else { return }
        await environment.saveQueue(snapshot)
    }

    private func scheduleSave() {
        saveTask?.cancel()
        let delay = saveDebounce
        saveTask = Task { [weak self] in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            await self?.saveNow()
        }
    }

    // MARK: - 内部

    private var currentPosition: TimeInterval {
        loadedKey != nil && loadedKey == queue.currentKey ? engine.currentTime : pendingPosition
    }

    private func resolve(_ ids: [String]) async {
        let missing = ids.filter { resolved[$0] == nil }
        guard !missing.isEmpty, let environment else { return }
        for track in await environment.resolve(missing) {
            resolved[track.requestedId] = track
            resolved[track.id] = track
        }
    }

    private func insert(_ trackId: String, _ edit: (inout PlaybackQueue, String) -> Void) async {
        await resolve([trackId])
        guard let id = resolved[trackId]?.id else { return }
        if queue.isEmpty {
            await play(PlayQueue(trackIds: [id]))
            return
        }
        edit(&queue, id)
        refreshPreload()
        changed()
        scheduleSave()
    }

    private func jump(to key: Int, play: Bool? = nil) {
        let playing = play ?? isPlaying
        queue.setCurrent(key: key)
        pendingPosition = 0
        loadCurrent(at: 0)
        if playing { engine.play() }
        changed()
        scheduleSave()
    }

    private func loadCurrent(at seconds: TimeInterval) {
        guard let key = queue.currentKey, let item = queue.item(key: key), let track = resolved[item.trackId] else {
            stopAndClear()
            return
        }
        let next = nextTrack()
        engine.load(current: track.audioURL, next: next?.track.audioURL, at: seconds)
        loadedKey = key
        preloadedKey = next?.key
        startedKey = nil
        currentTrack = track
        position = seconds
        duration = 0
    }

    private func nextTrack() -> (key: Int, track: PlayableTrack)? {
        guard !stopsAtEndOfTrack, let key = queue.nextKey(automatic: true), let item = queue.item(key: key),
              let track = resolved[item.trackId] else { return nil }
        return (key, track)
    }

    /// キューやリピートが変わったら、先読みしている次の曲を合わせる。
    private func refreshPreload() {
        guard loadedKey != nil, loadedKey == queue.currentKey else { return }
        let next = nextTrack()
        guard next?.key != preloadedKey else { return }
        engine.setNext(next?.track.audioURL)
        preloadedKey = next?.key
    }

    private func stopAndClear() {
        engine.stop()
        queue = PlaybackQueue()
        loadedKey = nil
        preloadedKey = nil
        currentTrack = nil
        isPlaying = false
        position = 0
        duration = 0
    }

    private func recordStarted() {
        guard isPlaying, let key = queue.currentKey, key != startedKey, let id = queue.item(key: key)?.trackId else { return }
        startedKey = key
        Task { await environment?.trackStarted(id) }
    }

    private func changed() { onChange?() }

    private func startPeriodicSave() {
        guard periodicSaveTask == nil else { return }
        let interval = saveInterval
        periodicSaveTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: interval)
                guard !Task.isCancelled else { return }
                await self?.saveNow()
            }
        }
    }
}

extension PlaybackController: AudioEngineDelegate {
    func audioEngineDidAdvance() {
        guard let key = preloadedKey else { return }
        queue.setCurrent(key: key)
        loadedKey = key
        startedKey = nil
        currentTrack = queue.item(key: key).flatMap { resolved[$0.trackId] }
        position = 0
        duration = 0
        // 次の次を先読みする
        let next = nextTrack()
        engine.setNext(next?.track.audioURL)
        preloadedKey = next?.key
        recordStarted()
        changed()
        scheduleSave()
    }

    func audioEngineDidFinish() {
        if stopsAtEndOfTrack {
            // スリープタイマー: 曲の終わりで止める。次の曲を「今の曲」にして、▶ でそこから聴けるようにする
            stopsAtEndOfTrack = false
            if let key = queue.nextKey(automatic: true) { queue.setCurrent(key: key) }
            currentTrack = queue.currentTrackId.flatMap { resolved[$0] }
            onStoppedAtEndOfTrack?()
        }
        // 最後まで再生した（か、曲の終わりで止めた）。今の曲の頭で止めておく（▶ で頭から）
        loadedKey = nil
        preloadedKey = nil
        pendingPosition = 0
        position = 0
        isPlaying = false
        changed()
        scheduleSave()
    }

    func audioEngineDidFail() {
        let next = queue.nextKey(automatic: false)
        switch errorPolicy.onError(hasNext: next != nil) {
        case .skip:
            notice = .skipped
            if let next { jump(to: next, play: true) }
        case .stop:
            notice = .stopped
            engine.pause()
            loadedKey = nil
            isPlaying = false
            changed()
        }
    }

    func audioEngineDidBecomeReady() {
        errorPolicy.onReady()
        if let seconds = engine.duration { duration = seconds }
        changed()
    }

    func audioEngineStatusChanged(isPlaying: Bool, isBuffering: Bool) {
        self.isPlaying = isPlaying
        self.isBuffering = isBuffering
        if isPlaying {
            recordStarted()
            startPeriodicSave()
        } else {
            periodicSaveTask?.cancel()
            periodicSaveTask = nil
        }
        changed()
        scheduleSave()
    }

    func audioEngineTimeChanged() {
        guard loadedKey != nil else { return }
        position = engine.currentTime
        if let seconds = engine.duration { duration = seconds }
    }
}
