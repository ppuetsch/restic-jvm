package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val SPECIAL_FILE: String = "mySpecialFile.txt"
private const val SPECIAL_CONTENT: String = "only in one snapshot"

/** Runs the Kotlin examples from docs/module.md so the documentation cannot drift away from the API. */
class DocumentationExamplesKotlinTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var restic: Restic

    @BeforeEach
    fun createRepositoryWithTwoSnapshots() {
        restic = resticFor(temporaryDirectory.resolve("repo"))
        restic.init()
        val source = temporaryDirectory.resolve("source/nested").createDirectories()
        source.resolve("other.txt").writeText("always there")
        restic.backup(listOf(source))
        source.resolve(SPECIAL_FILE).writeText(SPECIAL_CONTENT)
        restic.backup(listOf(source))
    }

    @Test
    fun `print all locks`() {
        HeldLock(restic).use {
            val lines =
                restic.list(ListObjectType.LOCKS).map { lock ->
                    "${lock.id}: pid ${lock.pid} on ${lock.hostname} since ${lock.time}" + if (lock.exclusive) " (exclusive)" else ""
                }
            lines.forEach(::println)

            assertEquals(1, lines.size)
        }
    }

    @Test
    fun `iterate all snapshots`() {
        val lines = restic.snapshots().map { "${it.shortId} ${it.time} ${it.hostname} ${it.paths}" }
        lines.forEach(::println)

        assertEquals(2, lines.size)
    }

    @Test
    fun `snapshots containing a file`() {
        val snapshotsWithFile = restic.find(listOf(SPECIAL_FILE)).map { it.snapshot }

        assertEquals(listOf(restic.snapshots().last().id), snapshotsWithFile.map { it.id })
    }

    @Test
    fun `extract a file into memory`() {
        val hit = restic.find(listOf(SPECIAL_FILE)).first()

        val buffer = ByteArrayOutputStream()
        restic.files().dump(hit.snapshotId, hit.matches.first().path, buffer)
        val content = buffer.toString(Charsets.UTF_8)

        assertEquals(SPECIAL_CONTENT, content)
    }
}
