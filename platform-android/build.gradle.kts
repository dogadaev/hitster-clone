plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.hitster.platform.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hitster.platform.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":playback-api"))
    implementation(project(":ui"))
    implementation(libs.gdx.core)
    implementation(libs.gdx.android)
    implementation(libs.gdx.freetype)
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-arm64-v8a")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-armeabi-v7a")
    runtimeOnly("com.badlogicgames.gdx:gdx-freetype-platform:1.13.1:natives-arm64-v8a")
    runtimeOnly("com.badlogicgames.gdx:gdx-freetype-platform:1.13.1:natives-armeabi-v7a")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
}
