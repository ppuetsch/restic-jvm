package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

private const val DAMAGED_PACK: String = "d61057f9e90fcffe81ee84410aaea29de8ccd11bd4544fe30ac5ca168dee2a74"

/** The lines are unchanged text output of restic 0.18.1. */
private val HEALTHY_CHECK =
    arrayOf(
        "out using temporary cache in /tmp/restic-check-cache",
        "out load indexes",
        "out check all packs",
        "out check snapshots, trees and blobs",
        "out [0:01]          0 snapshots",
        "out [0:12] 28.77%  353 / 1227 snapshots",
        "out read all data",
        "out [1:02:03] 4.10%  276 / 6727 packs",
        "out no errors were found",
    )

private val DAMAGED_CHECK =
    arrayOf(
        "out read all data",
        "out [0:00] 100.00%  2 / 2 packs",
        "err pack $DAMAGED_PACK contains 2 errors: [blob e8b0: ciphertext verification failed]",
        "err ",
        "err The repository contains damaged pack files.",
        "err restic repair packs $DAMAGED_PACK",
        "err restic repair snapshots --forget",
        "err Fatal: repository contains errors",
        "exit 1",
    )

class CheckProgressTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private fun checkWithProgress(vararg scenario: String): Pair<CheckResult, List<CheckProgress>> {
        val progress = CopyOnWriteArrayList<CheckProgress>()
        val result = FakeRestic(temporaryDirectory, *scenario).restic().maintenance().check { progress(progress::add) }
        return result to progress
    }

    @Test
    fun `progress lines become progress of the current phase`() {
        val (result, progress) = checkWithProgress(*HEALTHY_CHECK)

        assertEquals(
            listOf(
                CheckProgress("check snapshots, trees and blobs", Duration.ofSeconds(1), null, 0, null, "snapshots"),
                CheckProgress("check snapshots, trees and blobs", Duration.ofSeconds(12), 28.77 / 100, 353, 1227, "snapshots"),
                CheckProgress("read all data", Duration.ofSeconds(3723), 4.10 / 100, 276, 6727, "packs"),
            ),
            progress,
        )
        assertTrue(result.isHealthy())
        assertTrue(result.outputLines.none { it.startsWith("[") }, result.outputLines.toString())
        assertEquals("no errors were found", result.outputLines.last())
    }

    @Test
    fun `a damaged repository is a result with the broken packs from the text`() {
        val (result, _) = checkWithProgress(*DAMAGED_CHECK)

        assertFalse(result.isHealthy())
        assertEquals(listOf(DAMAGED_PACK), result.brokenPacks)
        assertEquals(1, result.errorCount)
        assertTrue(result.errorMessages.first().startsWith("pack $DAMAGED_PACK contains 2 errors"))
        assertFalse(result.errorMessages.contains("Fatal: repository contains errors"))
    }

    @Test
    fun `a locked repository is an exception with progress`() {
        val exception =
            assertThrows<ResticCommandException> {
                checkWithProgress("err unable to create lock in backend: repository is already locked by PID 1", "exit 11")
            }

        assertEquals(ResticErrorKind.REPOSITORY_LOCKED, exception.kind)
    }

    @Test
    fun `a locked repository is an exception in json mode although restic prints a summary`() {
        val fake =
            FakeRestic(
                temporaryDirectory,
                """out {"message_type":"summary","num_errors":1,"broken_packs":null,""" +
                    """"suggest_repair_index":false,"suggest_prune":false}""",
                """err {"message_type":"exit_error","code":11,""" +
                    """"message":"unable to create lock in backend: repository is already locked"}""",
                "exit 11",
            )

        val exception = assertThrows<ResticCommandException> { fake.restic().maintenance().check() }

        assertEquals(ResticErrorKind.REPOSITORY_LOCKED, exception.kind)
    }

    @Test
    fun `any other failure with progress is an exception`() {
        assertThrows<ResticCommandException> { checkWithProgress("err Fatal: unable to open config file", "exit 1") }
    }

    @Test
    fun `a line without progress format is a phase`() {
        assertNull(checkProgressFrom("read group #1 of 76 data packs (out of total 6727 packs in 100 groups)", ""))
    }

    @Test
    fun `check against a real repository reports progress up to the end`() {
        val repository = RepositoryFixture(temporaryDirectory)
        repository.backup()
        val progress = CopyOnWriteArrayList<CheckProgress>()

        val result = repository.restic.maintenance().check { readData().progress(progress::add) }

        assertTrue(result.isHealthy())
        assertTrue(progress.any { it.unit == "packs" && it.percentDone == 1.0 }, progress.toString())
    }
}
