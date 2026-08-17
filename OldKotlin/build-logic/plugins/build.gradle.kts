plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("publishing") {
            id = "com.infomaniak.mail.oldkotlin.plugins.publishing"
            implementationClass = "PublishingPlugin"
        }
    }
}
