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
        maven { url 'https://jitpack.io' }
    }
    versionCatalogs {
        create("core") { from(files("Core/gradle/core.versions.toml")) }
    }
}

rootProject.name = 'Infomaniak Mail'
include ':app',
        ':Core:AppIntegrity',
        ':Core:Auth',
        ':Core:Avatar',
        ':Core:Coil',
        ':Core:Compose:Basics',
        ':Core:Compose:Margin',
        ':Core:Compose:MaterialThemeFromXml',
        ':Core:CrossAppLogin:Back',
        ':Core:CrossAppLogin:Front',
        ':Core:FragmentNavigation',
        ':Core:Legacy',
        ':Core:Legacy:AppLock',
        ':Core:Legacy:BugTracker',
        ':Core:Legacy:Confetti',
        ':Core:Legacy:Stores',
        ':Core:Matomo',
        ':Core:kSuite:kSuitePro',
        ':Core:kSuite:MyKSuite',
        ':Core:Network',
        ':Core:Network:Models',
        ':Core:Sentry',
        ':EmojiComponents',
        ':HtmlCleaner'
