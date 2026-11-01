package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.io.path.createDirectories

private val STARTUP_TIMEOUT: Duration = Duration.ofSeconds(60)
private val EXIT_TIMEOUT: Duration = Duration.ofSeconds(20)

private typealias CancellableCommand = (Restic, CancellationToken, Path) -> Unit

/** Die Befehle, die der Dienst laut Anforderung mindestens abbrechen können muss; für sie wird der ganze Prozessbaum geprüft. */
private val PROCESS_TREE_COMMANDS: Set<String> = setOf("ls", "unlock", "init", "list", "cat", "snapshots", "check", "prune")

class CancellationTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private fun hangingScenario(directory: Path): Array<String> =
        arrayOf("self ${directory.resolve("restic.pid")}", "child ${directory.resolve("child.pid")}", "sleep")

    private fun allCommands(): Map<String, CancellableCommand> = readingCommands() + maintenanceCommands() + administrationCommands()

    private fun readingCommands(): Map<String, CancellableCommand> =
        mapOf(
            "version" to { restic, token, _ -> restic.version(token) },
            "init" to { restic, token, _ -> restic.init(cancellationToken = token) },
            "backup" to { restic, token, directory -> restic.backup(listOf(directory), backupOptions(token)) },
            "backupStandardInput" to {
                restic,
                token,
                _,
                ->
                restic.backupStandardInput(ByteArrayInputStream(ByteArray(1)), backupOptions(token))
            },
            "backupCommandOutput" to { restic, token, _ -> restic.backupCommandOutput(listOf("true"), backupOptions(token)) },
            "snapshots" to { restic, token, _ -> restic.snapshots(cancellationToken = token) },
            "snapshotGroups" to { restic, token, _ -> restic.snapshotGroups(cancellationToken = token) },
            "ls" to { restic, token, _ -> restic.ls(Restic.LATEST, LsOptions.DEFAULT, token, null) },
            "listNodes" to { restic, token, _ -> restic.listNodes(cancellationToken = token) },
            "find" to { restic, token, _ -> restic.find(listOf("*.txt"), FindOptions.DEFAULT, token) },
            "findObjects" to { restic, token, _ -> restic.findObjects(FindObjectKind.BLOB, listOf("abc"), FindOptions.DEFAULT, token) },
            "restore" to {
                restic,
                token,
                directory,
                ->
                restic.restore(Restic.LATEST, directory, RestoreOptions.builder().cancellationToken(token).build())
            },
            "dump" to { restic, token, _ -> restic.dump(Restic.LATEST, "/a", ByteArrayOutputStream(), DumpOptions.DEFAULT, token) },
            "dumpToFile" to {
                restic,
                token,
                directory,
                ->
                restic.dumpToFile(Restic.LATEST, "/a", directory.resolve("out"), DumpOptions.DEFAULT, token)
            },
            "cat" to { restic, token, _ -> restic.cat(CatObjectType.CONFIG, null, ByteArrayOutputStream(), token) },
            "catText" to { restic, token, _ -> restic.catText(CatObjectType.CONFIG, cancellationToken = token) },
            "catConfig" to { restic, token, _ -> restic.catConfig(token) },
            "catMasterKey" to { restic, token, _ -> restic.catMasterKey(token) },
            "catSnapshot" to { restic, token, _ -> restic.catSnapshot("abc", token) },
            "catKey" to { restic, token, _ -> restic.catKey("abc", token) },
            "catLock" to { restic, token, _ -> restic.catLock("abc", token) },
            "catTree" to { restic, token, _ -> restic.catTree("abc", token) },
            "catIndex" to { restic, token, _ -> restic.catIndex("abc", token) },
        )

    private fun maintenanceCommands(): Map<String, CancellableCommand> =
        mapOf(
            "forget" to { restic, token, _ ->
                restic.forget(
                    ForgetOptions
                        .builder()
                        .keepLast(1)
                        .cancellationToken(token)
                        .build(),
                )
            },
            "prune" to { restic, token, _ -> restic.prune(PruneOptions.builder().cancellationToken(token).build()) },
            "check" to { restic, token, _ -> restic.check(CheckOptions.builder().cancellationToken(token).build()) },
            "stats" to { restic, token, _ -> restic.stats(cancellationToken = token) },
            "diff" to { restic, token, _ -> restic.diff("a", "b", DiffOptions.builder().cancellationToken(token).build()) },
            "diffAll" to { restic, token, _ -> restic.diffAll("a", "b", DiffOptions.builder().cancellationToken(token).build()) },
            "tag" to { restic, token, _ -> restic.tag(TagOptions.builder().add("x").build(), token) },
            "rewrite" to { restic, token, _ -> restic.rewrite(RewriteOptions.builder().cancellationToken(token).build()) },
            "copy" to { restic, token, _ ->
                restic.copy(
                    CopyOptions
                        .builder()
                        .fromRepository("other")
                        .cancellationToken(token)
                        .build(),
                )
            },
        )

    private fun administrationCommands(): Map<String, CancellableCommand> =
        mapOf(
            "list" to { restic, token, _ -> restic.list(ListObjectType.SNAPSHOTS, token) },
            "listBlobs" to { restic, token, _ -> restic.listBlobs(token) },
            "listKeys" to { restic, token, _ -> restic.listKeys(token) },
            "addKey" to { restic, token, _ -> restic.addKey(NewKeyOptions.builder().newPassword("neu").build(), token) },
            "changePassword" to { restic, token, _ -> restic.changePassword(NewKeyOptions.builder().newPassword("neu").build(), token) },
            "removeKey" to { restic, token, _ -> restic.removeKey("abc", token) },
            "unlock" to { restic, token, _ -> restic.unlock(cancellationToken = token) },
            "migrate" to { restic, token, _ -> restic.migrate(emptyList(), false, token) },
            "cache" to { restic, token, _ -> restic.cache(CacheOptions.DEFAULT, token) },
            "features" to { restic, token, _ -> restic.features(token) },
            "recover" to { restic, token, _ -> restic.recover(token) },
            "repairIndex" to { restic, token, _ -> restic.repairIndex(false, token) },
            "repairPacks" to { restic, token, _ -> restic.repairPacks(listOf("abc"), token) },
            "repairSnapshots" to { restic, token, _ -> restic.repairSnapshots(RepairSnapshotsOptions.DEFAULT, token) },
            "generate" to { restic, token, directory -> restic.generate(GenerateOptions.builder().manDirectory(directory).build(), token) },
            "completion" to { restic, token, _ -> restic.completion(CompletionShell.BASH, token) },
            "selfUpdate" to { restic, token, _ -> restic.selfUpdate(null, token) },
            "help" to { restic, token, _ -> restic.help(listOf("backup"), token) },
            "extendedOptions" to { restic, token, _ -> restic.extendedOptions(token) },
        )

    private fun backupOptions(token: CancellationToken): BackupOptions = BackupOptions.builder().cancellationToken(token).build()

    @TestFactory
    fun `every command accepts a token and stops when it is cancelled`(): List<DynamicTest> =
        allCommands().map { (name, command) ->
            DynamicTest.dynamicTest(name) {
                val directory = temporaryDirectory.resolve(name).createDirectories()
                val fake = FakeRestic(directory, "out never reached", "exit 0")
                val cancelled = CancellationToken().also(CancellationToken::cancel)

                assertThrows<ResticCancelledException> { command(fake.restic(), cancelled, directory) }
            }
        }

    @TestFactory
    fun `cancelling a running command ends restic and its child processes`(): List<DynamicTest> =
        allCommands().filterKeys(PROCESS_TREE_COMMANDS::contains).map { (name, command) ->
            DynamicTest.dynamicTest(name) {
                val directory = temporaryDirectory.resolve(name).createDirectories()
                assertCancellationKillsProcessTree(FakeRestic(directory, *hangingScenario(directory)), directory, command)
            }
        }

    private fun assertCancellationKillsProcessTree(
        fake: FakeRestic,
        directory: Path,
        command: CancellableCommand,
    ) {
        val token = CancellationToken()
        val running = CompletableFuture.runAsync { command(fake.restic(), token, directory) }
        val resticProcess = directory.resolve("restic.pid").awaitPid(STARTUP_TIMEOUT)
        val childProcess = directory.resolve("child.pid").awaitPid(STARTUP_TIMEOUT)

        token.cancel()

        val failure = assertThrows<ExecutionException> { running.get() }
        assertTrue(failure.cause is ResticCancelledException, failure.cause.toString())
        assertTrue(resticProcess.awaitExit(EXIT_TIMEOUT), "restic process still alive")
        assertTrue(childProcess.awaitExit(EXIT_TIMEOUT), "child process still alive")
    }

    private fun commandsWithTokenInOptions(): Map<String, CancellableCommand> =
        mapOf(
            "ls" to { restic, token, _ -> restic.ls(Restic.LATEST, LsOptions.builder().cancellationToken(token).build()) },
            "listNodes" to { restic, token, _ -> restic.listNodes(Restic.LATEST, LsOptions.builder().cancellationToken(token).build()) },
            "snapshots" to { restic, token, _ -> restic.snapshots(SnapshotsOptions.builder().cancellationToken(token).build()) },
            "snapshotGroups" to { restic, token, _ -> restic.snapshotGroups(SnapshotsOptions.builder().cancellationToken(token).build()) },
            "find" to { restic, token, _ -> restic.find(listOf("*.txt"), FindOptions.builder().cancellationToken(token).build()) },
            "findObjects" to { restic, token, _ ->
                restic.findObjects(FindObjectKind.BLOB, listOf("abc"), FindOptions.builder().cancellationToken(token).build())
            },
            "stats" to { restic, token, _ -> restic.stats(StatsOptions.builder().cancellationToken(token).build()) },
        )

    @TestFactory
    fun `a token in the options cancels the call`(): List<DynamicTest> =
        commandsWithTokenInOptions().map { (name, command) ->
            DynamicTest.dynamicTest(name) {
                val directory = temporaryDirectory.resolve("options-$name").createDirectories()
                val fake = FakeRestic(directory, "out never reached", "exit 0")
                val cancelled = CancellationToken().also(CancellationToken::cancel)

                assertThrows<ResticCancelledException> { command(fake.restic(), cancelled, directory) }
            }
        }

    @Test
    fun `an explicit token takes precedence over the one in the options`() {
        val fake = FakeRestic(temporaryDirectory, "out not a snapshot header", "exit 0")
        val cancelledInOptions = LsOptions.builder().cancellationToken(CancellationToken().also(CancellationToken::cancel)).build()
        val cancelledExplicitly = CancellationToken().also(CancellationToken::cancel)

        assertThrows<ResticOutputException> { fake.restic().ls(Restic.LATEST, cancelledInOptions, CancellationToken(), null) }
        assertThrows<ResticCancelledException> {
            fake.restic().ls(Restic.LATEST, LsOptions.builder().cancellationToken(CancellationToken()).build(), cancelledExplicitly, null)
        }
        assertThrows<ResticCancelledException> { fake.restic().listNodes(Restic.LATEST, LsOptions.DEFAULT, cancelledExplicitly) }
    }

    @Test
    fun `one token cancels all calls running in parallel`() {
        val calls =
            listOf("first", "second").map { name ->
                val directory = temporaryDirectory.resolve(name).createDirectories()
                directory to FakeRestic(directory, *hangingScenario(directory))
            }
        val token = CancellationToken()
        val running = calls.map { (_, fake) -> CompletableFuture.runAsync { fake.restic().snapshots(cancellationToken = token) } }
        val children = calls.map { (directory, _) -> directory.resolve("child.pid").awaitPid(STARTUP_TIMEOUT) }

        token.cancel()

        running.forEach { call ->
            val failure = assertThrows<ExecutionException> { call.get() }
            assertTrue(failure.cause is ResticCancelledException, failure.cause.toString())
        }
        children.forEach { child -> assertTrue(child.awaitExit(EXIT_TIMEOUT), "child process still alive") }
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
