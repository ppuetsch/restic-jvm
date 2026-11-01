package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

private class RecordingListener : OutputLineListener {
    val standardOutput = CopyOnWriteArrayList<String>()
    val standardError = CopyOnWriteArrayList<String>()

    override fun onLine(line: String) {
        standardOutput += line
    }

    override fun onErrorLine(line: String) {
        standardError += line
    }
}

/** Die Prune-Zeilen sind unveränderte Ausgabe von restic 0.18.1. */
private val PRUNE_WITH_DELETION =
    arrayOf(
        "out loading indexes...",
        "out [0:00] 100.00%  2 / 2 packs processed",
        "out to repack:             0 blobs / 0 B",
        "out this removes:          0 blobs / 0 B",
        "out to delete:             2 blobs / 909 B",
        "out total prune:           2 blobs / 909 B",
        "out remaining:             7 blobs / 295.112 KiB",
        "out rebuilding index",
        "out removing 1 old packs",
        "out [0:00] 100.00%  1 / 1 files deleted",
        "out done",
    )

private val PRUNE_WITH_REPACK =
    arrayOf(
        "out to repack:             40 blobs / 1.5 MiB",
        "out total prune:           100 blobs / 2.5 MiB",
        "out repacking packs",
        "out [0:01] 50.00%  1 / 2 packs repacked",
        "out [0:02] 100.00%  2 / 2 packs repacked",
        "out removing 3 old packs",
        "out done",
    )

class CheckAndPruneOutputTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `check streams both outputs while running and keeps the lines in the result`() {
        val fake =
            FakeRestic(
                temporaryDirectory,
                """out using temporary cache in /tmp/x""",
                """err hint: repository is slow""",
                """out {"message_type":"error","error":{"message":"pack abc damaged"}}""",
                """out {"message_type":"summary","num_errors":1,"broken_packs":["abc"],""" +
                    """"suggest_repair_index":true,"suggest_prune":false}""",
                "exit 1",
            )
        val listener = RecordingListener()

        val result = fake.restic().check(CheckOptions.DEFAULT, listener)

        assertEquals(result.outputLines, listener.standardOutput)
        assertEquals("using temporary cache in /tmp/x", result.outputLines.first())
        assertEquals(
            listOf("using temporary cache in /tmp/x", "error: pack abc damaged", "check finished: 1 errors, 1 broken packs"),
            result.outputLines,
        )
        assertEquals(
            listOf(
                CheckMessage.Text("using temporary cache in /tmp/x"),
                CheckMessage.Error("pack abc damaged"),
                CheckMessage.Summary(1, listOf("abc"), suggestRepairIndex = true, suggestPrune = false),
            ),
            result.messages,
        )
        assertEquals(listOf("hint: repository is slow"), listener.standardError)
        assertEquals(listener.standardError, result.errorOutputLines)
        assertEquals(1, result.errorCount)
        assertEquals(listOf("abc"), result.brokenPacks)
        assertEquals(listOf("pack abc damaged"), result.errorMessages)
        assertTrue(result.suggestRepairIndex)
    }

    @Test
    fun `check without a listener still collects the lines`() {
        val fake =
            FakeRestic(
                temporaryDirectory,
                """out {"message_type":"summary","num_errors":0,"broken_packs":null,"suggest_repair_index":false,"suggest_prune":false}""",
            )

        val result = fake.restic().check()

        assertTrue(result.isHealthy())
        assertEquals(1, result.outputLines.size)
    }

    @Test
    fun `check against a real repository reports lines and a healthy result`() {
        val repository = RepositoryFixture(temporaryDirectory)
        repository.backup()
        val listener = RecordingListener()

        val result = repository.restic.check(CheckOptions.DEFAULT, listener)

        assertTrue(result.isHealthy())
        assertTrue(result.outputLines.any { it.startsWith("check finished") }, result.outputLines.toString())
        assertEquals(result.outputLines, listener.standardOutput)
    }

    @Test
    fun `prune reports packs and freed bytes from a deletion run`() {
        val listener = RecordingListener()

        val result = FakeRestic(temporaryDirectory, *PRUNE_WITH_DELETION).restic().prune(PruneOptions.DEFAULT, listener)

        assertEquals(PRUNE_WITH_DELETION.size, result.outputLines.size)
        assertEquals(result.outputLines, listener.standardOutput)
        assertEquals(1, result.packsDeleted)
        assertEquals(0, result.packsRepacked)
        assertEquals(909L, result.bytesFreed)
        assertNull(result.packsToKeep)
    }

    @Test
    fun `prune takes the last repack progress and converts units`() {
        val result = FakeRestic(temporaryDirectory, *PRUNE_WITH_REPACK).restic().prune()

        assertEquals(2, result.packsRepacked)
        assertEquals(3, result.packsDeleted)
        assertEquals((2.5 * 1024 * 1024).toLong(), result.bytesFreed)
    }

    @Test
    fun `prune reads verbose pack counts`() {
        val fake =
            FakeRestic(
                temporaryDirectory,
                "out total prune:           1 blobs / 1.000 KiB",
                "out to keep:      5 packs",
                "out to repack:    1 packs",
                "out to delete:    2 packs",
            )

        val result = fake.restic().prune()

        assertEquals(Triple(5, 1, 2), Triple(result.packsToKeep, result.packsRepacked, result.packsDeleted))
        assertEquals(1024L, result.bytesFreed)
    }

    @Test
    fun `prune figures stay null when restic prints no summary`() {
        val result = FakeRestic(temporaryDirectory, "out something unexpected").restic().prune()

        assertEquals(listOf("something unexpected"), result.outputLines)
        assertNull(result.bytesFreed)
        assertNull(result.packsDeleted)
        assertNull(result.packsRepacked)
    }

    @Test
    fun `prune forwards standard error lines to the listener`() {
        val listener = RecordingListener()

        FakeRestic(
            temporaryDirectory,
            "err warning: slow backend",
            "out total prune: 0 blobs / 0 B",
        ).restic().prune(PruneOptions.DEFAULT, listener)

        assertEquals(listOf("warning: slow backend"), listener.standardError)
    }

    @Test
    fun `prune against a real repository reports freed bytes`() {
        val repository = RepositoryFixture(temporaryDirectory)
        repository.backup()
        repository.source.resolve("c.txt").toFile().writeText("neu")
        repository.backup()
        repository.restic.forget { keepLast(1) }

        val result = repository.restic.prune()

        assertTrue(result.bytesFreed != null, result.outputLines.toString())
        assertTrue(result.packsDeleted != null && result.packsRepacked != null, result.outputLines.toString())
    }
}
