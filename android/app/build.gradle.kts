plugins {
    id("cmsl.android.application")
    id("cmsl.android.compose")
    id("cmsl.serialization")
}

android {
    namespace = "app.cmsl.classical"

    defaultConfig {
        applicationId = "app.cmsl.classical"
        versionCode = 1
        versionName = "0.1.0"
        // 配信先（SPEC §2）。アプリがアクセスするのはここと音源の mp3 だけ（classical-sound.up.seesaa.net と、
        // http の旧サーバー 3 つ。res/xml/network_security_config.xml）
        buildConfigField("String", "CATALOG_BASE_URL", "\"https://kate941-su.github.io/classicplayer/v1/\"")
    }

    buildTypes {
        release {
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
        }
    }
}

/**
 * 同梱カタログ（シード。SPEC §2・§6）を assets/catalog.json に入れる。
 * 既定は共有フィクスチャ。`-Pcmsl.seedCatalog=<path>`（android/ からの相対パスか絶対パス）で差し替える。
 * リリース前に公開中の実カタログへ切り替える（tool/update-seed.sh）。
 */
abstract class PrepareSeedCatalog : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        source.get().asFile.copyTo(out.resolve("catalog.json"))
    }
}

val seedCatalog: Provider<String> = providers.gradleProperty("cmsl.seedCatalog")
    .orElse("seed/catalog.json")
    .map { path -> rootProject.file(path).takeIf { it.exists() }?.path ?: rootProject.file("../fixtures/catalog.sample.json").path }

val prepareSeedCatalog = tasks.register<PrepareSeedCatalog>("prepareSeedCatalog") {
    source.set(layout.file(seedCatalog.map { File(it) }))
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
