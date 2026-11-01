plugins {
    kotlin("jvm") version "2.3.0"
    `java-library`
    `maven-publish`
    signing
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("org.jetbrains.dokka") version "2.1.0"
    id("org.jetbrains.dokka-javadoc") version "2.1.0"
}

group = "io.github.ppuetsch"
version = "0.3.0"

val repositoryUrl = "https://github.com/ppuetsch/restic-jvm"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
    explicitApi()
}

java {
    withSourcesJar()
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.github.ppuetsch.restic")
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

tasks.register("printVersion") {
    val projectVersion = project.version.toString()
    doLast { println(projectVersion) }
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            artifact(javadocJar)
            artifactId = "restic-jvm"
            pom {
                name.set("restic-jvm")
                description.set("Kotlin/Java library that drives the restic backup CLI")
                url.set(repositoryUrl)
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
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

signing {
    val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
    isRequired = false
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["library"])
    }
}
