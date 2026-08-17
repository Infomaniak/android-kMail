plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.realm.kotlin) apply false
}

// Prints "groupId:artifactId:version" for every module actually published to Reposilite (i.e.
// every module applying the `com.infomaniak.mail.oldkotlin.plugins.publishing` plugin), one per
// line. Used by the snapshot and release workflows to build the KChat notification without having
// to hardcode the module list.
tasks.register("listPublishedModules") {
    doLast {
        subprojects
            .filter { it.pluginManager.hasPlugin("com.infomaniak.mail.oldkotlin.plugins.publishing") }
            .sortedBy { it.name }
            .forEach { println("${it.group}:${it.name}:${it.version}") }
    }
}
