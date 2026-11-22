package jp.vntech.classic.core.model

import kotlinx.serialization.json.Json

/** アプリが読めるカタログの schemaVersion。 */
const val SUPPORTED_SCHEMA_VERSION = 2

object CatalogJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun decodeCatalog(text: String): Catalog = json.decodeFromString(Catalog.serializer(), text)

    fun decodeManifest(text: String): Manifest = json.decodeFromString(Manifest.serializer(), text)
}
