package io.github.ppuetsch.restic

import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

private const val RESOURCE_ROOT: String = "io/github/ppuetsch/restic/bundled"
private const val DESCRIPTION_FILE: String = "binary.properties"
private const val HASH_PREFIX_IN_DIRECTORY_NAME: Int = 16

/**
 * A restic binary shipped in a `restic-jvm-binary-*` artifact, described by the `binary.properties` next to it.
 *
 * @property fileName `restic` or `restic.exe`.
 * @property sha256 SHA-256 of the binary, recorded when the artifact was built.
 * @property resticVersion The restic version of the binary.
 */
internal data class BundledBinary(
    val platform: String,
    val fileName: String,
    val sha256: String,
    val resticVersion: String,
) {
    val resourcePath: String get() = "$RESOURCE_ROOT/$platform/$fileName"

    /** A directory per version and content, so different library versions never overwrite each other's binary. */
    fun cachePath(cacheDirectory: Path): Path =
        cacheDirectory.resolve("$resticVersion-${sha256.take(HASH_PREFIX_IN_DIRECTORY_NAME)}").resolve(fileName)
}

/**
 * Finds the restic binary for the running platform on the class path and extracts it into a cache directory, where it
 * can be executed. A binary is checked against its SHA-256 before it is used, once per process.
 */
internal object BundledRestic {
    private val verifiedBinaries: MutableSet<Path> = ConcurrentHashMap.newKeySet()

    /** The extracted binary, or `null` if no artifact for this platform is on the class path. */
    fun extractedBinary(
        cacheDirectory: Path,
        platform: String? = HostPlatform.current(),
        classLoader: ClassLoader = BundledRestic::class.java.classLoader,
    ): Path? = platform?.let { describe(it, classLoader) }?.let { bundled -> verifiedCopy(bundled, cacheDirectory, classLoader) }

    private fun verifiedCopy(
        bundled: BundledBinary,
        cacheDirectory: Path,
        classLoader: ClassLoader,
    ): Path {
        val target = bundled.cachePath(cacheDirectory)
        val alreadyVerified = target in verifiedBinaries
        val intactCopyExists = alreadyVerified || (Files.isRegularFile(target) && Sha256.of(target) == bundled.sha256)
        if (!intactCopyExists) extract(bundled, classLoader, target)
        verifiedBinaries.add(target)
        return target
    }

    private fun describe(
        platform: String,
        classLoader: ClassLoader,
    ): BundledBinary? {
        val description = classLoader.getResourceAsStream("$RESOURCE_ROOT/$platform/$DESCRIPTION_FILE") ?: return null
        val properties = description.use { stream -> Properties().apply { load(stream) } }
        return BundledBinary(
            platform = platform,
            fileName = properties.getProperty("file"),
            sha256 = properties.getProperty("sha256"),
            resticVersion = properties.getProperty("resticVersion"),
        )
    }

    /** Writes next to the target first and moves it into place, so a parallel process never runs a half-written file. */
    private fun extract(
        bundled: BundledBinary,
        classLoader: ClassLoader,
        target: Path,
    ) {
        Files.createDirectories(target.parent)
        val partial = Files.createTempFile(target.parent, bundled.fileName, ".partial")
        try {
            val writtenHash =
                checkNotNull(classLoader.getResourceAsStream(bundled.resourcePath)) { "${bundled.resourcePath} is missing" }
                    .use { resource -> Files.newOutputStream(partial).use { output -> Sha256.copyHashing(resource, output) } }
            check(writtenHash == bundled.sha256) {
                "The bundled restic for ${bundled.platform} does not match its recorded SHA-256; the artifact is damaged"
            }
            check(partial.toFile().setExecutable(true, false)) {
                "Cannot make the restic binary in ${target.parent} executable; choose another bundledBinaryDirectory"
            }
            moveIntoPlace(partial, target, bundled.sha256)
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    /** On Windows a running binary cannot be replaced; if an identical one is already in place, that one is used. */
    private fun moveIntoPlace(
        partial: Path,
        target: Path,
        expectedSha256: String,
    ) {
        try {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (failure: AccessDeniedException) {
            check(Sha256.of(target) == expectedSha256) { "Cannot replace $target: ${failure.message}" }
        } catch (failure: FileAlreadyExistsException) {
            check(Sha256.of(target) == expectedSha256) { "Cannot replace $target: ${failure.message}" }
        }
    }
}
