import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("kotlin-android")
}

val appCompileSdk = rootProject.ext["appCompileSdk"] as Int
val appMinSdk = rootProject.ext["appMinSdk"] as Int
val javaVersion = rootProject.ext["javaVersion"] as JavaVersion

android {
    namespace = "com.infomaniak.html.cleaner"
    compileSdk = appCompileSdk

    defaultConfig {
        minSdk = appMinSdk
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard")
        create("fdroid")
    }
}

kotlin.compilerOptions.jvmTarget = JvmTarget.valueOf("JVM_${javaVersion.name.substringAfter("VERSION_")}")

dependencies {
    api(libs.jsoup)
}
