pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "hitster-clone"

include(
    ":core-model",
    ":playback-api",
    ":playlist-data",
    ":core-game",
    ":networking",
    ":animations",
    ":ui",
    ":platform-android",
)

