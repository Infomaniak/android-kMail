plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.dagger.hilt)
    alias(core.plugins.compose.compiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.realm.kotlin)
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
        versionCode = 1_17_000_01
        versionName = "1.17.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        setProperty("archivesBaseName", "infomaniak-mail-$versionName ($versionCode)")

        buildConfigField("String", "CLIENT_ID", "\"E90BC22D-67A8-452C-BE93-28DA33588CA4\"")
        buildConfigField("String", "CREATE_ACCOUNT_URL", "\"https://welcome.infomaniak.com/signup/ikmail?app=true\"")
        buildConfigField("String", "CREATE_ACCOUNT_SUCCESS_HOST", "\"ksuite.infomaniak.com\"")
        buildConfigField("String", "CREATE_ACCOUNT_CANCEL_HOST", "\"welcome.infomaniak.com\"")
        buildConfigField("String", "IMPORT_EMAILS_URL", "\"https://import-email.infomaniak.com\"")
        buildConfigField("String", "MAIL_API", "\"https://mail.infomaniak.com\"")
        // buildConfigField("String", "MAIL_API", "\"https://mail.preprod.dev.infomaniak.ch\"") // Pre-production

        buildConfigField("String", "SHOP_URL", "\"https://ik.me\"")
        buildConfigField("String", "CHATBOT_URL", "\"https://www.infomaniak.com/chatbot\"")
        buildConfigField("String", "FAQ_URL", "\"https://www.infomaniak.com/fr/support/faq/admin2/service-mail\"")
        buildConfigField("String", "MANAGE_SIGNATURES_URL", "\"https://mail.infomaniak.com/0/settings/signatures\"")

        buildConfigField("String", "BUGTRACKER_MAIL_BUCKET_ID", "\"app_mail\"")
        buildConfigField("String", "BUGTRACKER_MAIL_PROJECT_NAME", "\"mail\"")
        buildConfigField("String", "GITHUB_REPO", "\"android-mail\"")
        buildConfigField("String", "GITHUB_REPO_URL", "\"https://github.com/Infomaniak/android-kMail\"")

        buildConfigField("String", "UI_TEST_ACCOUNT_EMAIL", "\"${System.getenv("UI_TEST_ACCOUNT_EMAIL") ?: "defaultUser"}\"")
        buildConfigField("String", "UI_TEST_ACCOUNT_PASSWORD", "\"${System.getenv("UI_TEST_ACCOUNT_PASSWORD") ?: "defaultPass"}\"")

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

dependencies {
    implementation(project(":Core"))
    implementation(project(":Core:Auth"))
    implementation(project(":Core:Avatar"))
    implementation(project(":Core:Coil"))
    implementation(project(":Core:Compose:Basics"))
    implementation(project(":Core:Compose:Margin"))
    implementation(project(":Core:Compose:MaterialThemeFromXml"))
    implementation(project(":Core:CrossAppLogin:Back"))
    implementation(project(":Core:CrossAppLogin:Front"))
    implementation(project(":Core:FragmentNavigation"))
    implementation(project(":Core:KSuite"))
    implementation(project(":Core:KSuite:KSuitePro"))
    implementation(project(":Core:KSuite:MyKSuite"))
    implementation(project(":Core:Legacy"))
    implementation(project(":Core:Legacy:AppLock"))
    implementation(project(":Core:Legacy:BugTracker"))
    implementation(project(":Core:Legacy:Confetti"))
    implementation(project(":Core:Legacy:Stores"))
    implementation(project(":Core:Matomo"))
    implementation(project(":Core:Network"))
    implementation(project(":Core:Sentry"))
    implementation(project(":EmojiComponents"))
    implementation(project(":HtmlCleaner"))

    implementation(libs.rich.html.editor)

    implementation(libs.realm.kotlin.base)
    implementation(libs.junit.ktx)
    implementation(libs.espresso.contrib)

    "standardImplementation"(libs.play.services.base)
    "standardImplementation"(libs.firebase.messaging.ktx)

    implementation(libs.dotlottie)
    implementation(core.lottie)
    implementation(libs.dragdropswipe.recyclerview)
    implementation(libs.dotsindicator)
    implementation(libs.emoji2.emojipicker)

    implementation(libs.flexbox)
    implementation(libs.lifecycle.process)
    implementation(libs.webkit)
    implementation(core.androidx.concurrent.futures.ktx)
    implementation(core.androidx.work.runtime)

    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.compiler.androidx)

    implementation(core.sentry.android.fragment)

    // Coil SVG support (auto-injected)
    implementation(core.coil.svg)

    coreLibraryDesugaring(libs.desugar.jdk)

    // Compose
    implementation(platform(core.compose.bom))
    implementation(libs.compose.ui.android)
    implementation(core.compose.runtime)
    implementation(core.compose.material3)
    implementation(core.compose.ui.tooling.preview)

    // Test
    testImplementation(core.junit)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.mockk.android)
    testImplementation(core.kotlinx.coroutines.test)

    androidTestImplementation(core.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.web)
    androidTestImplementation(core.junit)
    androidTestImplementation(libs.junit.ktx)
    androidTestImplementation(libs.stdlib)
    androidTestImplementation(core.androidx.test.core)
    androidTestImplementation(core.androidx.test.core.ktx)
    androidTestImplementation(core.androidx.runner)
    androidTestImplementation(libs.uiautomator)

    debugImplementation(libs.fragment.testing)

    // Debug
    if (enableLeakCanary) {
        debugImplementation(libs.leakcanary.android)
    }
}
