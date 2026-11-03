package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

private const val FAKE_PLATFORM: String = "linux-x86_64"
private val FAKE_BINARY_CONTENT: ByteArray = "#!/bin/sh\necho fake restic\n".toByteArray()

class BundledResticTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `platforms are recognised from the names the JVM reports`() {
        assertEquals("linux-x86_64", HostPlatform.current("Linux", "amd64"))
        assertEquals("linux-aarch64", HostPlatform.current("Linux", "aarch64"))
        assertEquals("windows-x86_64", HostPlatform.current("Windows 11", "amd64"))
        assertEquals("macos-x86_64", HostPlatform.current("Mac OS X", "x86_64"))
        assertEquals("macos-aarch64", HostPlatform.current("Mac OS X", "aarch64"))
        assertNull(HostPlatform.current("FreeBSD", "amd64"), "no artifact exists for FreeBSD")
        assertNull(HostPlatform.current("Linux", "arm"), "no artifact exists for 32-bit ARM")
    }

    @Test
    fun `without an artifact on the class path nothing is extracted`() {
        val emptyClassPath = URLClassLoader(arrayOf(temporaryDirectory.toUri().toURL()), null)

        assertNull(BundledRestic.extractedBinary(temporaryDirectory.resolve("cache"), FAKE_PLATFORM, emptyClassPath))
        assertNull(BundledRestic.extractedBinary(temporaryDirectory.resolve("cache"), null, emptyClassPath))
    }

    @Test
    fun `the binary is extracted once, executable and verified`() {
        val classPath = fakeArtifact(sha256 = sha256Hex(FAKE_BINARY_CONTENT))
        val cache = temporaryDirectory.resolve("cache")

        val extracted = checkNotNull(BundledRestic.extractedBinary(cache, FAKE_PLATFORM, classPath))

        assertTrue(extracted.startsWith(cache))
        assertEquals("fake restic", Files.readAllLines(extracted)[1].removePrefix("echo "))
        assertTrue(Files.isExecutable(extracted))
        assertEquals(extracted, BundledRestic.extractedBinary(cache, FAKE_PLATFORM, classPath))
        assertEquals(1, Files.list(extracted.parent).count(), "no partial files are left behind")
    }

    @Test
    fun `a tampered binary in the cache is replaced`() {
        val classPath = fakeArtifact(sha256 = sha256Hex(FAKE_BINARY_CONTENT))
        val cache = temporaryDirectory.resolve("cache-tampered")
        val description = BundledBinary(FAKE_PLATFORM, "restic", sha256Hex(FAKE_BINARY_CONTENT), "9.9.9")
        description.cachePath(cache).also { it.parent.createDirectories() }.writeText("malicious")

        val extracted = checkNotNull(BundledRestic.extractedBinary(cache, FAKE_PLATFORM, classPath))

        assertTrue(extracted.readText().contains("fake restic"))
    }

    @Test
    fun `a damaged artifact is refused`() {
        val classPath = fakeArtifact(sha256 = "0".repeat(64))

        val failure =
            assertThrows<IllegalStateException> {
                BundledRestic.extractedBinary(temporaryDirectory.resolve("cache-damaged"), FAKE_PLATFORM, classPath)
            }

        assertTrue(failure.message!!.contains("does not match"))
    }

    @Test
    fun `without a configured binary the bundled restic is used`() {
        assumeTrue(HostPlatform.current() != null, "no restic-jvm-binary artifact for this platform")
        val configuration =
            ResticConfiguration
                .builder()
                .bundledBinaryDirectory(temporaryDirectory.resolve("bundled"))
                .build()

        val version = Restic(configuration).tools().version()

        assertTrue(Path.of(configuration.binary).startsWith(temporaryDirectory.resolve("bundled")))
        assertEquals(bundledResticVersion(), version.version)
    }

    @Test
    fun `a configured binary wins over the bundled one`() {
        val configuration = ResticConfiguration.builder().binary("my-restic").build()

        assertEquals("my-restic", configuration.binary)
    }

    private fun fakeArtifact(sha256: String): ClassLoader {
        val root = temporaryDirectory.resolve("artifact-$sha256")
        val directory = root.resolve("io/github/ppuetsch/restic/bundled/$FAKE_PLATFORM").createDirectories()
        directory.resolve("restic").writeBytes(FAKE_BINARY_CONTENT)
        directory.resolve("binary.properties").writeText("file=restic\nsha256=$sha256\nresticVersion=9.9.9\n")
        return URLClassLoader(arrayOf(root.toUri().toURL()), null)
    }

    private fun sha256Hex(content: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(Locale.ROOT, it) }

    private fun bundledResticVersion(): String =
        checkNotNull(
            javaClass.classLoader.getResourceAsStream(
                "io/github/ppuetsch/restic/bundled/${HostPlatform.current()}/binary.properties",
            ),
        ).use { stream ->
            java.util
                .Properties()
                .apply { load(stream) }
                .getProperty("resticVersion")
        }
}
