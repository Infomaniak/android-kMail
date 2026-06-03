import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Don't change the order in this `plugins` block, it will mess things up.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.realm.kotlin)
    alias(libs.plugins.kotlin.parcelize)
}

group = "com.infomaniak.mail"
version = "1.0.0"

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

    api(libs.realm.kotlin.base)
    api(libs.kotlinx.serialization.json)
    api(libs.androidx.annotation)
    implementation(libs.sentry.android)
    api(project(":emoji-reaction-models"))
}
