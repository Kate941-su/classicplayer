import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Android に依存しない純 Kotlin のモジュール（model / search / data）
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("cmsl.quality")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

// 共有フィクスチャ（リポジトリ直下の fixtures/）をコピーせずに参照する
tasks.withType<Test>().configureEach {
    systemProperty("fixturesDir", rootProject.file("../fixtures").absolutePath)
}
