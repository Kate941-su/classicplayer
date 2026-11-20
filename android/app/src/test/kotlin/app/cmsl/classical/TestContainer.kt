package app.cmsl.classical

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import app.cmsl.classical.core.artwork.ArtworkProvider
import app.cmsl.classical.core.data.CatalogRepository
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
fun testContainer(context: Context, dir: File, player: FakePlayerController, english: Boolean = true): AppContainer {
    val fixtures = File(requireNotNull(System.getProperty("fixturesDir")))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
        File(dir, "test.preferences_pb")
    }
    val container = AppContainer(
        catalogRepository = CatalogRepository(
            seed = { File(fixtures, "catalog.sample.json").inputStream() },
            dir = dir,
            client = OkHttpClient(),
            manifestUrl = "https://example.invalid/v1/manifest.json".toHttpUrl(),
        ),
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build(),
        recentSearches = RecentSearchStore(prefs),
        queueStore = QueueStore(prefs),
        artwork = ArtworkProvider(),
        scope = scope,
        playerFactory = { player },
        english = { english },
    )
    runBlocking { container.catalogRepository.load() }
    return container
}
