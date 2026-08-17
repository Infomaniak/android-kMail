import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

/**
 * Applied to every OldKotlin module that should be published to Reposilite (`realm-models`,
 * `emoji-reaction-models`). Configures the release AAR + sources jar publication, its POM
 * metadata, and GPG signing, and registers the module's Maven repository (releases or snapshots
 * depending on the version).
 *
 * The published version is controlled via the `oldkotlin.version` Gradle property, e.g.
 * `./gradlew :OldKotlin:publishAllPublicationsToReposiliteRepository -Poldkotlin.version=1.0.1`
 * `-Poldkotlin.version=1.0.1-SNAPSHOT` publishes to the snapshots repository instead of releases.
 */
class PublishingPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("maven-publish")
            apply("signing")
        }

        group = "com.infomaniak.mail.oldkotlin"
        // Fall back to "unspecified" so that regular tasks (compile, assemble, tests…) keep working
        // without -Poldkotlin.version. Publishing tasks fail eagerly below, since publishing with an
        // "unspecified" version would silently produce an unusable artifact.
        version = getPropertyValue("oldkotlin.version") ?: "unspecified"

        tasks.withType<AbstractPublishToMaven>().configureEach {
            doFirst {
                check(version.toString() != "unspecified") {
                    "Missing version: pass -Poldkotlin.version=<version> (e.g. 1.0.1, or 1.0.1-SNAPSHOT " +
                        "to publish to the snapshots repository) when publishing OldKotlin modules."
                }
            }
        }

        extensions.configure<LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") {
                        from(components["release"])

                        pom {
                            name.set(project.name)
                            description.set("Infomaniak Mail - OldKotlin ${project.name} module")
                            url.set("https://github.com/Infomaniak/android-kMail")
                            licenses {
                                license {
                                    name.set("GPL-3.0")
                                    url.set("https://www.gnu.org/licenses/gpl-3.0.html")
                                }
                            }
                            issueManagement {
                                system.set("GitHub")
                                url.set("https://github.com/Infomaniak/android-kMail/issues")
                            }
                            scm {
                                connection.set("scm:git:https://github.com/Infomaniak/android-kMail.git")
                                developerConnection.set("scm:git:ssh://git@github.com/Infomaniak/android-kMail.git")
                                url.set("https://github.com/Infomaniak/android-kMail")
                            }
                            organization {
                                name.set("Infomaniak Network SA")
                                url.set("https://www.infomaniak.com/")
                            }
                            developers {
                                developer {
                                    id.set("Infomaniak")
                                    email.set("mobile+libraries@infomaniak-dev.ch")
                                    name.set("Infomaniak Development Team")
                                    url.set("https://www.infomaniak.com/")
                                }
                            }
                        }
                    }
                }

                repositories {
                    maven {
                        name = "reposilite"
                        url = uri(
                            if (version.toString().endsWith("SNAPSHOT")) {
                                "https://maven.infomaniak.app/snapshots"
                            } else {
                                "https://maven.infomaniak.app/releases"
                            }
                        )
                        credentials {
                            username = getPropertyValue("reposiliteUsername")
                            password = getPropertyValue("reposilitePassword")
                        }
                    }
                }
            }

            extensions.configure<SigningExtension> {
                val keyId: String = getPropertyValue("GPG_key_id") ?: return@configure
                val ringFile: String = getPropertyValue("GPG_private_key")?.replace('#', '\n') ?: return@configure
                val password: String = getPropertyValue("GPG_private_password") ?: return@configure

                isRequired = true
                useInMemoryPgpKeys(keyId, ringFile, password)
                sign(extensions.getByType<PublishingExtension>().publications)

                // Workaround for a Gradle bug, the issue is still open.
                // https://github.com/gradle/gradle/issues/26091#issuecomment-1722947958
                tasks.withType<AbstractPublishToMaven>().configureEach {
                    val signingTasks = tasks.withType<Sign>()
                    mustRunAfter(signingTasks)
                }
            }
        }
    }

    private fun Project.getPropertyValue(propertyName: String): String? {
        if (project.hasProperty(propertyName)) return project.property(propertyName) as String
        return System.getenv(propertyName)
    }
}
