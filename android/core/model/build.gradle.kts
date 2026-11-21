plugins {
    id("cmsl.kotlin.library")
    id("cmsl.serialization")
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
}
