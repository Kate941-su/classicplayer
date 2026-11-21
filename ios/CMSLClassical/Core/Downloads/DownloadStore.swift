import Foundation
import Observation

/// ダウンロード（オフライン再生。SPEC §5.1）。ファイルは `directory/{track.id}.mp3`。
/// 1 曲ずつ順番に取得し（音源のサーバーに負担をかけない）、失敗した曲は未ダウンロードに戻す。
@MainActor
@Observable
final class DownloadStore {
    static let wifiOnlyKey = "downloadWifiOnly"

    /// ダウンロード済みの曲とファイルの大きさ（バイト）。
    private(set) var downloaded: [String: Int64] = [:]
    /// 取得を待っている曲（順番）。
    private(set) var queued: [String] = []
    /// 取得中の曲と進み具合（0〜1）。
    private(set) var active: String?
    private(set) var progress: Double = 0
    /// 直前に失敗した曲（画面に知らせる）。
    private(set) var lastFailure: String?

    var wifiOnly: Bool {
        didSet { defaults.set(wifiOnly, forKey: Self.wifiOnlyKey) }
    }

    /// 曲の取得が終わったとき（再生側が URL を読み直すため）。
    @ObservationIgnored var onChange: ((String) -> Void)?

    @ObservationIgnored let directory: URL
    @ObservationIgnored private let downloader: any Downloader
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private var urls: [String: URL] = [:]
    @ObservationIgnored private var worker: Task<Void, Never>?

    init(directory: URL, downloader: any Downloader, defaults: UserDefaults = .standard) {
        self.directory = directory
        self.downloader = downloader
        self.defaults = defaults
        wifiOnly = defaults.object(forKey: Self.wifiOnlyKey) as? Bool ?? true
        scan()
    }

    var totalBytes: Int64 { downloaded.values.reduce(0, +) }

    func isDownloaded(_ trackId: String) -> Bool { downloaded[trackId] != nil }

    func isPending(_ trackId: String) -> Bool { active == trackId || queued.contains(trackId) }

    /// ダウンロード済みならファイルの URL。
    func localURL(for trackId: String) -> URL? {
        guard downloaded[trackId] != nil else { return nil }
        return fileURL(trackId)
    }

    /// 取得を待つ列に入れる（済み・待ち・取得中の曲は飛ばす）。
    func enqueue(_ tracks: [Track]) {
        for track in tracks where !isDownloaded(track.id) && !isPending(track.id) {
            guard let url = URL(string: track.audioUrl), CatalogValidator.isAllowedAudioURL(track.audioUrl) else { continue }
            urls[track.id] = url
            queued.append(track.id)
        }
        lastFailure = nil
        startWorker()
    }

    /// 待ち・取得中なら取り消し、済みならファイルを消す。
    func remove(_ trackId: String) {
        queued.removeAll { $0 == trackId }
        if active == trackId { worker?.cancel() }
        deleteFile(trackId)
    }

    func removeAll() {
        queued.removeAll()
        worker?.cancel()
        for id in downloaded.keys { deleteFile(id) }
    }

    /// カタログを更新したとき: 新しい録音に置き換わった曲とカタログから消えた曲のファイルを消す（SPEC §5.1）。
    func prune(keeping isInCatalog: (String) -> Bool) {
        for id in downloaded.keys where !isInCatalog(id) { deleteFile(id) }
        queued.removeAll { !isInCatalog($0) }
    }

    /// 取得し終わるのを待つ（テスト用）。
    func waitUntilIdle() async {
        while let worker {
            await worker.value
            if self.worker == worker { break }
        }
    }

    // MARK: - 内部

    private func fileURL(_ trackId: String) -> URL {
        directory.appendingPathComponent(trackId).appendingPathExtension("mp3")
    }

    private func scan() {
        let files = (try? FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: [.fileSizeKey]
        )) ?? []
        for file in files where file.pathExtension == "mp3" {
            let size = (try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize).flatMap { $0 } ?? 0
            downloaded[file.deletingPathExtension().lastPathComponent] = Int64(size)
        }
    }

    private func deleteFile(_ trackId: String) {
        guard downloaded.removeValue(forKey: trackId) != nil else { return }
        try? FileManager.default.removeItem(at: fileURL(trackId))
        onChange?(trackId)
    }

    private func startWorker() {
        guard worker == nil, !queued.isEmpty else { return }
        worker = Task { [weak self] in
            while let self, let id = self.next() {
                await self.fetch(id)
            }
            self?.worker = nil
            self?.startWorker()
        }
    }

    private func next() -> String? {
        guard !queued.isEmpty else {
            active = nil
            return nil
        }
        let id = queued.removeFirst()
        active = id
        progress = 0
        return id
    }

    private func fetch(_ trackId: String) async {
        defer {
            active = nil
            progress = 0
        }
        guard let url = urls[trackId] else { return }
        do {
            let temp = try await downloader.download(from: url, wifiOnly: wifiOnly) { [weak self] value in
                Task { @MainActor in
                    if self?.active == trackId { self?.progress = value }
                }
            }
            try Task.checkCancellation()
            try place(temp, as: trackId)
            onChange?(trackId)
        } catch is CancellationError {
            // 取り消し（削除）
        } catch let error as URLError where error.code == .cancelled {
            // 取り消し（削除）
        } catch {
            lastFailure = trackId
        }
    }

    /// 一時ファイルを置き場所へ移す（中身が空なら失敗）。iCloud のバックアップには入れない。
    private func place(_ temp: URL, as trackId: String) throws {
        defer { try? FileManager.default.removeItem(at: temp) }
        let size = (try? temp.resourceValues(forKeys: [.fileSizeKey]).fileSize).flatMap { $0 } ?? 0
        guard size > 0 else { throw DownloadError.emptyFile }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var folder = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? folder.setResourceValues(values)
        let target = fileURL(trackId)
        try? FileManager.default.removeItem(at: target)
        try FileManager.default.moveItem(at: temp, to: target)
        downloaded[trackId] = Int64(size)
    }
}
