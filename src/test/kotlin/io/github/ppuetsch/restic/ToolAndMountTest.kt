package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

class ToolAndMountTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var fixture: RepositoryFixture

    private val restic: Restic get() = fixture.restic

    @BeforeEach
    fun createFixture() {
        fixture = RepositoryFixture(temporaryDirectory)
    }

    @Test
    fun `generate writes completion files and man pages`() {
        val bash = temporaryDirectory.resolve("restic.bash")
        val fish = temporaryDirectory.resolve("restic.fish")
        val zsh = temporaryDirectory.resolve("restic.zsh")
        val powershell = temporaryDirectory.resolve("restic.ps1")
        val man = temporaryDirectory.resolve("man").createDirectories()
        restic.generate {
            bashCompletion(bash)
            fishCompletion(fish)
            zshCompletion(zsh)
            powershellCompletion(powershell)
            manDirectory(man)
        }
        listOf(bash, fish, zsh, powershell).forEach { assertTrue(Files.size(it) > 0, it.toString()) }
        assertTrue(Files.list(man).use { it.count() } > 5)
        assertTrue(bash.readText().contains("restic"))
    }

    @Test
    fun `completion scripts can be read as text`() {
        CompletionShell.entries.forEach { shell ->
            assertTrue(restic.completion(shell).contains("restic"), shell.name)
        }
    }

    @Test
    fun `help prints the usage of a command`() {
        assertTrue(restic.help("backup").contains("restic backup"))
        assertTrue(restic.help().contains("Available Commands"))
        assertTrue(restic.help("key", "add").contains("key add"))
    }

    @Test
    fun `extended options are parsed`() {
        val options = restic.extendedOptions()
        assertTrue(options.any { it.name == "local.connections" }, options.take(3).toString())
        assertTrue(options.all { it.description.isNotEmpty() })
    }

    @Test
    fun `self update can write the binary to another file`() {
        val executableName = if (System.getProperty("os.name").startsWith("Windows")) "restic-new.exe" else "restic-new"
        val output = temporaryDirectory.resolve(executableName)
        val result = restic.selfUpdate(output)
        assertTrue(result.lines.isNotEmpty())
    }

    private fun mountIsSupported(): Boolean {
        val isLinux = System.getProperty("os.name").startsWith("Linux")
        val hasFuseDevice = Files.exists(Path.of("/dev/fuse"))
        val hasFusermount =
            System.getenv("PATH").orEmpty().split(':').any {
                Files.exists(Path.of(it, "fusermount3")) || Files.exists(Path.of(it, "fusermount"))
            }
        return isLinux && hasFuseDevice && hasFusermount
    }

    private fun findFileNamed(
        directory: Path,
        name: String,
    ): Path = Files.walk(directory).use { stream -> stream.filter { it.fileName.toString() == name }.findFirst().orElseThrow() }

    @Test
    fun `mount flags exist in the installed restic`() {
        assumeTrue(System.getProperty("os.name").startsWith("Linux"), "mount is not available on this platform")
        val help = restic.help("mount")
        val expectedFlags = listOf("--allow-other", "--no-default-permissions", "--owner-root", "--path-template")
        val expectedTemplateFlags = listOf("--time-template", "--host", "--path", "--tag")
        (expectedFlags + expectedTemplateFlags).forEach { assertTrue(help.contains(it), "$it missing in: $help") }
    }

    @Test
    fun `mount exposes the snapshots as a directory tree`() {
        assumeTrue(mountIsSupported(), "FUSE is not available")
        fixture.backup()
        val mountpoint = temporaryDirectory.resolve("mnt").createDirectories()
        restic.mount(mountpoint, MountOptions.builder().ownerRoot().build()).use { handle ->
            handle.awaitMounted(Duration.ofSeconds(60))
            assertTrue(handle.isRunning())
            val latest = mountpoint.resolve("snapshots").resolve("latest").toRealPath()
            val readFile = findFileNamed(latest, "a.txt")
            assertEquals("hello", readFile.readText())
        }
    }

    @Test
    fun `mount reports failure when the repository cannot be opened`() {
        assumeTrue(System.getProperty("os.name").startsWith("Linux"), "mount is not available on this platform")
        val broken = resticFor(temporaryDirectory.resolve("does-not-exist"))
        val handle = broken.mount(temporaryDirectory.resolve("mnt2").createDirectories())
        try {
            val failure = runCatching { handle.awaitMounted(Duration.ofSeconds(30)) }.exceptionOrNull()
            assertTrue(failure is ResticCommandException, failure.toString())
            assertFalse(handle.isRunning())
        } finally {
            handle.close()
        }
    }
}
