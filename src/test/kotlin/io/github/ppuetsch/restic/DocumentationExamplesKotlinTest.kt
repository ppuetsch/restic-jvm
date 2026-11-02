package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val SPECIAL_FILE: String = "mySpecialFile.txt"
private const val SPECIAL_CONTENT: String = "nur in einem Snapshot"

/** Führt die Kotlin-Beispiele aus docs/module.md aus, damit die Doku nicht von der API abweicht. */
class DocumentationExamplesKotlinTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var restic: Restic

    @BeforeEach
    fun createRepositoryWithTwoSnapshots() {
        restic = resticFor(temporaryDirectory.resolve("repo"))
        restic.init()
        val source = temporaryDirectory.resolve("source/nested").createDirectories()
        source.resolve("other.txt").writeText("immer da")
        restic.backup(listOf(source))
        source.resolve(SPECIAL_FILE).writeText(SPECIAL_CONTENT)
        restic.backup(listOf(source))
    }

    @Test
    fun `print all locks`() {
        val printed = mutableListOf<String>()

        restic.list(ListObjectType.LOCKS).forEach { lockId ->
            val lock = restic.catLock(lockId)
            printed += "$lockId: pid ${lock.pid} on ${lock.hostname} since ${lock.time}" + if (lock.exclusive) " (exclusive)" else ""
        }

        assertTrue(printed.isEmpty(), "an idle repository holds no locks")
    }

    @Test
    fun `iterate all snapshots`() {
        val printed = mutableListOf<String>()

        restic.snapshots().forEach { snapshot ->
            printed += "${snapshot.shortId} ${snapshot.time} ${snapshot.hostname} ${snapshot.paths}"
        }

        assertEquals(2, printed.size)
    }

    @Test
    fun `snapshots containing a file`() {
        val snapshotIds = restic.find(listOf(SPECIAL_FILE)).map { it.snapshotId }.toSet()
        val snapshotsWithFile = restic.snapshots().filter { it.id in snapshotIds }

        assertEquals(listOf(restic.snapshots()[1].id), snapshotsWithFile.map { it.id })
    }

    @Test
    fun `extract a file into memory`() {
        val hit = restic.find(listOf(SPECIAL_FILE)).first()

        val buffer = ByteArrayOutputStream()
        restic.dump(hit.snapshotId, hit.matches.first().path, buffer)
        val content = buffer.toString(Charsets.UTF_8)

        assertEquals(SPECIAL_CONTENT, content)
    }
}
