plugins {
    id("cmsl.android.library")
    id("cmsl.serialization")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "jp.vntech.classic.core.db"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    api(libs.room.runtime)
    ksp(libs.room.compiler)
    api(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
