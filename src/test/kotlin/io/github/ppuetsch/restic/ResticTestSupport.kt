package io.github.ppuetsch.restic

import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Path
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal val resticBinaryForTests: String = System.getenv("RESTIC_BINARY") ?: DEFAULT_RESTIC_BINARY

internal const val TEST_PASSWORD: String = "test-password"

internal fun resticFor(
    repository: Path,
    configure: ResticConfiguration.Builder.() -> Unit = {},
): Restic =
    Restic(
        ResticConfiguration
            .builder()
            .binary(resticBinaryForTests)
            .repository(repository.toString())
            .password(TEST_PASSWORD)
            .apply(configure)
            .build(),
    )

/** A fresh local repository and a source directory with three files (`a.txt`, `debug.log`, `sub/b.txt`). */
internal class RepositoryFixture(
    workDirectory: Path,
) {
    val source: Path = workDirectory.resolve("source").createDirectories()
    val repository: Path = workDirectory.resolve("repo")
    val restic: Restic = resticFor(repository)

    init {
        source.resolve("a.txt").writeText("hello")
        source.resolve("debug.log").writeText("noise")
        source
            .resolve("sub")
            .createDirectories()
            .resolve("b.txt")
            .writeText("world")
        restic.init()
    }

    fun backup(configure: BackupOptions.Builder.() -> Unit = {}): BackupSummary =
        restic
            .backup(
                listOf(source),
                configure = configure,
            ).summary
}

private val LOCK_POLL_INTERVAL: Duration = Duration.ofMillis(100)
private val LOCK_WAIT_LIMIT: Duration = Duration.ofSeconds(30)

/**
 * Keeps a real, non-exclusive lock on the repository: a backup from standard input that waits for input. [close] ends
 * the input, so restic finishes regularly, stores an empty file as a new snapshot and releases its lock. (Cancelling
 * would kill restic and leave a stale lock behind.)
 */
class HeldLock(
    private val restic: Restic,
) : AutoCloseable {
    private val silentInput = PipedOutputStream()
    private val waitingBackup =
        thread(isDaemon = true) {
            runCatching {
                restic.backupStandardInput(PipedInputStream(silentInput))
            }
        }

    init {
        awaitLock()
    }

    private fun awaitLock() {
        val deadline = System.nanoTime() + LOCK_WAIT_LIMIT.toNanos()
        while (restic.list(ListObjectType.LOCKS).isEmpty()) {
            check(System.nanoTime() < deadline) { "restic took no lock within $LOCK_WAIT_LIMIT" }
            Thread.sleep(LOCK_POLL_INTERVAL.toMillis())
        }
    }

    override fun close() {
        silentInput.close()
        waitingBackup.join(LOCK_WAIT_LIMIT.toMillis())
    }
}
