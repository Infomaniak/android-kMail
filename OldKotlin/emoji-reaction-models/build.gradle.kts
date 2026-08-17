import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Don't change the order in this `plugins` block, it will mess things up.
 */
plugins {
    alias(oldKotlinCatalog.plugins.android.library)
    alias(oldKotlinCatalog.plugins.kotlin.android)
    alias(oldKotlinCatalog.plugins.oldkotlin.publishing)
}

val javaVersion: JavaVersion = JavaVersion.VERSION_17

android {
    namespace = "com.infomaniak.mail.emoji.models"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
}

kotlin.compilerOptions.jvmTarget = JvmTarget.valueOf("JVM_${javaVersion.name.substringAfter("VERSION_")}")
