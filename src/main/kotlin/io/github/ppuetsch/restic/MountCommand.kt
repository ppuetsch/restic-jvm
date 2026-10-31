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

/** Optionen für `restic mount` (nur auf Plattformen mit FUSE verfügbar, nicht unter Windows). */
public class MountOptions private constructor(builder: Builder) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val allowOther: Boolean = builder.allowOther
    public val noDefaultPermissions: Boolean = builder.noDefaultPermissions
    public val ownerRoot: Boolean = builder.ownerRoot
    public val pathTemplates: List<String> = builder.pathTemplates.toList()
    public val timeTemplate: String? = builder.timeTemplate

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalFlag("--allow-other", allowOther)
            optionalFlag("--no-default-permissions", noDefaultPermissions)
            optionalFlag("--owner-root", ownerRoot)
            pathTemplates.forEach { optionalValue("--path-template", it) }
            optionalValue("--time-template", timeTemplate)
        }

    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var allowOther: Boolean = false
        internal var noDefaultPermissions: Boolean = false
        internal var ownerRoot: Boolean = false
        internal val pathTemplates: MutableList<String> = mutableListOf()
        internal var timeTemplate: String? = null

        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        @JvmOverloads
        public fun allowOther(enabled: Boolean = true): Builder = apply { allowOther = enabled }

        @JvmOverloads
        public fun noDefaultPermissions(enabled: Boolean = true): Builder = apply { noDefaultPermissions = enabled }

        @JvmOverloads
        public fun ownerRoot(enabled: Boolean = true): Builder = apply { ownerRoot = enabled }

        public fun pathTemplate(template: String): Builder = apply { pathTemplates.add(template) }

        public fun timeTemplate(template: String): Builder = apply { timeTemplate = template }

        public fun build(): MountOptions = MountOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: MountOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Ein laufendes `restic mount`. [close] beendet restic, das dabei das Dateisystem aushängt.
 */
public class MountHandle internal constructor(
    private val process: Process,
    private val ready: CompletableFuture<Unit>,
    private val errorOutput: CompletableFuture<String>,
) : AutoCloseable {
    /** Wartet, bis restic meldet, dass das Repository bereitgestellt ist. */
    @JvmOverloads
    public fun awaitMounted(timeout: Duration = DEFAULT_MOUNT_TIMEOUT) {
        try {
            ready.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (failure: TimeoutException) {
            close()
            throw ResticTimeoutException("restic mount was not ready within $timeout")
        } catch (failure: ExecutionException) {
            process.waitFor(EXIT_GRACE_SECONDS, TimeUnit.SECONDS)
            throw commandExceptionFor(exitCodeOrOne(), errorOutput.getNow(""))
        }
    }

    public fun isRunning(): Boolean = process.isAlive

    override fun close() {
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(UNMOUNT_GRACE_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    private fun exitCodeOrOne(): Int = if (process.isAlive) 1 else process.exitValue()
}

internal class MountCommand(private val runner: ProcessRunner) {
    fun mount(
        mountpoint: Path,
        options: MountOptions,
    ): MountHandle {
        val process = runner.startProcess(ProcessRequest(listOf("mount") + options.arguments() + mountpoint.toString()))
        val ready = CompletableFuture<Unit>()
        process.outputStream.close()
        Thread.ofVirtual().start { watchForReadyMarker(process, ready) }
        val errorOutput = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }
        return MountHandle(process, ready, errorOutput)
    }

    private fun watchForReadyMarker(
        process: Process,
        ready: CompletableFuture<Unit>,
    ) {
        try {
            process.inputStream.bufferedReader().forEachLine { line -> if (line.contains(READY_MARKER)) ready.complete(Unit) }
        } catch (ignored: IOException) {
            // Stream wurde mit dem Prozess geschlossen
        }
        ready.completeExceptionally(IOException("restic mount exited before the repository was mounted"))
    }
}
