plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.hitster.platform.web.LocalWebServerKt")
}

val gdxWebCompatVersion = "1.13.5"

dependencies {
    implementation(project(":core-model"))
    implementation(project(":networking"))
    implementation(project(":playback-api"))
    implementation(project(":transport-jvm"))
    implementation(project(":ui"))
    implementation("com.badlogicgames.gdx:gdx:$gdxWebCompatVersion")
    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxWebCompatVersion")
    implementation(libs.gdx.teavm.backend)
    implementation(libs.gdx.teavm.freetype)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
}

val buildWebDist = tasks.register<JavaExec>("buildWebDist") {
    dependsOn("classes")
    group = "web"
    description = "Build the libGDX TeaVM web guest client."
    mainClass.set("com.hitster.platform.web.BuildHitsterWeb")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.named<JavaExec>("run") {
    dependsOn(buildWebDist)
}
