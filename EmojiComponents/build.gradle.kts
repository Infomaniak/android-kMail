import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

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

    kotlinOptions {
        jvmTarget = javaVersion.toString()
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("standard") {}
        create("fdroid") {}
    }
}

ktlint {
    version.set("1.7.1")
    android.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(ReporterType.PLAIN)
    }
    baseline.set(file("${projectDir}/config/ktlint/baseline.xml"))
}

dependencies {
    implementation(project(":Core:Avatar"))
    implementation(project(":Core:Compose:Margin"))
    implementation(project(":Core:Compose:MaterialThemeFromXml"))

    implementation(libs.compose.ui.android)

    implementation(core.androidx.core.ktx)

    implementation(platform(core.compose.bom))
    implementation(core.compose.runtime)
    implementation(core.compose.material3)
    implementation(core.compose.ui)
    debugImplementation(core.compose.ui.tooling)
    implementation(core.compose.ui.tooling.preview)
}
