pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
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

rootProject.name = "Infomaniak Mail"
include(
    ":app",
    ":Core:AppIntegrity",
    ":Core:Auth",
    ":Core:Avatar",
    ":Core:Coil",
    ":Core:Compose:BasicButton",
    ":Core:Compose:Basics",
    ":Core:Compose:Margin",
    ":Core:Compose:MaterialThemeFromXml",
    ":Core:CrossAppLogin:Back",
    ":Core:CrossAppLogin:Front",
    ":Core:DotLottie",
    ":Core:FragmentNavigation",
    ":Core:KSuite",
    ":Core:KSuite:KSuitePro",
    ":Core:KSuite:MyKSuite",
    ":Core:Legacy",
    ":Core:Legacy:AppLock",
    ":Core:Legacy:BugTracker",
    ":Core:Legacy:Confetti",
    ":Core:Legacy:Stores",
    ":Core:Matomo",
    ":Core:Network",
    ":Core:Network:Models",
    ":Core:Onboarding",
    ":Core:Sentry",
    ":EmojiComponents",
    ":HtmlCleaner",
)
