import Foundation

/// 作曲家の肖像画の JPEG を取り出す（SPEC §3.6・§4.10・§6。Android の `PortraitRepository` と同じ規則）。
/// どれも `sha256` が一致したものだけを使う。
///
/// 1. 同梱（シードの作曲家の分。`bundled("portraits/{id}.jpg")`）
/// 2. 端末のキャッシュ（`Caches/portraits/{id}.jpg`。`sha256` が変わったら取り直して上書きする）
/// 3. 配信元（catalog のベース URL からの相対。https で同じホストに限る）。1 枚ずつ取り、失敗したら `retryAfter` は取りに行かない
///
/// 取れなければ nil（呼び出し側は生成アートワークにする）。アクセスするのは配信元（`site/v1/portraits/`）だけ。
actor PortraitStore {
    static let retryAfter: TimeInterval = 10 * 60

    private let bundled: @Sendable (String) -> Data?
    private let cacheDirectory: URL
    private let client: any HTTPClient
    private let baseURL: URL
    private let now: @Sendable () -> Date
    private var failedAt: [String: Date] = [:]
    /// 配信元へは同時に 1 つだけ（同じ画像を重ねて取らない）。
    private var fetching: Task<Data?, Never>?

    init(
        bundled: @escaping @Sendable (String) -> Data?,
        cacheDirectory: URL,
        client: any HTTPClient,
        baseURL: URL,
        now: @escaping @Sendable () -> Date = Date.init
    ) {
        self.bundled = bundled
        self.cacheDirectory = cacheDirectory
        self.client = client
        self.baseURL = baseURL
        self.now = now
    }

    /// `network: false` なら同梱とキャッシュだけを見る（すぐ返す）。
    func load(composerId: String, portrait: Portrait, network: Bool = true) async -> Data? {
        let path = CatalogValidator.portraitPath(composerId)
        guard portrait.url == path else { return nil }
        if let data = bundled(path), CatalogStore.sha256(data) == portrait.sha256 { return data }
        if let data = readCache(composerId, portrait) { return data }
        guard network else { return nil }
        // 前の取得を待ってから（actor は await の間に別の呼び出しを受けるので、Task をつないで 1 つずつにする）
        let previous = fetching
        let task = Task<Data?, Never> {
            _ = await previous?.value
            if let data = readCache(composerId, portrait) { return data }
            return await fetch(composerId, portrait)
        }
        fetching = task
        return await task.value
    }

    private func cacheFile(_ composerId: String) -> URL {
        cacheDirectory.appendingPathComponent("\(composerId).jpg")
    }

    private func readCache(_ composerId: String, _ portrait: Portrait) -> Data? {
        guard let data = try? Data(contentsOf: cacheFile(composerId)),
              CatalogStore.sha256(data) == portrait.sha256 else { return nil }
        return data
    }

    private func fetch(_ composerId: String, _ portrait: Portrait) async -> Data? {
        let key = "\(composerId):\(portrait.sha256)"
        let current = now()
        if let last = failedAt[key], current >= last, current.timeIntervalSince(last) < Self.retryAfter { return nil }
        let data = await download(portrait)
        guard let data, CatalogStore.sha256(data) == portrait.sha256 else {
            failedAt[key] = current
            return nil
        }
        failedAt[key] = nil
        writeCache(composerId, data)
        return data
    }

    private func download(_ portrait: Portrait) async -> Data? {
        guard let url = URL(string: portrait.url, relativeTo: baseURL)?.absoluteURL,
              url.scheme == "https", url.host == baseURL.host else { return nil }
        guard let (data, response) = try? await client.data(for: URLRequest(url: url)),
              (200..<300).contains(response.statusCode) else { return nil }
        return data
    }

    /// 書けなくても表示はできるので、失敗は無視する。
    private func writeCache(_ composerId: String, _ data: Data) {
        try? FileManager.default.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
        try? data.write(to: cacheFile(composerId), options: .atomic)
    }
}
