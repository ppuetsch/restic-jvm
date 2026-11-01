package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

class RestoreDumpCatTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var fixture: RepositoryFixture
    private lateinit var target: Path

    private val restic: Restic get() = fixture.restic

    @BeforeEach
    fun createFixtureAndBackup() {
        fixture = RepositoryFixture(temporaryDirectory)
        fixture.backup()
        target = temporaryDirectory.resolve("restored")
    }

    private fun restoredFiles(): List<Path> =
        if (Files.exists(target)) Files.walk(target).use { stream -> stream.filter { Files.isRegularFile(it) }.toList() } else emptyList()

    private fun restoredFile(name: String): Path? = restoredFiles().firstOrNull { it.name == name }

    /** Nur das Quellverzeichnis wiederherstellen, weil Windows die Zeitstempel von Vorfahren wie C:\Users nicht setzen darf. */
    private fun sourceSpec(): String = "latest:" + snapshotPathOf("source")

    private fun snapshotPathOf(name: String): String =
        restic
            .listNodes("latest", LsOptions.builder().recursive().build())
            .nodes
            .first {
                it.name == name
            }.path

    @Test
    fun `restore writes all files with their content`() {
        val summary = restic.restore(sourceSpec(), target)
        assertEquals("hello", restoredFile("a.txt")?.readText())
        assertEquals("world", restoredFile("b.txt")?.readText())
        assertEquals(3, restoredFiles().size)
        assertTrue(summary.totalBytes >= 15)
    }

    @Test
    fun `restore reports events to the listener`() {
        val events = mutableListOf<RestoreEvent>()
        restic.restore(sourceSpec(), target, RestoreOptions.builder().verbosity(2).build(), events::add)
        assertTrue(events.last() is RestoreEvent.Summary)
        assertTrue(events.any { it is RestoreEvent.FileAction && it.item.orEmpty().endsWith("a.txt") }, events.toString())
    }

    @Test
    fun `include and exclude patterns select files`() {
        restic.restore(sourceSpec(), target) { include("*.txt") }
        assertEquals(setOf("a.txt", "b.txt"), restoredFiles().map { it.name }.toSet())

        val other = temporaryDirectory.resolve("restored2")
        restic.restore(sourceSpec(), other) { exclude("*.txt") }
        val names = Files.walk(other).use { s -> s.filter { Files.isRegularFile(it) }.map { it.name }.toList() }
        assertEquals(listOf("debug.log"), names)
    }

    @Test
    fun `case insensitive include and exclude and pattern files`() {
        restic.restore(sourceSpec(), target) { caseInsensitiveInclude("*.TXT") }
        assertEquals(setOf("a.txt", "b.txt"), restoredFiles().map { it.name }.toSet())

        val patterns = temporaryDirectory.resolve("patterns.txt").also { it.writeText("*.log\n") }
        val withFile = temporaryDirectory.resolve("restored3")
        restic.restore(sourceSpec(), withFile) { excludeFile(patterns) }
        assertFalse(Files.walk(withFile).use { s -> s.anyMatch { it.name == "debug.log" } })

        val includes = temporaryDirectory.resolve("includes.txt").also { it.writeText("*.log\n") }
        val onlyLogs = temporaryDirectory.resolve("restored4")
        restic.restore(sourceSpec(), onlyLogs) { includeFile(includes) }
        assertTrue(Files.walk(onlyLogs).use { s -> s.anyMatch { it.name == "debug.log" } })
        assertFalse(Files.walk(onlyLogs).use { s -> s.anyMatch { it.name == "a.txt" } })

        restic.restore(sourceSpec(), temporaryDirectory.resolve("restored5")) {
            caseInsensitiveExclude("*.TXT")
            caseInsensitiveExcludeFile(patterns)
        }
        restic.restore(sourceSpec(), temporaryDirectory.resolve("restored6")) {
            caseInsensitiveInclude("*.LOG")
            caseInsensitiveIncludeFile(includes)
        }
    }

    @Test
    fun `dry run restores nothing`() {
        val summary = restic.restore(sourceSpec(), target) { dryRun() }
        assertTrue(summary.totalFiles > 0)
        assertEquals(0, restoredFiles().size)
    }

    @Test
    fun `overwrite never keeps existing content and always replaces it`() {
        restic.restore(sourceSpec(), target)
        val file = restoredFile("a.txt")!!
        file.writeText("changed")
        restic.restore(sourceSpec(), target) { overwrite(OverwriteBehavior.NEVER) }
        assertEquals("changed", file.readText())
        restic.restore(sourceSpec(), target) { overwrite(OverwriteBehavior.IF_CHANGED) }
        assertEquals("hello", file.readText())
        file.writeText("again")
        restic.restore(sourceSpec(), target) { overwrite(OverwriteBehavior.ALWAYS) }
        assertEquals("hello", file.readText())
        restic.restore(sourceSpec(), target) { overwrite(OverwriteBehavior.IF_NEWER) }
    }

    @Test
    fun `delete removes files that are not in the snapshot`() {
        restic.restore(sourceSpec(), target)
        val extra = restoredFile("a.txt")!!.resolveSibling("extra.txt").also { it.writeText("extra") }
        restic.restore(sourceSpec(), target) { delete() }
        assertFalse(Files.exists(extra))
    }

    @Test
    fun `verify sparse and verbosity flags are accepted`() {
        restic.restore(sourceSpec(), target) {
            verify()
            sparse()
        }
        assertEquals("hello", restoredFile("a.txt")?.readText())
    }

    @Test
    fun `snapshot filters apply for latest`() {
        fixture.backup {
            host("other")
            tag("special")
        }
        restic.restore(sourceSpec(), target) {
            host("other")
            tag("special")
            path(fixture.source.toString())
        }
        assertEquals(3, restoredFiles().size)
    }

    @Test
    fun `restoring an unknown snapshot fails`() {
        assertThrows<ResticFailedException> { restic.restore("00000000", target) }
    }

    @Test
    fun `dump streams file content`() {
        val output = ByteArrayOutputStream()
        restic.dump("latest", snapshotPathOf("a.txt"), output)
        assertEquals("hello", output.toString(Charsets.UTF_8))
    }

    @Test
    fun `dump of a directory is a tar or zip archive`() {
        val directory = snapshotPathOf("sub")
        val tar = ByteArrayOutputStream().also { restic.dump("latest", directory, it) }.toByteArray()
        assertTrue(String(tar, Charsets.ISO_8859_1).contains("b.txt"))
        val zip =
            ByteArrayOutputStream().also {
                restic.dump("latest", directory, it, DumpOptions.builder().archiveFormat(ArchiveFormat.ZIP).build())
            }
        assertEquals("PK", String(zip.toByteArray(), 0, 2, Charsets.ISO_8859_1))
    }

    @Test
    fun `dump to a target file with host path and tag filters`() {
        val out = temporaryDirectory.resolve("dumped.txt")
        val options =
            DumpOptions
                .builder()
                .host(restic.snapshots().single().hostname)
                .path(fixture.source.toString())
                .build()
        restic.dumpToFile("latest", snapshotPathOf("a.txt"), out, options)
        assertEquals("hello", out.readText())
    }

    @Test
    fun `cat prints config snapshot and tree objects as text`() {
        assertTrue(restic.catText(CatObjectType.CONFIG).contains("chunker_polynomial"))
        val snapshot = restic.snapshots().single()
        assertTrue(restic.catText(CatObjectType.SNAPSHOT, snapshot.id).contains(snapshot.tree))
        assertTrue(restic.catText(CatObjectType.MASTERKEY).contains("encrypt"))
        assertTrue(restic.catText(CatObjectType.TREE, snapshot.id).contains("\"nodes\""))
    }

    @Test
    fun `cat of blob writes raw bytes`() {
        val blobId = blobIds().first()
        val output = ByteArrayOutputStream()
        restic.cat(CatObjectType.BLOB, blobId, output)
        assertTrue(output.size() > 0)
    }

    @Test
    fun `cat requires an id for types that need one`() {
        assertThrows<IllegalArgumentException> { restic.catText(CatObjectType.SNAPSHOT) }
    }

    @Test
    fun `cat of other object types`() {
        val lines = mutableListOf<String>()
        ProcessRunner(restic.configuration).runChecked(listOf("list", "keys"), lines::add)
        assertTrue(restic.catText(CatObjectType.KEY, lines.first().trim()).contains("kdf"))
        val index = mutableListOf<String>()
        ProcessRunner(restic.configuration).runChecked(listOf("list", "index"), index::add)
        assertTrue(restic.catText(CatObjectType.INDEX, index.first().trim()).contains("packs"))
        val packs = mutableListOf<String>()
        ProcessRunner(restic.configuration).runChecked(listOf("list", "packs"), packs::add)
        assertTrue(ByteArrayOutputStream().also { restic.cat(CatObjectType.PACK, packs.first().trim(), output = it) }.size() > 0)
    }

    private fun blobIds(): List<String> {
        val lines = mutableListOf<String>()
        ProcessRunner(restic.configuration).runChecked(listOf("list", "blobs"), lines::add)
        return lines.filter { it.startsWith("data ") }.map { it.substringAfter(' ').trim() }
    }
}
