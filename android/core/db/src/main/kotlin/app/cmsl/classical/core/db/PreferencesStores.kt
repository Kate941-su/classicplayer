package app.cmsl.classical.core.db

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cmsl.classical.core.model.QueueSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val JSON = Json { ignoreUnknownKeys = true }

private inline fun <T> decodeOrNull(block: () -> T): T? = try {
    block()
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

/** 最近の検索（新しい順、最大 10 件。SPEC §4.6）。 */
class RecentSearchStore(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("recent_searches")
    private val serializer = ListSerializer(String.serializer())

    val recent: Flow<List<String>> = dataStore.data.map { prefs -> decode(prefs[key]) }

    suspend fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        dataStore.edit { prefs ->
            val updated = (listOf(q) + decode(prefs[key]).filterNot { it == q }).take(MAX)
            prefs[key] = JSON.encodeToString(serializer, updated)
        }
    }

    suspend fun remove(query: String) {
        dataStore.edit { prefs -> prefs[key] = JSON.encodeToString(serializer, decode(prefs[key]) - query) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }

    private fun decode(text: String?): List<String> =
        text?.let { decodeOrNull { JSON.decodeFromString(serializer, it) } }.orEmpty()

    companion object {
        const val MAX = 10
    }
}

/** 再生キューと位置（SPEC §5「状態の復元」）。 */
class QueueStore(private val dataStore: DataStore<Preferences>) {
    private val key = stringPreferencesKey("queue")

    suspend fun load(): QueueSnapshot? =
        dataStore.data.first()[key]?.let { decodeOrNull { JSON.decodeFromString(QueueSnapshot.serializer(), it) } }

    suspend fun save(snapshot: QueueSnapshot) {
        dataStore.edit { it[key] = JSON.encodeToString(QueueSnapshot.serializer(), snapshot) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }
}

/** ダウンロードの設定（SPEC §5.1・§6）。「Wi-Fi のときだけ」は既定でオン。 */
class DownloadSettingsStore(private val dataStore: DataStore<Preferences>) {
    private val key = booleanPreferencesKey("download_wifi_only")

    val wifiOnly: Flow<Boolean> = dataStore.data.map { it[key] ?: true }

    suspend fun setWifiOnly(value: Boolean) {
        dataStore.edit { it[key] = value }
    }
}
