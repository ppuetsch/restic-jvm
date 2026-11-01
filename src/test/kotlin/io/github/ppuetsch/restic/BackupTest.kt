package io.github.ppuetsch.restic

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class BackupTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var source: Path
    private lateinit var repository: Path
    private lateinit var restic: Restic

    @BeforeEach
    fun createRepositoryAndSources() {
        source = temporaryDirectory.resolve("source").createDirectories()
        source.resolve("a.txt").writeText("hello")
        source.resolve("debug.log").writeText("noise")
        source.resolve("sub").createDirectories().resolve("b.txt").writeText("world")
        repository = temporaryDirectory.resolve("repo")
        restic = resticFor(repository)
        restic.init()
    }

    private fun snapshots(): List<JsonNode> {
        val lines = mutableListOf<String>()
        ProcessRunner(restic.configuration).runChecked(listOf("snapshots", "--json"), lines::add)
        return Json.parse(lines.joinToString("")).toList()
    }

    @Test
    fun `first backup creates a snapshot and second backup is incremental`() {
        val first = restic.backup(listOf(source)).summary
        assertEquals(3, first.filesNew)
        assertTrue(Regex("[0-9a-f]{64}").matches(first.snapshotId.orEmpty()))
        assertNotNull(first.backupStart)

        val second = restic.backup(listOf(source)).summary
        assertEquals(0, second.filesNew)
        assertEquals(3, second.filesUnmodified)
        assertEquals(2, snapshots().size)
    }

    @Test
    fun `a relative path starting with a dash is backed up instead of being read as a flag`() {
        temporaryDirectory.resolve("-dash").createDirectories().resolve("c.txt").writeText("dash")
        val inWorkDirectory = resticFor(repository) { workingDirectory(temporaryDirectory) }

        val summary = inWorkDirectory.backup(listOf(Path.of("-dash"))).summary

        assertEquals(1, summary.filesNew)
    }

    @Test
    fun `listener receives events and ends with the summary`() {
        val events = mutableListOf<BackupEvent>()
        val result = restic.backup(listOf(source), BackupOptions.DEFAULT, events::add)
        assertEquals(BackupEvent.Summary(result.summary), events.last())
    }

    @Test
    fun `verbose reports an action per file`() {
        val actions = mutableListOf<BackupEvent.FileAction>()
        restic.backup(listOf(source), BackupOptions.builder().verbose().build()) { event ->
            if (event is BackupEvent.FileAction) actions += event
        }
        assertTrue(actions.any { it.action == "new" && it.item.orEmpty().endsWith("a.txt") }, actions.toString())
    }

    @Test
    fun `exclude patterns and kotlin dsl`() {
        val summary =
            restic.backup(listOf(source)) {
                exclude("*.log")
                excludeLargerThan("1k")
            }.summary
        assertEquals(2, summary.filesNew)
    }

    @Test
    fun `case insensitive exclude`() {
        val summary = restic.backup(listOf(source)) { caseInsensitiveExclude("*.LOG") }.summary
        assertEquals(2, summary.filesNew)
    }

    @Test
    fun `exclude file is honoured`() {
        val patterns = temporaryDirectory.resolve("excludes.txt")
        patterns.writeText("*.log\nsub\n")
        val summary = restic.backup(listOf(source)) { excludeFile(patterns) }.summary
        assertEquals(1, summary.filesNew)
    }

    @Test
    fun `exclude if present skips marked directories`() {
        source.resolve("sub").resolve(".nobackup").writeText("")
        val summary = restic.backup(listOf(source)) { excludeIfPresent(".nobackup") }.summary
        assertEquals(3, summary.filesNew)
    }

    @Test
    fun `exclude caches skips tagged cache directories`() {
        val cache = source.resolve("cache").createDirectories()
        cache.resolve("CACHEDIR.TAG").writeText("Signature: 8a477f597d28d172789f06886806bc55")
        cache.resolve("data.bin").writeText("cached")
        val summary = restic.backup(listOf(source)) { excludeCaches() }.summary
        assertEquals(4, summary.filesNew)
    }

    @Test
    fun `dry run writes no snapshot`() {
        val summary = restic.backup(listOf(source)) { dryRun() }.summary
        assertTrue(summary.dryRun)
        assertEquals(0, snapshots().size)
    }

    @Test
    fun `tags host and time end up on the snapshot`() {
        restic.backup(listOf(source)) {
            tag("daily")
            tag("test")
            host("my-host")
            time(LocalDateTime.of(2020, 1, 2, 3, 4, 5))
        }
        val snapshot = snapshots().single()
        assertEquals(listOf("daily", "test"), snapshot.textList("tags"))
        assertEquals("my-host", snapshot.text("hostname"))
        assertTrue(snapshot.text("time").startsWith("2020-01-02T03:04:05"), snapshot.text("time"))
    }

    @Test
    fun `skip if unchanged creates no second snapshot`() {
        restic.backup(listOf(source))
        val second = restic.backup(listOf(source)) { skipIfUnchanged() }.summary
        val snapshotWasSkipped = second.snapshotId == null
        assertEquals(snapshotWasSkipped, snapshots().size == 1)
    }

    @Test
    fun `parent group by force and scan flags are accepted`() {
        val first = restic.backup(listOf(source)).summary
        val summary =
            restic.backup(listOf(source)) {
                parent(first.snapshotId.orEmpty())
                groupBy("host")
                force()
                noScan()
                ignoreCtime()
                ignoreInode()
                withAtime()
                readConcurrency(1)
            }.summary
        assertNotNull(summary.snapshotId)
    }

    @Test
    fun `one file system flag is accepted`() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"), "device ids are not supported on Windows")
        assertNotNull(restic.backup(listOf(source)) { oneFileSystem() }.summary.snapshotId)
    }

    @Test
    fun `files from list selects what is backed up`() {
        val list = temporaryDirectory.resolve("list.txt")
        list.writeText(source.resolve("a.txt").toString() + "\n")
        val summary = restic.backup(emptyList()) { filesFrom(list) }.summary
        assertEquals(1, summary.filesNew)
    }

    @Test
    fun `files from raw and verbatim are accepted`() {
        val verbatim = temporaryDirectory.resolve("verbatim.txt")
        verbatim.writeText(source.resolve("a.txt").toString() + "\n")
        assertEquals(1, restic.backup(emptyList()) { filesFromVerbatim(verbatim) }.summary.filesNew)
        val raw = temporaryDirectory.resolve("raw.bin")
        Files.write(raw, (source.resolve("sub").resolve("b.txt").toString() + "\u0000").toByteArray())
        assertEquals(1, restic.backup(emptyList()) { filesFromRaw(raw) }.summary.filesNew)
    }

    @Test
    fun `missing source fails with ResticFailedException`() {
        assertThrows<ResticFailedException> { restic.backup(listOf(temporaryDirectory.resolve("gibtsnicht"))) }
    }

    @Test
    fun `standard input is stored under the given filename`() {
        val content = "from stdin".byteInputStream()
        val summary = restic.backupStandardInput(content, BackupOptions.builder().stdinFilename("dump.sql").build()).summary
        assertEquals(1, summary.filesNew)
        val listing = mutableListOf<String>()
        ProcessRunner(restic.configuration).runChecked(listOf("ls", "latest", "--json"), listing::add)
        assertTrue(listing.any { it.contains("/dump.sql") }, listing.toString())
    }

    @Test
    fun `command output is stored`() {
        val javaLauncher = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val summary =
            restic.backupCommandOutput(
                listOf(javaLauncher, "--version"),
                BackupOptions.builder().stdinFilename("java.txt").build(),
            ).summary
        assertEquals(1, summary.filesNew)
    }

    @Test
    fun `cancellation token aborts a running backup`() {
        val hangingProgram = temporaryDirectory.resolve("Hang.java")
        hangingProgram.writeText("public class Hang { public static void main(String[] a) throws Exception { Thread.sleep(60000); } }")
        val javaLauncher = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val token = CancellationToken()
        Thread.ofVirtual().start {
            Thread.sleep(3000)
            token.cancel()
        }
        assertThrows<ResticCancelledException> {
            restic.backupCommandOutput(
                listOf(javaLauncher, hangingProgram.toString()),
                BackupOptions.builder().cancellationToken(token).build(),
            )
        }
        assertTrue(token.isCancelled)
        assertFalse(snapshots().isNotEmpty())
    }
}
