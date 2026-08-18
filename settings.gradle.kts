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
        mavenCentral {
            content { excludeGroup("com.infomaniak.mail.oldkotlin") }
        }
        maven { url = uri("https://jitpack.io") }
        maven {
            name = "infomaniakReposiliteRepositorySnapshots"
            url = uri("https://maven.infomaniak.app/snapshots")
            content { includeGroup("com.infomaniak.mail.oldkotlin") }
        }
        maven {
            name = "infomaniakReposiliteRepository"
            url = uri("https://maven.infomaniak.app/releases")
            content { includeGroup("com.infomaniak.mail.oldkotlin") }
        }
    }
    versionCatalogs {
        create("core") {
            from(files("Core/gradle/core.versions.toml"))
        }
        create("oldKotlinCatalog") {
            from(files("OldKotlin/gradle/libs.versions.toml"))
        }
    }
}

plugins {
    id("com.infomaniak.core.composite")
}

rootProject.name = "Infomaniak Mail"

// Read local.properties first (git-ignored), then fall back to gradle.properties.
// Set useOldKotlinCompositeBuild=true in local.properties to build the OldKotlin modules
// (realm-models, emoji-reaction-models) from local source instead of consuming the AAR
// artifacts published to Reposilite (com.infomaniak.mail.oldkotlin:*).
val localProperties = java.util.Properties().also { props ->
    val localPropertiesFile = file("local.properties")
    if (localPropertiesFile.exists()) localPropertiesFile.reader().use { props.load(it) }
}
val useOldKotlinCompositeBuild = (localProperties.getProperty("useOldKotlinCompositeBuild")
    ?: providers.gradleProperty("useOldKotlinCompositeBuild").orNull)
    ?.toBoolean() ?: false
gradle.extra["useOldKotlinCompositeBuild"] = useOldKotlinCompositeBuild

if (useOldKotlinCompositeBuild) {
    includeBuild("OldKotlin") {
        dependencySubstitution {
            substitute(module("com.infomaniak.mail.oldkotlin:realm-models")).using(project(":realm-models"))
            substitute(module("com.infomaniak.mail.oldkotlin:emoji-reaction-models")).using(project(":emoji-reaction-models"))
        }
    }
}

include(
    ":app",
    ":Core:Legacy",
    ":Core:Legacy:Confetti",
    ":EmojiComponents",
    ":HtmlCleaner",
)
