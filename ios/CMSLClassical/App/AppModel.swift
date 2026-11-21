import Foundation
import Observation
import SwiftData

/// アプリ全体で 1 つ（Android の `AppContainer` に当たる）。カタログ・検索の索引・お気に入りと履歴・再生をまとめる。
@MainActor
@Observable
final class AppModel {
    static let catalogBaseURL = URL(string: "https://kate941-su.github.io/classicplayer/v1/")!
    static let siteBaseURL = URL(string: "https://kate941-su.github.io/classicplayer/")!

    enum RefreshStatus: Equatable { case idle, checking, updated, upToDate, failed, unsupported }

    private(set) var catalog: CatalogState?
    private(set) var searchIndex: SearchIndex?
    private(set) var refreshStatus: RefreshStatus = .idle
    /// 英語 UI では `nameEn` / `titleEn` を優先する（SPEC §4.11）。
    let english: Bool

    let library: LibraryStore
    let recentSearches: RecentSearchStore
    @ObservationIgnored let playback: PlaybackController
    @ObservationIgnored private let store: CatalogStore
    @ObservationIgnored private let queueStore: QueueStore
    @ObservationIgnored private var systemPlayback: SystemPlayback?
    @ObservationIgnored private let refreshOnStart: Bool
    @ObservationIgnored private var started = false
    @ObservationIgnored private let environmentBridge = EnvironmentBridge()

    init(
        store: CatalogStore,
        library: LibraryStore,
        recentSearches: RecentSearchStore,
        queueStore: QueueStore,
        engine: any AudioEngine,
        english: Bool,
        refreshOnStart: Bool = true
    ) {
        self.store = store
        self.library = library
        self.recentSearches = recentSearches
        self.queueStore = queueStore
        self.english = english
        self.refreshOnStart = refreshOnStart
        playback = PlaybackController(engine: engine, environment: environmentBridge)
        environmentBridge.model = self
    }

    /// 本番の部品で作る。`uiTesting` では通信せず、保存先をまっさらにする（UI テスト用）。
    static func live(uiTesting: Bool = false) -> AppModel {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let seed: @Sendable () throws -> Data = {
            guard let url = Bundle.main.url(forResource: "catalog", withExtension: "json") else {
                throw CocoaError(.fileNoSuchFile)
            }
            return try Data(contentsOf: url)
        }
        let suite = uiTesting ? "ui-testing" : nil
        if let suite { UserDefaults().removePersistentDomain(forName: suite) }
        let defaults = suite.flatMap(UserDefaults.init(suiteName:)) ?? .standard
        let container = (try? LibraryStore.makeContainer(inMemory: uiTesting))
            ?? (try? LibraryStore.makeContainer(inMemory: true))
        guard let container else { fatalError("SwiftData のストアを作れない") }
        let directory = uiTesting
            ? FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            : support
        return AppModel(
            store: CatalogStore(
                seed: seed, directory: directory, client: URLSessionHTTPClient.makeDefault(),
                manifestURL: catalogBaseURL.appendingPathComponent("manifest.json")
            ),
            library: LibraryStore(container: container),
            recentSearches: RecentSearchStore(defaults: defaults),
            queueStore: QueueStore(suiteName: suite),
            engine: AVQueueAudioEngine(),
            english: Bundle.main.preferredLocalizations.first?.hasPrefix("ja") != true,
            refreshOnStart: !uiTesting
        )
    }

    var index: CatalogIndex? { catalog?.index }

    /// 起動時: ローカル（またはシード）を読み、裏で更新を確認する（SPEC §7）。
    func start() {
        guard !started else { return }
        started = true
        Task {
            for await state in await store.updates() { apply(state) }
        }
        Task {
            // 画面に出すカタログを先に読む。オーディオセッションの設定などはその後
            _ = try? await store.load()
            let system = SystemPlayback(controller: playback)
            system.start()
            systemPlayback = system
            await playback.restore()
            if refreshOnStart { _ = await store.refresh() }
        }
    }

    /// 情報画面の手動更新（24 時間の制限を無視する）。
    func refreshCatalog() async {
        refreshStatus = .checking
        switch await store.refresh(force: true) {
        case .updated: refreshStatus = .updated
        case .notModified, .upToDate, .skipped: refreshStatus = .upToDate
        case .unsupported: refreshStatus = .unsupported
        case .failed: refreshStatus = .failed
        }
    }

    private func apply(_ state: CatalogState) {
        let changedIndex = catalog?.index !== state.index
        catalog = state
        guard changedIndex else { return }
        // 新しい録音に置き換えた曲のお気に入り・履歴を読み替える（SPEC §6）
        library.replaceIds(state.index.replacements(for: library.allTrackIds))
        let index = state.index
        Task.detached(priority: .userInitiated) {
            let search = SearchIndex(index)
            await MainActor.run { [weak self] in
                if self?.catalog?.index === index { self?.searchIndex = search }
            }
        }
    }

    func waitForIndex() async -> CatalogIndex? {
        if let index { return index }
        return try? await store.load().index
    }

    /// アプリがバックグラウンドに入るとき。
    func saveState() {
        Task { await playback.saveNow() }
    }

    // MARK: - 再生のための情報

    fileprivate func resolve(_ ids: [String]) async -> [PlayableTrack] {
        guard let index = await waitForIndex() else { return [] }
        return ids.compactMap { requested in
            guard let track = index.track(index.currentTrackId(requested)),
                  let url = URL(string: track.audioUrl) else { return nil }
            return PlayableTrack(
                id: track.id,
                requestedId: requested,
                audioURL: url,
                title: track.shortTitle(english: english),
                composer: index.composer(track.composerId)?.displayName(english: english) ?? "",
                workTitle: track.workTitle(english: english, work: index.work(track.workId)),
                composerId: track.composerId,
                genreId: track.genreId
            )
        }
    }

    fileprivate func loadQueue() -> QueueSnapshot? { queueStore.load() }

    fileprivate func saveQueue(_ snapshot: QueueSnapshot) { queueStore.save(snapshot) }

    fileprivate func trackStarted(_ id: String) { library.recordPlayed(id) }
}

/// PlaybackController は環境を weak で持つので、AppModel との循環を作らない橋渡し。
@MainActor
private final class EnvironmentBridge: PlaybackEnvironment {
    weak var model: AppModel?

    func resolve(_ trackIds: [String]) async -> [PlayableTrack] { await model?.resolve(trackIds) ?? [] }

    func loadQueue() async -> QueueSnapshot? { model?.loadQueue() }

    func saveQueue(_ snapshot: QueueSnapshot) async { model?.saveQueue(snapshot) }

    func trackStarted(_ trackId: String) async { model?.trackStarted(trackId) }
}
