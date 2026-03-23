import java.util.Properties

/**
 * Don't change the order in this `plugins` block, it will mess things up.
 */
plugins {
    alias(core.plugins.android.application)
    alias(core.plugins.dagger.hilt)
    alias(libs.plugins.google.services)
    alias(core.plugins.kotlin.android)
    alias(core.plugins.kotlin.serialization)
    alias(core.plugins.ksp)
    alias(core.plugins.navigation.safeargs)
    alias(libs.plugins.realm.kotlin)
    alias(core.plugins.compose.compiler)
    alias(core.plugins.kotlin.parcelize)
    alias(core.plugins.sentry.plugin)
}

val enableLeakCanary = false

val appCompileSdk: Int by rootProject.extra
val appMinSdk: Int by rootProject.extra
val javaVersion: JavaVersion by rootProject.extra

android {
    namespace = "com.infomaniak.mail"
    compileSdk = appCompileSdk

    defaultConfig {
        applicationId = "com.infomaniak.mail"
        minSdk = appMinSdk
        targetSdk = appCompileSdk
        versionCode = 1_24_007_01
        versionName = "1.24.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"

        testOptions {
            @Suppress("UnstableApiUsage")
            managedDevices {
                allDevices {
                    create<com.android.build.api.dsl.ManagedVirtualDevice>("ui-test") {
                        device = "Pixel 9 Pro XL"
                        apiLevel = 36
                        systemImageSource = "google"
                    }
                }

                execution = "ANDROIDX_TEST_ORCHESTRATOR"
            }
        }

        setProperty("archivesBaseName", "infomaniak-mail-$versionName ($versionCode)")

        buildConfigField("String", "CLIENT_ID", "\"E90BC22D-67A8-452C-BE93-28DA33588CA4\"")

        buildConfigField("String", "SHOP_URL", "\"https://ik.me\"")

        buildConfigField("String", "BUGTRACKER_MAIL_BUCKET_ID", "\"app_mail\"")
        buildConfigField("String", "BUGTRACKER_MAIL_PROJECT_NAME", "\"mail\"")
        buildConfigField("String", "GITHUB_REPO", "\"android-mail\"")
        buildConfigField("String", "GITHUB_REPO_URL", "\"https://github.com/Infomaniak/android-kMail\"")

        resValue("string", "ATTACHMENTS_AUTHORITY", "com.infomaniak.mail.attachments")
        resValue("string", "EML_AUTHORITY", "com.infomaniak.mail.eml")
        resValue("string", "FILES_AUTHORITY", "com.infomaniak.mail.attachments;com.infomaniak.mail.eml")

        androidResources {
            localeFilters += listOf("da", "de", "el", "en", "es", "fi", "fr", "it", "nb", "nl", "pl", "pt", "sv")
            generateLocaleConfig = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlinOptions {
        jvmTarget = javaVersion.majorVersion
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    flavorDimensions.add("distribution")

    productFlavors {
        create("standard") {
            dimension = "distribution"
            isDefault = true
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjspecify-annotations=strict")
    }
}

val isRelease = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }

val envProperties = rootProject.file("env.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().also { it.load(file.reader()) } }

val sentryAuthToken = envProperties?.getProperty("sentryAuthToken")
    .takeUnless { it.isNullOrBlank() }
    ?: if (isRelease) error("The `sentryAuthToken` property in `env.properties` must be specified (see `env.example.properties`).") else ""

configurations.configureEach {
    // The Matomo SDK logs network issues to Timber, and the Sentry plugin detects the Timber dependency,
    // and adds its integration, which generates noise.
    // Since we're not using Timber for anything else, it's safe to completely disabled it,
    // as specified in Sentry's documentation: https://docs.sentry.io/platforms/android/integrations/timber/#disable
    exclude(group = "io.sentry", module = "sentry-android-timber")
}

sentry {
    autoInstallation.sentryVersion.set(core.versions.sentry)
    org = "sentry"
    projectName = "mail-android"
    authToken = sentryAuthToken
    url = "https://sentry-mobile.infomaniak.com"
    includeDependenciesReport = false
    includeSourceContext = isRelease

    // Enables or disables the automatic upload of mapping files during a build.
    // If you disable this, you'll need to manually upload the mapping files with sentry-cli when you do a release.
    // Default is enabled.
    autoUploadProguardMapping = true

    // Disables or enables the automatic configuration of Native Symbols for Sentry.
    // This executes sentry-cli automatically so you don't need to do it manually.
    // Default is disabled.
    uploadNativeSymbols = isRelease

    // Does or doesn't include the source code of native code for Sentry.
    // This executes sentry-cli with the --include-sources param. automatically so you don't need to do it manually.
    // Default is disabled.
    includeNativeSources = isRelease
}

dependencies {
    implementation(core.infomaniak.core.appversionchecker)
    implementation(core.infomaniak.core.applock)
    implementation(core.infomaniak.core.auth)
    implementation(core.infomaniak.core.avatar)
    implementation(core.infomaniak.core.bugtracker)
    implementation(core.infomaniak.core.coil)
    implementation(core.infomaniak.core.common)
    implementation(core.infomaniak.core.crossapplogin.front)
    implementation(core.infomaniak.core.dotlottie)
    implementation(core.infomaniak.core.file)
    implementation(core.infomaniak.core.fragmentnavigation)
    implementation(core.infomaniak.core.inappreview)
    implementation(core.infomaniak.core.inappupdate)
    implementation(core.infomaniak.core.ksuite)
    implementation(core.infomaniak.core.ksuite.myksuite)
    implementation(core.infomaniak.core.ksuite.pro)
    implementation(core.infomaniak.core.matomo)
    implementation(core.infomaniak.core.network)
    implementation(core.infomaniak.core.sentry)
    implementation(core.infomaniak.core.twofactorauth.back.withuserdb)
    implementation(core.infomaniak.core.twofactorauth.front)
    implementation(core.infomaniak.core.ui)
    implementation(core.infomaniak.core.ui.compose.basicbutton)
    implementation(core.infomaniak.core.ui.compose.basics)
    implementation(core.infomaniak.core.ui.compose.bottomstickybuttonscaffolds)
    implementation(core.infomaniak.core.ui.compose.margin)
    implementation(core.infomaniak.core.ui.compose.materialthemefromxml)
    implementation(core.infomaniak.core.ui.compose.preview)
    implementation(core.infomaniak.core.ui.view)

    implementation(project(":Core:Legacy"))
    implementation(project(":Core:Legacy:Confetti"))
    implementation(project(":EmojiComponents"))
    implementation(project(":HtmlCleaner"))

    implementation(libs.rich.html.editor)

    implementation(libs.realm.kotlin.base)
    implementation(libs.junit.ktx)

    "standardImplementation"(core.infomaniak.core.notifications.registration)
    "standardImplementation"(libs.play.services.base)
    "standardImplementation"(libs.firebase.messaging.ktx)

    implementation(core.androidx.datastore.preferences)

    implementation(core.lottie)
    implementation(libs.dragdropswipe.recyclerview)
    implementation(libs.dotsindicator)
    implementation(libs.emoji2.emojipicker)

    implementation(libs.flexbox)
    implementation(libs.lifecycle.process)
    implementation(libs.webkit)
    implementation(core.androidx.concurrent.futures.ktx)
    implementation(core.androidx.work.runtime)

    implementation(core.hilt.android)
    implementation(core.hilt.work)
    ksp(core.hilt.compiler)
    ksp(core.hilt.androidx.compiler)

    implementation(core.sentry.android.fragment)

    // Coil SVG support (auto-injected)
    implementation(core.coil.svg)

    coreLibraryDesugaring(libs.desugar.jdk)

    // Compose
    implementation(platform(core.compose.bom))
    implementation(libs.compose.ui.android)
    implementation(core.activity.compose)
    implementation(core.compose.runtime)
    implementation(core.compose.material3)
    implementation(core.compose.ui.tooling.preview)

    // Test
    testImplementation(core.junit)
    testImplementation(core.mockk.agent)
    testImplementation(core.mockk.android)
    testImplementation(core.kotlinx.coroutines.test)

    androidTestImplementation(core.androidx.junit)
    androidTestImplementation(core.androidx.runner)
    androidTestImplementation(core.androidx.test.core)
    androidTestImplementation(core.androidx.test.core.ktx)
    androidTestImplementation(core.junit)
    androidTestImplementation(core.stdlib)
    androidTestImplementation(libs.espresso.contrib)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.web)
    androidTestImplementation(libs.hamcrest)
    androidTestImplementation(libs.junit.ktx)
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.uiautomator)

    androidTestUtil(libs.orchestrator)

    debugImplementation(libs.fragment.testing)

    // Debug
    if (enableLeakCanary) debugImplementation(libs.leakcanary.android)
}
