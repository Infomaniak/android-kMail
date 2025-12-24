import java.util.Properties

/**
 * Don't change the order in this `plugins` block, it will mess things up.
 */
plugins {
    alias(libs.plugins.android.application)
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
        versionCode = 1_23_001_01
        versionName = "1.23.1"
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

        resourceConfigurations += listOf("en", "de", "es", "fr", "it")
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
    implementation(libs.infomaniak.core)
    implementation(libs.infomaniak.core.appversionchecker)
    implementation(libs.infomaniak.core.auth)
    implementation(libs.infomaniak.core.avatar)
    implementation(libs.infomaniak.core.coil)
    implementation(libs.infomaniak.core.crossapplogin.front)
    implementation(libs.infomaniak.core.dotlottie)
    implementation(libs.infomaniak.core.fragmentnavigation)
    implementation(libs.infomaniak.core.inappreview)
    implementation(libs.infomaniak.core.inappupdate)
    implementation(libs.infomaniak.core.ksuite)
    implementation(libs.infomaniak.core.ksuite.ksuitepro)
    implementation(libs.infomaniak.core.ksuite.myksuite)
    implementation(libs.infomaniak.core.matomo)
    implementation(libs.infomaniak.core.network)
    implementation(libs.infomaniak.core.sentry)
    implementation(libs.infomaniak.core.twofactorauth.back.withuserdb)
    implementation(libs.infomaniak.core.twofactorauth.front)
    implementation(libs.infomaniak.core.ui)
    implementation(libs.infomaniak.core.ui.compose.basicbutton)
    implementation(libs.infomaniak.core.ui.compose.basics)
    implementation(libs.infomaniak.core.ui.compose.bottomstickybuttonscaffolds)
    implementation(libs.infomaniak.core.ui.compose.margin)
    implementation(libs.infomaniak.core.ui.compose.materialthemefromxml)
    implementation(libs.infomaniak.core.ui.compose.preview)
    implementation(libs.infomaniak.core.ui.view)

    implementation(project(":Core:Legacy"))
    implementation(project(":Core:Legacy:AppLock"))
    implementation(project(":Core:Legacy:BugTracker"))
    implementation(project(":Core:Legacy:Confetti"))
    implementation(project(":EmojiComponents"))
    implementation(project(":HtmlCleaner"))

    implementation(libs.rich.html.editor)

    implementation(libs.realm.kotlin.base)
    implementation(libs.junit.ktx)

    "standardImplementation"(libs.infomaniak.core.notifications.registration)
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
    testImplementation(libs.mockk.agent)
    testImplementation(libs.mockk.android)
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
