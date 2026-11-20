plugins {
    id("cmsl.kotlin.library")
    id("cmsl.serialization")
}

dependencies {
    api(project(":core:model"))

    testImplementation(libs.junit4)
}
