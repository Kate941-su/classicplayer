plugins {
    id("cmsl.android.library")
}

android {
    namespace = "app.cmsl.classical.core.artwork"
}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit4)
}
