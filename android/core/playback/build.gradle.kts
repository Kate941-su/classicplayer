plugins {
    id("cmsl.android.library")
}

android {
    namespace = "jp.vntech.classic.core.playback"
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
