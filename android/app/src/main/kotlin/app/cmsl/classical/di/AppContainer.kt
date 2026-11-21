package app.cmsl.classical.di

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.preferencesDataStore
import app.cmsl.classical.BuildConfig
import app.cmsl.classical.artwork.Portraits
import app.cmsl.classical.core.artwork.ArtworkProvider
import app.cmsl.classical.core.data.CatalogRepository
import app.cmsl.classical.core.data.CatalogSource
import app.cmsl.classical.core.data.CatalogState
import app.cmsl.classical.core.data.DownloadRepository
import app.cmsl.classical.core.data.OkHttpAudioFetcher
import app.cmsl.classical.core.data.PortraitRepository
import app.cmsl.classical.core.data.RefreshResult
import app.cmsl.classical.core.db.DownloadSettingsStore
import app.cmsl.classical.core.db.LibraryDatabase
import app.cmsl.classical.core.db.QueueStore
import app.cmsl.classical.core.db.RecentSearchStore
import app.cmsl.classical.core.model.CatalogIndex
import app.cmsl.classical.core.model.QueueSnapshot
import app.cmsl.classical.core.model.displayName
import app.cmsl.classical.core.model.shortTitle
import app.cmsl.classical.core.playback.MediaControllerPlayer
import app.cmsl.classical.core.playback.PlayableTrack
import app.cmsl.classical.core.playback.PlaybackEnvironment
import app.cmsl.classical.core.playback.PlayerController
import app.cmsl.classical.core.search.SearchIndex
import app.cmsl.classical.download.unmeteredNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 同梱の肖像画（シードの作曲家の分。assets/portraits/）。なければ null。 */
private fun openAsset(context: Context, path: String): InputStream? = try {
    context.assets.open(path)
} catch (_: FileNotFoundException) {
    null
}

private val Context.settings by preferencesDataStore(name = "settings")

