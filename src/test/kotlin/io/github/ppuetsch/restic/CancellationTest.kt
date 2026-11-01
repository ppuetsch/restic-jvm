package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.io.path.createDirectories

private val STARTUP_TIMEOUT: Duration = Duration.ofSeconds(60)
private val EXIT_TIMEOUT: Duration = Duration.ofSeconds(20)

class CancellationTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private fun hangingScenario(directory: Path): Array<String> =
        arrayOf("self ${directory.resolve("restic.pid")}", "child ${directory.resolve("child.pid")}", "sleep")

    private fun cancellableCommands(): Map<String, (Restic, CancellationToken) -> Unit> =
        mapOf(
            "ls" to { restic, token -> restic.ls(Restic.LATEST, LsOptions.DEFAULT, token, null) },
            "listNodes" to { restic, token -> restic.listNodes(cancellationToken = token) },
            "init" to { restic, token -> restic.init(cancellationToken = token) },
            "unlock" to { restic, token -> restic.unlock(cancellationToken = token) },
            "list" to { restic, token -> restic.list(ListObjectType.SNAPSHOTS, token) },
            "listBlobs" to { restic, token -> restic.listBlobs(token) },
            "cat" to { restic, token -> restic.cat(CatObjectType.CONFIG, null, ByteArrayOutputStream(), token) },
            "catText" to { restic, token -> restic.catText(CatObjectType.CONFIG, cancellationToken = token) },
            "snapshots" to { restic, token -> restic.snapshots(cancellationToken = token) },
            "snapshotGroups" to { restic, token -> restic.snapshotGroups(cancellationToken = token) },
            "stats" to { restic, token -> restic.stats(cancellationToken = token) },
            "listKeys" to { restic, token -> restic.listKeys(token) },
            "check" to { restic, token -> restic.check(CheckOptions.builder().cancellationToken(token).build()) },
            "prune" to { restic, token -> restic.prune(PruneOptions.builder().cancellationToken(token).build()) },
        )

    @TestFactory
    fun `every command throws ResticCancelledException and leaves no process behind`(): List<DynamicTest> =
        cancellableCommands().map { (name, command) ->
            DynamicTest.dynamicTest(name) {
                val directory = temporaryDirectory.resolve(name).createDirectories()
                assertCancellationKillsProcessTree(FakeRestic(directory, *hangingScenario(directory)), directory, command)
            }
        }

    private fun assertCancellationKillsProcessTree(
        fake: FakeRestic,
        directory: Path,
        command: (Restic, CancellationToken) -> Unit,
    ) {
        val token = CancellationToken()
        val running = CompletableFuture.runAsync { command(fake.restic(), token) }
        val resticProcess = directory.resolve("restic.pid").awaitPid(STARTUP_TIMEOUT)
        val childProcess = directory.resolve("child.pid").awaitPid(STARTUP_TIMEOUT)

        token.cancel()

        val failure = assertThrows<ExecutionException> { running.get() }
        assertTrue(failure.cause is ResticCancelledException, failure.cause.toString())
        assertTrue(resticProcess.awaitExit(EXIT_TIMEOUT), "restic process still alive")
        assertTrue(childProcess.awaitExit(EXIT_TIMEOUT), "child process still alive")
    }

    @Test
    fun `a token cancelled before the call stops it immediately`() {
        val fake = FakeRestic(temporaryDirectory, *hangingScenario(temporaryDirectory))
        val token = CancellationToken().also(CancellationToken::cancel)

        assertThrows<ResticCancelledException> { fake.restic().snapshots(cancellationToken = token) }
    }

    @Test
    fun `timeout also ends the child processes`() {
        val fake = FakeRestic(temporaryDirectory, *hangingScenario(temporaryDirectory))
        val restic = fake.restic { timeout(Duration.ofSeconds(20)) }

        assertThrows<ResticTimeoutException> { restic.snapshots() }

        val childProcess = fake.pidWrittenTo("child.pid").awaitPid(STARTUP_TIMEOUT)
        assertTrue(childProcess.awaitExit(EXIT_TIMEOUT), "child process still alive")
        assertFalse(fake.pidWrittenTo("restic.pid").awaitPid(STARTUP_TIMEOUT).isRunning())
    }
}
