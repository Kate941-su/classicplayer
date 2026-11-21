package app.cmsl.classical

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import app.cmsl.classical.core.artwork.ArtworkProvider
import app.cmsl.classical.core.data.AudioFetcher
import app.cmsl.classical.core.data.CatalogRepository
import app.cmsl.classical.core.data.DownloadRepository
import app.cmsl.classical.core.data.PortraitRepository
import app.cmsl.classical.core.db.DownloadSettingsStore
import app.cmsl.classical.core.db.LibraryDatabase
import app.cmsl.classical.core.db.QueueStore
import app.cmsl.classical.core.db.RecentSearchStore
import app.cmsl.classical.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File

/** 共有フィクスチャをシードにした、通信しないコンテナ。 */
fun testContainer(
    context: Context,
    dir: File,
    player: FakePlayerController,
    english: Boolean = true,
    /** 同梱の肖像画を置く場所（`portraits/{id}.jpg`）。null ならリポジトリの site/v1/。 */
    portraitsDir: File? = null,
    /** シードのカタログ。null なら共有フィクスチャ。 */
    seed: File? = null,
    /** ダウンロードの取得（既定は通信せず、曲の ID を書いたファイルを作る）。 */
    fetcher: AudioFetcher = AudioFetcher { url, target, _ -> target.writeText("mp3:$url") },
): AppContainer {
    val fixtures = File(requireNotNull(System.getProperty("fixturesDir")))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
        File(dir, "test.preferences_pb")
    }
    val container = AppContainer(
        catalogRepository = CatalogRepository(
            seed = { (seed ?: File(fixtures, "catalog.sample.json")).inputStream() },
            dir = dir,
            client = OkHttpClient(),
            manifestUrl = "https://example.invalid/v1/manifest.json".toHttpUrl(),
        ),
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build(),
        recentSearches = RecentSearchStore(prefs),
        queueStore = QueueStore(prefs),
        artwork = ArtworkProvider(),
        // 同梱の肖像画の代わりに、リポジトリの site/v1/portraits/ を読む（配信元は通信しない宛先）
        portraitRepository = PortraitRepository(
            bundled = { path ->
                File(
                    portraitsDir ?: File(fixtures, "../site/v1"),
                    path
                ).takeIf { it.exists() }?.inputStream()
            },
            cacheDir = File(dir, "portraits"),
            client = OkHttpClient(),
            baseUrl = "https://example.invalid/v1/catalog.json".toHttpUrl(),
        ),
        // 取得はその場で終わらせる（テストから待たずに結果を見られるように）
        downloads = DownloadRepository(
            dir = File(dir, "downloads"),
            fetcher = fetcher,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        ),
        downloadSettings = DownloadSettingsStore(prefs),
        scope = scope,
        playerFactory = { player },
        english = { english },
    )
    runBlocking { container.catalogRepository.load() }
    return container
}