/**
 * 手動 DI（SPEC §8.2: シンプルさを優先）。アプリ全体で 1 つ。テストでは部品を差し替えて作る。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList") // 手動 DI の部品をまとめて受け取る
class AppContainer(
    val catalogRepository: CatalogRepository,
    val database: LibraryDatabase,
    val recentSearches: RecentSearchStore,
    private val queueStore: QueueStore,
    val artwork: ArtworkProvider,
    portraitRepository: PortraitRepository,
    val downloads: DownloadRepository,
    val downloadSettings: DownloadSettingsStore,
    val scope: CoroutineScope,
    playerFactory: (PlaybackEnvironment) -> PlayerController,
    private val now: () -> Long = System::currentTimeMillis,
    private val english: () -> Boolean = { Locale.getDefault().language != "ja" },
) {
    val catalog: StateFlow<CatalogState?> = catalogRepository.state

    val portraits = Portraits(portraitRepository, catalog)

    /** カタログが差し替わったら作り直す（重いので Default で）。 */
    val searchIndex: StateFlow<SearchIndex?> = catalog.filterNotNull()
        .mapLatest { SearchIndex(it.index) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)

    val favoriteIds: StateFlow<Set<String>> = database.favorites().observeIds()
        .map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val playbackEnvironment: PlaybackEnvironment = object : PlaybackEnvironment {
        override suspend fun resolve(trackIds: List<String>): List<PlayableTrack> {
            val index = catalog.filterNotNull().first().index
            val en = english()
            return withContext(Dispatchers.Default) {
                trackIds.mapNotNull { id ->
                    // 保存したキューの曲が新しい録音に置き換わっていたら、置き換え先を再生する
                    val t = index.track(index.currentTrackId(id)) ?: return@mapNotNull null
                    PlayableTrack(
                        id = t.id,
                        // ダウンロード済みなら端末のファイルを再生する（SPEC §5.1）
                        audioUrl = downloads.file(t.id)?.let { Uri.fromFile(it).toString() } ?: t.audioUrl,
                        title = t.shortTitle(en),
                        composer = index.composer(t.composerId)?.displayName(en).orEmpty(),
                        workTitle = index.work(t.workId)?.title.orEmpty(),
                        genre = index.genre(t.genreId)?.displayName(en).orEmpty(),
                        // 通信は待たない（取得したら artwork() でサービスが差し替える）
                        artworkData = portraits.jpeg(t.composerId, network = false)
                            ?: artwork.png(t.composerId, t.genreId),
                    )
                }
            }
        }

        override suspend fun artwork(trackId: String): ByteArray? {
            val index = catalog.filterNotNull().first().index
            val track = index.track(index.currentTrackId(trackId)) ?: return null
            return portraits.jpeg(track.composerId, network = true)
        }

        override val trackChanges = downloads.changes

        override suspend fun loadQueue(): QueueSnapshot? = queueStore.load()

        override suspend fun saveQueue(snapshot: QueueSnapshot) = queueStore.save(snapshot)

        override suspend fun onTrackStarted(trackId: String) = database.history().record(trackId, now())
    }

    val player: PlayerController = playerFactory(playbackEnvironment)

    fun nowMillis(): Long = now()

    /** 起動時: ローカル（またはシード）を読み、裏で更新を確認する（SPEC §7）。 */
    fun start() {
        scope.launch {
            // カタログが差し替わるたびに、新しい録音に置き換えた曲のお気に入り・履歴を読み替える（SPEC §6）
            catalog.filterNotNull().collect {
                replaceStoredTrackIds(it.index)
                pruneDownloads(it)
            }
        }
        scope.launch {
            catalogRepository.load()
            catalogRepository.refresh()
        }
    }

    suspend fun refreshCatalog(force: Boolean): RefreshResult = catalogRepository.refresh(force)

    /**
     * 置き換わった曲・カタログから消えた曲のダウンロードを消す（SPEC §5.1）。
     * シードのときは消さない（古い同梱カタログで、新しいカタログの曲を消さないため）。
     */
    internal fun pruneDownloads(state: CatalogState) {
        if (state.source != CatalogSource.Downloaded) return
        downloads.prune { state.index.track(it) != null }
    }

    internal suspend fun replaceStoredTrackIds(index: CatalogIndex) {
        if (index.catalog.replacedTracks.isEmpty()) return
        val favorites = database.favorites()
        favorites.replaceIds(index.replacementsFor(favorites.ids()))
        val history = database.history()
        history.replaceIds(index.replacementsFor(history.ids()))
    }

    companion object {
        private const val TIMEOUT_SECONDS = 20L

        fun create(context: Context): AppContainer {
            val app = context.applicationContext
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val client = OkHttpClient.Builder()
                .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val downloadSettings = DownloadSettingsStore(app.settings)
            // 「Wi-Fi のときだけ」がオンなら、従量制でない通信のときだけ取る
            val unmetered = unmeteredNetwork(app).shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)
            val networkAllowed = combine(downloadSettings.wifiOnly, unmetered) { wifiOnly, free -> !wifiOnly || free }
                .distinctUntilChanged()
            return AppContainer(
                catalogRepository = CatalogRepository(
                    seed = { app.assets.open("catalog.json") },
                    dir = app.filesDir,
                    client = client,
                    manifestUrl = (BuildConfig.CATALOG_BASE_URL + "manifest.json").toHttpUrl(),
                ),
                database = LibraryDatabase.create(app),
                recentSearches = RecentSearchStore(app.settings),
                queueStore = QueueStore(app.settings),
                artwork = ArtworkProvider(),
                portraitRepository = PortraitRepository(
                    bundled = { path -> openAsset(app, path) },
                    cacheDir = File(app.cacheDir, "portraits"),
                    client = client,
                    // 肖像画の url は catalog.json と同じ場所（site/v1/）からの相対（SPEC §3.6）
                    baseUrl = (BuildConfig.CATALOG_BASE_URL + "catalog.json").toHttpUrl(),
                ),
                downloads = DownloadRepository(
                    dir = File(app.filesDir, "downloads"),
                    fetcher = OkHttpAudioFetcher(client),
                    scope = scope,
                    networkAllowed = networkAllowed,
                ),
                downloadSettings = downloadSettings,
                scope = scope,
                playerFactory = { env -> MediaControllerPlayer(app, env) },
            )
        }
    }
}
