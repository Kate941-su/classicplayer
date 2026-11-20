// detekt と ktlint の標準ルール（detekt-formatting）。`./gradlew detekt` で全モジュールを検査する
plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = providers.gradleProperty("detektAutoCorrect").isPresent
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "detektPlugins"(catalog.findLibrary("detekt-formatting").get())
}

// detekt 1.23.x は自身がビルドされた Kotlin で動かす必要がある
configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
    }
}
