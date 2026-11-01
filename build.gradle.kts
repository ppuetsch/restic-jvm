buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dependency.check)
    alias(libs.plugins.sonarqube)
    jacoco
}

group = "io.github.ppuetsch"
version = "0.6.0"

val repositoryUrl = "https://github.com/ppuetsch/restic-jvm"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

dependencyLocking {
    lockAllConfigurations()
}

ktlint {
    version.set(libs.versions.ktlint.engine)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    parallel = true
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "SARIF")
    scanConfigurations = listOf("runtimeClasspath")
    nvd.apiKey = providers.environmentVariable("NVD_API_KEY").orNull
    analyzers.ossIndex.enabled = false
}

sonar {
    properties {
        property("sonar.projectKey", "ppuetsch_restic-jvm")
        property("sonar.organization", "ppuetsch")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
    }
}

kotlin {
    jvmToolchain(25)
    explicitApi()
}

java {
    withSourcesJar()
}

val javadocJar =
    tasks.register<Jar>("javadocJar") {
        group = "documentation"
        description = "Packages the Dokka Javadoc as the javadoc jar of the publication."
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
    finalizedBy(tasks.jacocoTestReport)
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// detektMain und detektTest analysieren mit Typauflösung; das einfache `detekt` sähe weniger.
tasks.named("check") {
    dependsOn("detektMain", "detektTest")
}

tasks.register("printVersion") {
    group = "help"
    description = "Prints the project version; the release workflow compares it with the tag."
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
