package jp.vntech.classic.core.model

import java.io.File

/** リポジトリ直下の fixtures/（Gradle が system property で渡す）。 */
object Fixtures {
    val dir: File = File(requireNotNull(System.getProperty("fixturesDir")) { "fixturesDir が未設定" })

    fun text(name: String): String = File(dir, name).readText()

    fun sample(): Catalog = CatalogJson.decodeCatalog(text("catalog.sample.json"))
}
