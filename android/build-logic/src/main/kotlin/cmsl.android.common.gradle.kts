import com.android.build.api.dsl.CommonExtension

// application / library 共通の Android 設定
val android = extensions.getByType(CommonExtension::class.java)

android.apply {
    compileSdk = 37

    defaultConfig.apply {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions.apply {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint.apply {
        lintConfig = rootProject.file("config/lint/lint.xml")
        warningsAsErrors = true
        abortOnError = true
        checkTestSources = false
    }

    testOptions.apply {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.systemProperty("fixturesDir", rootProject.file("../fixtures").absolutePath)
            // Robolectric が JDK 内部の FileDescriptor を使うため
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }
}
