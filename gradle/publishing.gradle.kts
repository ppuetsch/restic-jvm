// Publishing shared by all artifacts: the POM fields Maven Central requires, signing and the target repository.
// Each project creates its publication and sets name, description and licenses itself.

apply(plugin = "maven-publish")
apply(plugin = "signing")

val repositoryUrl = "https://github.com/ppuetsch/restic-jvm"

configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        pom {
            url.set(repositoryUrl)
            developers {
                developer {
                    id.set("ppuetsch")
                    name.set("Philip Puetsch")
                    url.set("https://github.com/ppuetsch")
                }
            }
            scm {
                url.set(repositoryUrl)
                connection.set("scm:git:$repositoryUrl.git")
                developerConnection.set("scm:git:ssh://git@github.com/ppuetsch/restic-jvm.git")
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ppuetsch/restic-jvm")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

configure<SigningExtension> {
    val signingKey = providers.environmentVariable("SIGNING_KEY").orNull?.takeIf(String::isNotBlank)
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
    isRequired = false
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(the<PublishingExtension>().publications)
    }
}
