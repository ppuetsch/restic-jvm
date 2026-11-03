// One artifact per platform with the restic binary for it. The project name names the platform, for example
// restic-jvm-binary-linux-x86_64. The release archive comes from restic's GitHub releases; Gradle's dependency
// verification pins its SHA-256 (gradle/verification-metadata.xml), cross-checked against restic's signed SHA256SUMS.

import java.security.MessageDigest
import java.util.zip.ZipFile

/** restic release asset per platform, as named on https://github.com/restic/restic/releases. */
val releaseAssetByPlatform: Map<String, String> =
    mapOf(
        "linux-x86_64" to "linux_amd64.bz2",
        "linux-aarch64" to "linux_arm64.bz2",
        "windows-x86_64" to "windows_amd64.zip",
        "macos-x86_64" to "darwin_amd64.bz2",
        "macos-aarch64" to "darwin_arm64.bz2",
    )

apply(plugin = "java-library")
apply(from = rootProject.file("gradle/publishing.gradle.kts"))

group = rootProject.group
version = rootProject.version

val platform = project.name.removePrefix("restic-jvm-binary-")
val (assetClassifier, assetExtension) = releaseAssetByPlatform.getValue(platform).split('.')
val resticVersion: String =
    the<VersionCatalogsExtension>().named("libs").findVersion("restic-bundled").get().requiredVersion
val binaryName = if (platform.startsWith("windows")) "restic.exe" else "restic"
val bundledResources = layout.buildDirectory.dir("bundled")

repositories {
    ivy {
        name = "resticReleases"
        url = uri("https://github.com/restic/restic/releases/download")
        patternLayout { artifact("v[revision]/[module]_[revision]_[classifier].[ext]") }
        metadataSources { artifact() }
        content { includeModule("restic", "restic") }
    }
}

val resticRelease: Configuration by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    add(resticRelease.name, "restic:restic:$resticVersion:$assetClassifier@$assetExtension")
}

dependencyLocking {
    lockAllConfigurations()
}

fun extractBinary(
    archive: File,
    target: File,
) {
    if (archive.name.endsWith(".zip")) {
        ZipFile(archive).use { zip ->
            val entry = zip.entries().asSequence().single { it.name.endsWith(".exe") }
            zip.getInputStream(entry).use { input -> target.outputStream().use(input::copyTo) }
        }
    } else {
        ant.withGroovyBuilder { "bunzip2"("src" to archive, "dest" to target) }
    }
}

fun sha256Of(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

val prepareBundledBinary =
    tasks.register("prepareBundledBinary") {
        description = "Extracts the restic binary for $platform and records its version and SHA-256."
        inputs.files(resticRelease)
        inputs.property("resticVersion", resticVersion)
        outputs.dir(bundledResources)
        doLast {
            val directory = bundledResources.get().dir("io/github/ppuetsch/restic/bundled/$platform").asFile
            directory.deleteRecursively()
            directory.mkdirs()
            val binary = File(directory, binaryName)
            extractBinary(resticRelease.singleFile, binary)
            File(directory, "binary.properties").writeText(
                "file=$binaryName\nsha256=${sha256Of(binary)}\nresticVersion=$resticVersion\n",
            )
        }
    }

configure<SourceSetContainer> {
    named("main") { resources.srcDir(prepareBundledBinary) }
}

configure<JavaPluginExtension> {
    // no bytecode inside, so the artifact suits every Java version
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

tasks.named<Jar>("jar") {
    from(rootProject.file("binaries/LICENSE-restic.txt")) { into("META-INF") }
    manifest { attributes("Automatic-Module-Name" to "io.github.ppuetsch.restic.bundled.${platform.replace('-', '.')}") }
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("binary") {
            from(components["java"])
            pom {
                name.set("restic-jvm binary for $platform")
                description.set("restic $resticVersion for $platform, found and used by restic-jvm when no binary is configured")
                licenses {
                    license {
                        name.set("BSD 2-Clause License (restic)")
                        url.set("https://github.com/restic/restic/blob/v$resticVersion/LICENSE")
                    }
                }
            }
        }
    }
}
