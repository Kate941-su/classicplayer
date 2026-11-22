import java.security.MessageDigest
import java.util.Properties

plugins {
    id("cmsl.android.application")
    id("cmsl.android.compose")
    id("cmsl.serialization")
}

android {
    namespace = "jp.vntech.classic"

    defaultConfig {
        applicationId = "jp.vntech.classic"
        versionCode = 1
        versionName = "0.1.0"
        // 配信先（SPEC §2）。アプリがアクセスするのはここと音源の mp3 だけ（classical-sound.up.seesaa.net と、
        // http の旧サーバー 3 つ。res/xml/network_security_config.xml）
        buildConfigField("String", "CATALOG_BASE_URL", "\"https://kate941-su.github.io/classicplayer/v1/\"")
    }

    // Google Play のアップロード鍵。鍵と properties はリポジトリに入れない（既定は ~/keys/google/。
    // -Pcmsl.keystoreProperties=<path> で変えられる）。無いときは署名なしの release を作る（CI など）
    val keystoreProperties = providers.gradleProperty("cmsl.keystoreProperties")
        .orElse("${System.getProperty("user.home")}/keys/google/classicplayer-keystore.properties")
        .map { file(it) }.get()
    val uploadKey = if (keystoreProperties.isFile) {
        Properties().apply { keystoreProperties.inputStream().use { load(it) } }
    } else {
        null
    }
    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        checkDependencies = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // ./gradlew :app:testDebugUnitTest -Pcmsl.recordScreenshots で docs/screens/android/ に保存する
        unitTests.all {
            if (providers.gradleProperty("cmsl.recordScreenshots").isPresent) {
                it.systemProperty("screenshotsDir", rootProject.file("../docs/screens/android").absolutePath)
                it.outputs.upToDateWhen { false }
            }
            // ストアの掲載用の素の画面（公開中のカタログ）: -Pcmsl.storeScreenshots → ../build/store_screenshots/raw-android/
            if (providers.gradleProperty("cmsl.storeScreenshots").isPresent) {
                it.systemProperty("storeScreenshotsDir", rootProject.file("../build/store_screenshots/raw-android").absolutePath)
                it.systemProperty("storeSeedCatalog", rootProject.file("../site/v1/catalog.json").absolutePath)
                it.outputs.upToDateWhen { false }
            }
        }
    }
}

/**
 * 同梱カタログ（シード。SPEC §2・§6）を assets/catalog.json に、シードの作曲家の肖像画（SPEC §4.10）を assets/portraits/ に入れる。
 * 既定は共有フィクスチャ。`-Pcmsl.seedCatalog=<path>`（android/ からの相対パスか絶対パス）で差し替える。
 * リリース前に公開中の実カタログへ切り替える（tool/update-seed.sh。肖像画も seed/portraits/ に置く）。
 * 肖像画は seed/portraits/ → ../site/v1/portraits/ の順に探し、カタログの sha256 と一致するものだけを入れる
 * （ないもの・違うものは、アプリが配信元から取ってキャッシュする）。
 */
abstract class PrepareSeedCatalog : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val source: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val portraitDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val catalogFile = source.get().asFile
        catalogFile.copyTo(out.resolve("catalog.json"))

        @Suppress("UNCHECKED_CAST")
        val composers = (groovy.json.JsonSlurper().parse(catalogFile) as Map<String, Any?>)["composers"] as List<Map<String, Any?>>
        var bundled = 0
        val missing = mutableListOf<String>()
        composers.forEach { c ->
            @Suppress("UNCHECKED_CAST")
            val portrait = c["portrait"] as Map<String, Any?>? ?: return@forEach
            val url = portrait["url"] as String
            val sha = portrait["sha256"] as String
            // url は portraits/{composer.id}.jpg（SPEC §3.6）。ほかの形は入れない
            if (url != "portraits/${c["id"]}.jpg") return@forEach
            val name = url.removePrefix("portraits/")
            val file = portraitDirs.files.map { it.resolve(name) }.firstOrNull { it.isFile && sha256(it) == sha }
            if (file == null) {
                missing += name
            } else {
                file.copyTo(out.resolve(url))
                bundled++
            }
        }
        logger.lifecycle("同梱の肖像画: $bundled 枚" + if (missing.isEmpty()) "" else "（見つからない・sha256 が違う: ${missing.joinToString()}）")
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}

val seedCatalog: Provider<String> = providers.gradleProperty("cmsl.seedCatalog")
    .orElse("seed/catalog.json")
    .map { path -> rootProject.file(path).takeIf { it.exists() }?.path ?: rootProject.file("../fixtures/catalog.sample.json").path }

val prepareSeedCatalog = tasks.register<PrepareSeedCatalog>("prepareSeedCatalog") {
    source.set(layout.file(seedCatalog.map { File(it) }))
    portraitDirs.from(
        listOf(rootProject.file("seed/portraits"), rootProject.file("../site/v1/portraits")).filter { it.isDirectory }
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(prepareSeedCatalog, PrepareSeedCatalog::outputDir)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:search"))
    implementation(project(":core:db"))
    implementation(project(":core:playback"))
    implementation(project(":core:artwork"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
