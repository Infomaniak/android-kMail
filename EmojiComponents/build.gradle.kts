import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    alias(core.plugins.kotlin.android)
    alias(core.plugins.compose.compiler)
}

val appCompileSdk: Int by rootProject.extra
val appMinSdk: Int by rootProject.extra
val javaVersion: JavaVersion by rootProject.extra

android {
    namespace = "com.infomaniak.emojicomponents"
    compileSdk = appCompileSdk

    defaultConfig {
        minSdk = appMinSdk
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    buildFeatures {
        compose = true
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("standard") {}
        create("fdroid") {}
    }
}

kotlin.compilerOptions.jvmTarget = JvmTarget.valueOf("JVM_${javaVersion.name.substringAfter("VERSION_")}")

dependencies {
    implementation(core.infomaniak.core.avatar)
    implementation(core.infomaniak.core.ui.compose.margin)
    implementation(core.infomaniak.core.ui.compose.materialthemefromxml)

    implementation(libs.compose.ui.android)

    implementation(core.androidx.core.ktx)
    api(libs.emoji.reaction.models)

    implementation(platform(core.compose.bom))
    implementation(core.compose.runtime)
    implementation(core.compose.material3)
    implementation(core.compose.ui)
    debugImplementation(core.compose.ui.tooling)
    implementation(core.compose.ui.tooling.preview)
}
