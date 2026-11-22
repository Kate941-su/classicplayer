plugins {
    id("cmsl.android.library")
}

android {
    namespace = "jp.vntech.classic.core.artwork"
}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit4)
}
