buildscript {
    dependencies {
        classpath(libs.navigation.safeargs)
        classpath(libs.google.services)
    }

    extra.apply {
        set("appCompileSdk", 34)
        set("appMinSdk", 25)
        set("javaVersion", JavaVersion.VERSION_17)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.dagger.hilt) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.jetbrains.kotlin.serialization) apply false
    alias(libs.plugins.kapt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.realm.kotlin) apply false
}

tasks.register("clean", Delete::class) { delete(rootProject.layout.buildDirectory) }