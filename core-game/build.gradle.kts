plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":playback-api"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

