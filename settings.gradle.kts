pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    includeBuild("Core/build-logic")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    versionCatalogs {
        create("core") {
            from(files("Core/gradle/core.versions.toml"))
        }
    }
}

plugins {
    id("com.infomaniak.core.composite")
}

rootProject.name = "Infomaniak Mail"
include(
    ":app",
    ":Core:Legacy",
    ":Core:Legacy:AppLock",
    ":Core:Legacy:Confetti",
    ":EmojiComponents",
    ":HtmlCleaner",
)
