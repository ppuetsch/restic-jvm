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
 * Optionen für [Restic.mount] (`restic mount`, nur auf Plattformen mit FUSE verfügbar, nicht unter Windows).
 *
 * @property filter Auswahl der eingehängten Snapshots nach Host, Pfad und Tags.
 * @property allowOther Erlaubt anderen Benutzern den Zugriff (`--allow-other`).
 * @property noDefaultPermissions Schaltet die Rechteprüfung des Kernels ab (`--no-default-permissions`).
 * @property ownerRoot Zeigt root als Eigentümer aller Dateien (`--owner-root`).
 * @property pathTemplates Vorlagen für die Verzeichnisstruktur der Snapshots (`--path-template`).
 * @property timeTemplate Zeitformat in den Verzeichnisnamen (`--time-template`).
 */
public class MountOptions private constructor(
    builder: Builder,
) {
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

    /** Stellt [MountOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var allowOther: Boolean = false
        internal var noDefaultPermissions: Boolean = false
        internal var ownerRoot: Boolean = false
        internal val pathTemplates: MutableList<String> = mutableListOf()
        internal var timeTemplate: String? = null

        /** Hängt nur Snapshots dieses Hosts ein (`--host`); mehrfach möglich. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Hängt nur Snapshots ein, die genau diesen Pfad gesichert haben (`--path`); mehrfach möglich. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Hängt nur Snapshots ein, die alle diese Tags tragen (`--tag`); mehrere Aufrufe sind ODER-verknüpft. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Erlaubt anderen Benutzern den Zugriff (`--allow-other`); braucht `user_allow_other` in `/etc/fuse.conf`. */
        @JvmOverloads
        public fun allowOther(enabled: Boolean = true): Builder = apply { allowOther = enabled }

        /** Schaltet die Rechteprüfung des Kernels ab; jeder mit Zugriff sieht alle Dateien (`--no-default-permissions`). */
        @JvmOverloads
        public fun noDefaultPermissions(enabled: Boolean = true): Builder = apply { noDefaultPermissions = enabled }

        /** Zeigt root als Eigentümer aller Dateien (`--owner-root`). */
        @JvmOverloads
        public fun ownerRoot(enabled: Boolean = true): Builder = apply { ownerRoot = enabled }

        /** Vorlage für die Verzeichnisstruktur, z.B. `hosts/%h/%T` (`--path-template`); mehrfach möglich. */
        public fun pathTemplate(template: String): Builder = apply { pathTemplates.add(template) }

        /** Go-Zeitformat für die Verzeichnisnamen, z.B. `2006-01-02_15-04-05` (`--time-template`). */
        public fun timeTemplate(template: String): Builder = apply { timeTemplate = template }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): MountOptions = MountOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: hängt alle Snapshots ein. */
        @JvmField
        public val DEFAULT: MountOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Ein laufendes `restic mount`. [close] beendet restic, das dabei das Dateisystem aushängt; deshalb eignet sich der
 * Handle für `use { }` bzw. try-with-resources.
 */
public class MountHandle internal constructor(
    private val process: Process,
    private val ready: CompletableFuture<Unit>,
    private val errorOutput: CompletableFuture<String>,
) : AutoCloseable {
    /**
     * Wartet, bis restic meldet, dass das Repository bereitgestellt ist.
     *
     * @throws ResticTimeoutException wenn restic nicht rechtzeitig bereit ist; restic wird dann beendet.
     * @throws ResticCommandException wenn restic sich vorher beendet, etwa weil FUSE fehlt.
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

    /** `true`, solange der restic-Prozess läuft. */
    public fun isRunning(): Boolean = process.isAlive

    /** Beendet restic und hängt damit aus; nach 30 Sekunden ohne Ende wird restic samt Kindprozessen hart beendet. */
    override fun close() {
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(UNMOUNT_GRACE_SECONDS, TimeUnit.SECONDS)) ProcessTree.terminateForcibly(process)
    }

    private fun exitCodeOrOne(): Int = if (process.isAlive) 1 else process.exitValue()

    /** Nach dem Prozessende braucht der Leser von stderr noch einen Moment, bis er alles hat. */
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
            // Stream wurde mit dem Prozess geschlossen
        }
        ready.completeExceptionally(IOException("restic mount exited before the repository was mounted"))
    }
}
