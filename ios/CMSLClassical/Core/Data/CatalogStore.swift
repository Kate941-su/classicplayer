import CryptoKit
import Foundation

/// HTTP の取得（テストで差し替える）。
protocol HTTPClient: Sendable {
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse)
}

struct URLSessionHTTPClient: HTTPClient {
    let session: URLSession

    /// ETag は自前で扱うので、URLSession のキャッシュは使わない（304 をそのまま受け取るため）。
    static func makeDefault() -> URLSessionHTTPClient {
        let config = URLSessionConfiguration.ephemeral
        config.urlCache = nil
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 60
        return URLSessionHTTPClient(session: URLSession(configuration: config))
    }

    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        return (data, http)
    }
}

enum CatalogSource: Sendable, Equatable { case seed, downloaded }

struct CatalogState: Sendable {
    let index: CatalogIndex
    let source: CatalogSource
    /// シードのときは nil。
    let catalogVersion: String?
    let lastCheckedAt: Date?
}

enum RefreshResult: Sendable, Equatable {
    /// 前回の確認から 24 時間以内。
    case skipped
    /// manifest が 304。
    case notModified
    /// manifest は取れたが catalogVersion が同じ。
    case upToDate
    /// manifest の schemaVersion がアプリの対応範囲外。手持ちのカタログを使い続ける。
    case unsupported
    case updated(catalogVersion: String)
    case failed(reason: String)
}

/// `catalog.meta.json`。ローカルのカタログの版と、manifest の確認の記録。
struct CatalogMeta: Codable, Equatable, Sendable {
    var catalogVersion: String?
    var manifestEtag: String?
    var lastCheckedAt: Date?
}

