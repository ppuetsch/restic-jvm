package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class VersionAndInitTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `version reports restic and go details`() {
        val version = resticFor(temporaryDirectory.resolve("unused")).version()
        assertTrue(version.isAtLeast(0, 17), version.toString())
        assertTrue(version.goVersion.startsWith("go"), version.toString())
        assertTrue(version.goOs.isNotEmpty() && version.goArch.isNotEmpty())
    }

    @Test
    fun `version comparison`() {
        val version = ResticVersion("0.18.1", "go1.25", "linux", "amd64")
        assertTrue(version.isAtLeast(0, 18, 1))
        assertTrue(version.isAtLeast(0, 17, 9))
        assertFalse(version.isAtLeast(0, 18, 2))
        assertFalse(version.isAtLeast(1, 0))
        assertTrue(ResticVersion("0.19.0-dev", "", "", "").isAtLeast(0, 18))
    }

    @Test
    fun `init creates a repository usable afterwards`() {
        val repository = temporaryDirectory.resolve("repo")
        val result = resticFor(repository).init()
        assertTrue(Regex("[0-9a-f]{64}").matches(result.id), result.toString())
        assertEquals(repository.toString(), result.repository)
        assertTrue(Files.exists(repository.resolve("config")))
    }

    @Test
    fun `init with repository version 1 and kotlin dsl`() {
        val repository = temporaryDirectory.resolve("repo-v1")
        resticFor(repository) { compression(Compression.OFF) }.init { repositoryVersion("1") }
        assertTrue(Files.exists(repository.resolve("keys")))
    }

    @Test
    fun `init on an existing repository fails`() {
        val restic = resticFor(temporaryDirectory.resolve("repo"))
        restic.init()
        val failure = assertThrows<ResticFailedException> { restic.init() }
        assertTrue(failure.errorMessage.contains("config file already exists"), failure.errorMessage)
    }

    @Test
    fun `init can copy chunker parameters from another repository`() {
        val source = temporaryDirectory.resolve("source")
        resticFor(source).init()
        val target = temporaryDirectory.resolve("target")
        resticFor(target).init {
            copyChunkerParams()
            fromRepository(source.toString())
            fromPassword(TEST_PASSWORD)
        }
        assertTrue(Files.exists(target.resolve("config")))
    }

    @Test
    fun `init with insecure empty password`() {
        val repository = temporaryDirectory.resolve("nopw")
        val restic =
            Restic(
                ResticConfiguration.builder()
                    .binary(resticBinaryForTests)
                    .repository(repository.toString())
                    .insecureNoPassword()
                    .build(),
            )
        restic.init()
        assertTrue(Files.exists(repository.resolve("config")))
    }
}
