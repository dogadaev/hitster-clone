import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val webProject = project(":platform-web")
val syncHostedWebAssets = tasks.register<Copy>("syncHostedWebAssets") {
    dependsOn(":platform-web:buildWebDist")
    from(webProject.layout.buildDirectory.dir("dist/webapp"))
    into(layout.buildDirectory.dir("generated/web-assets/webapp"))
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun readLocalOrEnvironment(name: String, envName: String = name): String {
    return localProperties.getProperty(name)
        ?: System.getenv(envName)
        ?: ""
}

fun String.toBuildConfigLiteral(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

val spotifyClientId = readLocalOrEnvironment("spotifyClientId", "SPOTIFY_CLIENT_ID")
val spotifyRedirectUri = readLocalOrEnvironment("spotifyRedirectUri", "SPOTIFY_REDIRECT_URI")
val spotifyRedirect = spotifyRedirectUri.takeIf { it.isNotBlank() }?.let(URI::create)

android {
    namespace = "com.hitster.platform.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hitster.platform.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["redirectSchemeName"] = spotifyRedirect?.scheme ?: "hitsterclone"
        manifestPlaceholders["redirectHostName"] = spotifyRedirect?.host ?: "spotify-auth-callback"
        manifestPlaceholders["redirectPathPattern"] = spotifyRedirect?.path
            ?.takeIf { it.isNotBlank() }
            ?: ".*"
        buildConfigField("String", "SPOTIFY_CLIENT_ID", spotifyClientId.toBuildConfigLiteral())
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", spotifyRedirectUri.toBuildConfigLiteral())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/web-assets"))
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(syncHostedWebAssets)
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":networking"))
    implementation(project(":playback-api"))
    implementation(project(":transport-jvm"))
    implementation(project(":ui"))
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation(libs.gdx.core)
    implementation(libs.gdx.android)
    implementation(libs.gdx.freetype)
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-arm64-v8a")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-armeabi-v7a")
    runtimeOnly("com.badlogicgames.gdx:gdx-freetype-platform:1.13.1:natives-arm64-v8a")
    runtimeOnly("com.badlogicgames.gdx:gdx-freetype-platform:1.13.1:natives-armeabi-v7a")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.gson)
    implementation(libs.jmdns)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.websockets)
    implementation(libs.spotify.auth)
    implementation(libs.zxing.core)
}