/// カタログの取得と更新（SPEC §7。Android の `CatalogRepository` と同じ仕様）。
///
/// 1. `load()`: ローカル（`directory/catalog.json`）を読む。なければ・壊れていればシード（同梱）を使う。ネットワークは待たない
/// 2. `refresh()`: manifest を ETag 付きで取得する（前回の確認から 24 時間以内なら何もしない。`force` で無視）
/// 3. `catalogVersion` が変わっていれば catalog を取得し、SHA-256 と内容を検証してから差し替える。失敗したら旧版のまま
actor CatalogStore {
    static let catalogFile = "catalog.json"
    static let metaFile = "catalog.meta.json"
    static let checkInterval: TimeInterval = 24 * 60 * 60

    private let seed: @Sendable () throws -> Data
    private let directory: URL
    private let client: any HTTPClient
    private let manifestURL: URL
    private let now: @Sendable () -> Date

    private(set) var state: CatalogState?
    private var observers: [UUID: AsyncStream<CatalogState>.Continuation] = [:]

    init(
        seed: @escaping @Sendable () throws -> Data,
        directory: URL,
        client: any HTTPClient,
        manifestURL: URL,
        now: @escaping @Sendable () -> Date = Date.init
    ) {
        self.seed = seed
        self.directory = directory
        self.client = client
        self.manifestURL = manifestURL
        self.now = now
    }

    private var catalogURL: URL { directory.appendingPathComponent(Self.catalogFile) }
    private var metaURL: URL { directory.appendingPathComponent(Self.metaFile) }

    /// 状態が変わるたびに流す（購読した時点の状態があれば最初に流す）。
    func updates() -> AsyncStream<CatalogState> {
        let (stream, continuation) = AsyncStream<CatalogState>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let id = UUID()
        observers[id] = continuation
        if let state { continuation.yield(state) }
        continuation.onTermination = { [weak self] _ in
            Task { await self?.removeObserver(id) }
        }
        return stream
    }

    private func removeObserver(_ id: UUID) { observers[id] = nil }

    private func publish(_ new: CatalogState) {
        state = new
        for continuation in observers.values { continuation.yield(new) }
    }

    @discardableResult
    func load() throws -> CatalogState {
        if let state { return state }
        let meta = readMeta()
        let loaded: CatalogState
        if let local = readLocal() {
            loaded = CatalogState(
                index: CatalogIndex(local), source: .downloaded,
                catalogVersion: meta.catalogVersion, lastCheckedAt: meta.lastCheckedAt
            )
        } else {
            // ローカルがなければ版の記録も無効（次の確認で必ず取得する）
            var cleared = meta
            cleared.catalogVersion = nil
            cleared.manifestEtag = nil
            try? writeMeta(cleared)
            loaded = CatalogState(
                index: CatalogIndex(try readSeed()), source: .seed, catalogVersion: nil, lastCheckedAt: meta.lastCheckedAt
            )
        }
        publish(loaded)
        return loaded
    }

    func refresh(force: Bool = false) async -> RefreshResult {
        do {
            try load()
        } catch {
            return .failed(reason: "同梱カタログを読めない: \(error)")
        }
        let meta = readMeta()
        let time = now()
        if !force, isRecent(meta.lastCheckedAt, time) { return .skipped }
        do {
            return try await fetchManifest(meta: meta, time: time)
        } catch let error as DecodingError {
            return .failed(reason: "JSON を読めない: \(error)")
        } catch let error as URLError {
            return .failed(reason: "通信エラー: \(error.localizedDescription)")
        } catch {
            return .failed(reason: "内容が不正: \(error)")
        }
    }

    private func fetchManifest(meta: CatalogMeta, time: Date) async throws -> RefreshResult {
        var request = URLRequest(url: manifestURL)
        if let etag = meta.manifestEtag { request.setValue(etag, forHTTPHeaderField: "If-None-Match") }
        let (body, response) = try await client.data(for: request)
        if response.statusCode == 304 {
            var checked = meta
            checked.lastCheckedAt = time
            try writeMeta(checked)
            updateCheckedAt(time)
            return .notModified
        }
        guard (200..<300).contains(response.statusCode) else { return .failed(reason: "manifest: HTTP \(response.statusCode)") }
        let manifest = try CatalogJSON.decodeManifest(body)
        var checked = meta
        checked.lastCheckedAt = time
        checked.manifestEtag = response.value(forHTTPHeaderField: "ETag")
        if !manifest.isSupported {
            try writeMeta(checked)
            return .unsupported
        }
        if manifest.catalogVersion == meta.catalogVersion, FileManager.default.fileExists(atPath: catalogURL.path) {
            try writeMeta(checked)
            updateCheckedAt(time)
            return .upToDate
        }
        return try await fetchCatalog(manifest: manifest, meta: checked, time: time)
    }

    private func fetchCatalog(manifest: Manifest, meta: CatalogMeta, time: Date) async throws -> RefreshResult {
        guard let url = URL(string: manifest.catalogUrl, relativeTo: manifestURL)?.absoluteURL else {
            return .failed(reason: "catalogUrl を解決できない")
        }
        guard url.scheme == "https", url.host == manifestURL.host else {
            return .failed(reason: "catalogUrl が manifest と別のホスト")
        }
        let (bytes, response) = try await client.data(for: URLRequest(url: url))
        guard (200..<300).contains(response.statusCode) else { return .failed(reason: "catalog: HTTP \(response.statusCode)") }
        guard Self.sha256(bytes) == manifest.sha256 else { return .failed(reason: "SHA-256 が manifest と一致しない") }
        let catalog = try CatalogJSON.decodeCatalog(bytes)
        let errors = CatalogValidator.validate(catalog)
        if let first = errors.first { return .failed(reason: "検証エラー: \(first)（ほか \(errors.count - 1) 件）") }

        try write(bytes, to: catalogURL)
        // 版の記録は差し替えの後（途中で落ちても、次回は取り直すだけで済む）
        var updated = meta
        updated.catalogVersion = manifest.catalogVersion
        try writeMeta(updated)
        publish(CatalogState(
            index: CatalogIndex(catalog), source: .downloaded, catalogVersion: manifest.catalogVersion, lastCheckedAt: time
        ))
        return .updated(catalogVersion: manifest.catalogVersion)
    }

    /// 時計が戻った（last が未来）ときは確認する。
    private func isRecent(_ last: Date?, _ time: Date) -> Bool {
        guard let last, last <= time else { return false }
        return time.timeIntervalSince(last) < Self.checkInterval
    }

    private func updateCheckedAt(_ time: Date) {
        guard let state else { return }
        publish(CatalogState(
            index: state.index, source: state.source, catalogVersion: state.catalogVersion, lastCheckedAt: time
        ))
    }

    private func readSeed() throws -> Catalog {
        let catalog = try CatalogJSON.decodeCatalog(seed())
        let errors = CatalogValidator.validate(catalog)
        guard errors.isEmpty else { throw CatalogStoreError.invalidSeed(errors) }
        return catalog
    }

    /// 壊れていたら消して nil（シードに戻る）。
    private func readLocal() -> Catalog? {
        guard let data = try? Data(contentsOf: catalogURL) else { return nil }
        guard let catalog = try? CatalogJSON.decodeCatalog(data), CatalogValidator.validate(catalog).isEmpty else {
            try? FileManager.default.removeItem(at: catalogURL)
            return nil
        }
        return catalog
    }

    private func readMeta() -> CatalogMeta {
        guard let data = try? Data(contentsOf: metaURL) else { return CatalogMeta() }
        return (try? Self.metaDecoder.decode(CatalogMeta.self, from: data)) ?? CatalogMeta()
    }

    private func writeMeta(_ meta: CatalogMeta) throws {
        try write(Self.metaEncoder.encode(meta), to: metaURL)
    }

    private func write(_ data: Data, to url: URL) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try data.write(to: url, options: .atomic)
    }

    static func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private static var metaEncoder: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        return encoder
    }

    private static var metaDecoder: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .millisecondsSince1970
        return decoder
    }
}

enum CatalogStoreError: Error {
    case invalidSeed([String])
}
