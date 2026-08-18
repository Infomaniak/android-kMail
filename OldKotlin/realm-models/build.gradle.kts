import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Don't change the order in this `plugins` block, it will mess things up.
 */
plugins {
    alias(oldKotlinCatalog.plugins.android.library)
    alias(oldKotlinCatalog.plugins.kotlin.android)
    alias(oldKotlinCatalog.plugins.kotlin.serialization)
    alias(oldKotlinCatalog.plugins.realm.kotlin)
    alias(oldKotlinCatalog.plugins.kotlin.parcelize)
    alias(oldKotlinCatalog.plugins.oldkotlin.publishing)
}

val javaVersion: JavaVersion = JavaVersion.VERSION_17

android {
    namespace = "com.infomaniak.mail.realm"
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

dependencies {
    api(oldKotlinCatalog.realm.kotlin.base)
    api(oldKotlinCatalog.kotlinx.serialization.json)
    api(oldKotlinCatalog.androidx.annotation)
    implementation(oldKotlinCatalog.sentry.android)
    api(project(":emoji-reaction-models"))
}
