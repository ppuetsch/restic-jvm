// Pulls in the restic binaries of all platforms; restic-jvm picks the matching one at runtime.

apply(plugin = "java-library")
apply(from = rootProject.file("gradle/publishing.gradle.kts"))

group = rootProject.group
version = rootProject.version

val platformArtifacts = rootProject.subprojects.filter { it.name.startsWith("restic-jvm-binary-") }

dependencies {
    platformArtifacts.forEach { add("runtimeOnly", it) }
}

dependencyLocking {
    lockAllConfigurations()
}

configure<JavaPluginExtension> {
    // no bytecode inside, so the artifact suits every Java version
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("all") {
            from(components["java"])
            pom {
                name.set("restic-jvm binaries for all platforms")
                description.set("Depends on the restic binaries of every supported platform; restic-jvm picks the matching one at runtime")
                licenses {
                    license {
                        name.set("BSD 2-Clause License (restic)")
                        url.set("https://github.com/restic/restic/blob/master/LICENSE")
                    }
                }
            }
        }
    }
}
