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
        targetSdk = appCompileSdk
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlinOptions {
        jvmTarget = javaVersion.toString()
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard")
        create("fdroid")
    }
}

dependencies {
    api(libs.jsoup)
}
