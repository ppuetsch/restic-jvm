package io.github.ppuetsch.restic

import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val READY_MARKER: String = "Now serving the repository"
private val DEFAULT_MOUNT_TIMEOUT: Duration = Duration.ofSeconds(60)
private const val UNMOUNT_GRACE_SECONDS: Long = 30
private const val EXIT_GRACE_SECONDS: Long = 5

/**
 * A running `restic mount`. [close] ends restic, which unmounts the file system on the way out; that is why the
 * handle suits `use { }` and try-with-resources.
 */
public class MountHandle internal constructor(
    private val process: Process,
    private val ready: CompletableFuture<Unit>,
    private val errorOutput: CompletableFuture<String>,
) : AutoCloseable {
    /**
     * Waits until restic reports that the repository is mounted.
     *
     * @throws ResticTimeoutException if restic is not ready in time; restic is then ended.
     * @throws ResticCommandException if restic exits before, for example because FUSE is missing.
     */
    @JvmOverloads
    public fun awaitMounted(timeout: Duration = DEFAULT_MOUNT_TIMEOUT) {
        try {
            ready.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (notReady: TimeoutException) {
            close()
            throw ResticTimeoutException("restic mount was not ready within $timeout", notReady)
        } catch (expectedEarlyExit: ExecutionException) {
            process.waitFor(EXIT_GRACE_SECONDS, TimeUnit.SECONDS)
            throw commandExceptionFor(exitCodeOrOne(), errorOutputOrEmpty())
        }
    }

    /** `true` while the restic process is running. */
    public fun isRunning(): Boolean = process.isAlive

    /** Ends restic and thereby unmounts; if it has not ended after 30 seconds, restic and its child processes are killed. */
    override fun close() {
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(UNMOUNT_GRACE_SECONDS, TimeUnit.SECONDS)) ProcessTree.terminateForcibly(process)
    }

    private fun exitCodeOrOne(): Int = if (process.isAlive) 1 else process.exitValue()

    /** After the process has ended, the stderr reader needs a moment to collect everything. */
    private fun errorOutputOrEmpty(): String =
        errorOutput
            .completeOnTimeout("", EXIT_GRACE_SECONDS, TimeUnit.SECONDS)
            .exceptionally { "" }
            .join()
}

internal class MountCommand(
    private val runner: ProcessRunner,
) {
    fun mount(
        mountpoint: Path,
        options: MountOptions,
    ): MountHandle {
        val process = runner.startProcess(ProcessRequest(listOf("mount") + options.arguments() + mountpoint.toString()))
        val ready = CompletableFuture<Unit>()
        process.outputStream.close()
        STREAM_READERS.execute { watchForReadyMarker(process, ready) }
        val errorOutput = CompletableFuture.supplyAsync({ process.errorStream.bufferedReader().readText() }, STREAM_READERS)
        return MountHandle(process, ready, errorOutput)
    }

    private fun watchForReadyMarker(
        process: Process,
        ready: CompletableFuture<Unit>,
    ) {
        try {
            process.inputStream.bufferedReader().forEachLine { line -> if (line.contains(READY_MARKER)) ready.complete(Unit) }
        } catch (ignored: IOException) {
            // the stream was closed together with the process
        }
        ready.completeExceptionally(IOException("restic mount exited before the repository was mounted"))
    }
}
