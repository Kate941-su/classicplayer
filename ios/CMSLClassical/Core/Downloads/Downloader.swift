import Foundation

/// 1 曲分の取得（テストで差し替える）。完了したら一時ファイルの場所を返す（呼び出し側が移す）。
protocol Downloader: Sendable {
    func download(
        from url: URL, wifiOnly: Bool, progress: @escaping @Sendable (Double) -> Void
    ) async throws -> URL
}

enum DownloadError: Error, Equatable {
    case httpStatus(Int)
    case emptyFile
}

/// URLSession のダウンロードタスクで取る。Wi-Fi のときだけの設定では、モバイル通信・省データモードを使わずに待つ。
final class URLSessionDownloader: NSObject, Downloader, URLSessionDownloadDelegate, @unchecked Sendable {
    private struct Pending {
        let continuation: CheckedContinuation<URL, any Error>
        let progress: @Sendable (Double) -> Void
    }

    private let lock = NSLock()
    private var pending: [Int: Pending] = [:]
    private var sessions: [Bool: URLSession] = [:]

    private func session(wifiOnly: Bool) -> URLSession {
        lock.withLock {
            if let session = sessions[wifiOnly] { return session }
            let config = URLSessionConfiguration.default
            config.allowsExpensiveNetworkAccess = !wifiOnly
            config.allowsConstrainedNetworkAccess = !wifiOnly
            // 通信できるようになるまで待つ（Wi-Fi のときだけの設定でモバイル通信のときなど）
            config.waitsForConnectivity = true
            config.timeoutIntervalForResource = 60 * 60
            config.urlCache = nil
            let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
            sessions[wifiOnly] = session
            return session
        }
    }

    func download(
        from url: URL, wifiOnly: Bool, progress: @escaping @Sendable (Double) -> Void
    ) async throws -> URL {
        let task = session(wifiOnly: wifiOnly).downloadTask(with: url)
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                lock.withLock { pending[task.taskIdentifier] = Pending(continuation: continuation, progress: progress) }
                task.resume()
            }
        } onCancel: {
            task.cancel()
        }
    }

    private func take(_ task: URLSessionTask) -> Pending? {
        lock.withLock { pending.removeValue(forKey: task.taskIdentifier) }
    }

    func urlSession(
        _ session: URLSession, downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64
    ) {
        guard totalBytesExpectedToWrite > 0 else { return }
        let item = lock.withLock { pending[downloadTask.taskIdentifier] }
        item?.progress(Double(totalBytesWritten) / Double(totalBytesExpectedToWrite))
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        guard let item = take(downloadTask) else { return }
        let status = (downloadTask.response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            item.continuation.resume(throwing: DownloadError.httpStatus(status))
            return
        }
        // location はこのメソッドを抜けると消えるので、すぐに一時フォルダへ移す
        let kept = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".mp3")
        do {
            try FileManager.default.moveItem(at: location, to: kept)
            item.continuation.resume(returning: kept)
        } catch {
            item.continuation.resume(throwing: error)
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: (any Error)?) {
        guard let error, let item = take(task) else { return }
        item.continuation.resume(throwing: error)
    }
}
