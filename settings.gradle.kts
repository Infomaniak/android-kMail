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
    ":Core:AppVersionChecker",
    ":Core:Auth",
    ":Core:Avatar",
    ":Core:Coil",
    ":Core:CrossAppLogin:Back",
    ":Core:CrossAppLogin:Front",
    ":Core:DotLottie",
    ":Core:FragmentNavigation",
    ":Core:InAppReview",
    ":Core:InAppUpdate",
    ":Core:KSuite",
    ":Core:KSuite:KSuitePro",
    ":Core:KSuite:MyKSuite",
    ":Core:Legacy",
    ":Core:Legacy:AppLock",
    ":Core:Legacy:BugTracker",
    ":Core:Legacy:Confetti",
    ":Core:Matomo",
    ":Core:Network",
    ":Core:Network:Ktor",
    ":Core:Network:Models",
    ":Core:Notifications:Registration",
    ":Core:Onboarding",
    ":Core:Sentry",
    ":Core:TwoFactorAuth:Front",
    ":Core:TwoFactorAuth:Back",
    ":Core:TwoFactorAuth:Back:WithUserDb",
    ":Core:Ui",
    ":Core:Ui:Compose:BasicButton",
    ":Core:Ui:Compose:Basics",
    ":Core:Ui:Compose:Margin",
    ":Core:Ui:Compose:BottomStickyButtonScaffolds",
    ":Core:Ui:Compose:MaterialThemeFromXml",
    ":Core:Ui:Compose:Preview",
    ":Core:Ui:View",
    ":Core:WebView",
    ":EmojiComponents",
    ":HtmlCleaner",
)
