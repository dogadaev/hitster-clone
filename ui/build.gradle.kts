plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":animations"))
    implementation(project(":core-game"))
    implementation(project(":core-model"))
    implementation(project(":networking"))
    implementation(project(":playback-api"))
    implementation(project(":playlist-data"))
    implementation(libs.gdx.core)
    implementation(libs.gdx.freetype)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
