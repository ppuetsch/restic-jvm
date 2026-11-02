package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val REWRITE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val SAVED_SNAPSHOT_PATTERN = Regex("""snapshot (\w+) saved""")
private val SAVED_REWRITE_PATTERN = Regex("""saved new snapshot (\w+)""")

/**
 * Optionen für [Restic.rewrite] (`restic rewrite`). Ohne Snapshot-IDs und Filter werden alle Snapshots umgeschrieben.
 *
 * @property filter Auswahl der Snapshots nach Host, Pfad und Tags.
 * @property snapshotIds Umzuschreibende Snapshots.
 * @property dryRun Nur zeigen, was umgeschrieben würde (`--dry-run`).
 * @property excludes Nachträglich auszuschließende Muster (`--exclude`).
 * @property caseInsensitiveExcludes Ausschlussmuster ohne Groß-/Kleinschreibung (`--iexclude`).
 * @property excludeFiles Dateien mit Ausschlussmustern (`--exclude-file`).
 * @property caseInsensitiveExcludeFiles Dateien mit Ausschlussmustern ohne Groß-/Kleinschreibung (`--iexclude-file`).
 * @property forget Entfernt die Originale nach dem Umschreiben (`--forget`).
 * @property newHost Neuer Hostname der Snapshots (`--new-host`).
 * @property newTime Neuer Zeitpunkt der Snapshots (`--new-time`).
 * @property snapshotSummary Ergänzt fehlende Zusammenfassungen (`--snapshot-summary`).
 * @property cancellationToken Token, über das sich der Aufruf abbrechen lässt.
 */
public class RewriteOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val snapshotIds: List<String> = builder.snapshotIds.toList()
    public val dryRun: Boolean = builder.dryRun
    public val excludes: List<String> = builder.excludes.toList()
    public val caseInsensitiveExcludes: List<String> = builder.caseInsensitiveExcludes.toList()
    public val excludeFiles: List<Path> = builder.excludeFiles.toList()
    public val caseInsensitiveExcludeFiles: List<Path> = builder.caseInsensitiveExcludeFiles.toList()
    public val forget: Boolean = builder.forget
    public val newHost: String? = builder.newHost
    public val newTime: LocalDateTime? = builder.newTime
    public val snapshotSummary: Boolean = builder.snapshotSummary
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalFlag("--dry-run", dryRun)
            excludes.forEach { optionalValue("--exclude", it) }
            caseInsensitiveExcludes.forEach { optionalValue("--iexclude", it) }
            excludeFiles.forEach { optionalValue("--exclude-file", it) }
            caseInsensitiveExcludeFiles.forEach { optionalValue("--iexclude-file", it) }
            optionalFlag("--forget", forget)
            optionalValue("--new-host", newHost)
            optionalValue("--new-time", newTime?.format(REWRITE_TIME_FORMAT))
            optionalFlag("--snapshot-summary", snapshotSummary)
            addAll(snapshotIds)
        }

    /** Stellt [RewriteOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    @Suppress("TooManyFunctions") // eine Methode je restic-Flag; das Builder-API bildet die CLI vollständig ab
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal var dryRun: Boolean = false
        internal val excludes: MutableList<String> = mutableListOf()
        internal val caseInsensitiveExcludes: MutableList<String> = mutableListOf()
        internal val excludeFiles: MutableList<Path> = mutableListOf()
        internal val caseInsensitiveExcludeFiles: MutableList<Path> = mutableListOf()
        internal var forget: Boolean = false
        internal var newHost: String? = null
        internal var newTime: LocalDateTime? = null
        internal var snapshotSummary: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        /** Schreibt nur Snapshots dieses Hosts um (`--host`); mehrfach möglich. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Schreibt nur Snapshots um, die genau diesen Pfad gesichert haben (`--path`); mehrfach möglich. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Schreibt nur Snapshots um, die alle diese Tags tragen (`--tag`); mehrere Aufrufe sind ODER-verknüpft. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Schreibt diesen Snapshot um (ID, Kurz-ID oder `latest`); mehrfach möglich. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Zeigt nur, was umgeschrieben würde (`--dry-run`). */
        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /** Entfernt passende Pfade aus den Snapshots (`--exclude`); mehrfach möglich. */
        public fun exclude(pattern: String): Builder = apply { excludes.add(pattern) }

        /** Wie [exclude], ohne Beachtung der Groß-/Kleinschreibung (`--iexclude`). */
        public fun caseInsensitiveExclude(pattern: String): Builder = apply { caseInsensitiveExcludes.add(pattern) }

        /** Liest Ausschlussmuster zeilenweise aus einer Datei (`--exclude-file`). */
        public fun excludeFile(file: Path): Builder = apply { excludeFiles.add(file) }

        /** Wie [excludeFile], ohne Beachtung der Groß-/Kleinschreibung (`--iexclude-file`). */
        public fun caseInsensitiveExcludeFile(file: Path): Builder = apply { caseInsensitiveExcludeFiles.add(file) }

        /** Entfernt die Original-Snapshots, nachdem die neuen angelegt wurden. */
        @JvmOverloads
        public fun forget(enabled: Boolean = true): Builder = apply { forget = enabled }

        /** Setzt einen neuen Hostnamen (`--new-host`). */
        public fun newHost(hostname: String): Builder = apply { newHost = hostname }

        /** Setzt einen neuen Zeitpunkt in lokaler Zeit (`--new-time`). */
        public fun newTime(time: LocalDateTime): Builder = apply { newTime = time }

        /** Legt einen Snapshot-Summary-Datensatz an, falls er fehlt. */
        @JvmOverloads
        public fun snapshotSummary(enabled: Boolean = true): Builder = apply { snapshotSummary = enabled }

        /** Token, über das sich der Aufruf von einem anderen Thread aus abbrechen lässt. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): RewriteOptions = RewriteOptions(this)
    }

    /** Einstieg in den [Builder]. */
    public companion object {
        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Ergebnis von [Restic.rewrite]. `restic rewrite` kennt kein JSON; [newSnapshotIds] sind die aus der Textausgabe
 * gelesenen Kurz-IDs.
 *
 * @property outputLines Die Textausgabe von restic.
 * @property newSnapshotIds Kurz-IDs der neu angelegten Snapshots.
 */
public data class RewriteResult(
    val outputLines: List<String>,
    val newSnapshotIds: List<String>,
)

/**
 * Optionen für [Restic.copy] (`restic copy`); die Quelle wird über die `from*`-Einstellungen angegeben, das Ziel ist
 * das Repository der [ResticConfiguration].
 *
 * @property filter Auswahl der Snapshots nach Host, Pfad und Tags.
 * @property snapshotIds Zu kopierende Snapshots; ohne Angabe alle.
 * @property fromRepository Quell-Repository als Connection-String (`--from-repo`).
 * @property fromRepositoryFile Datei mit dem Connection-String der Quelle (`--from-repository-file`).
 * @property fromPasswordFile Passwortdatei der Quelle (`--from-password-file`).
 * @property fromPasswordCommand Passwort-Kommando der Quelle (`--from-password-command`).
 * @property fromPassword Passwort der Quelle im Klartext, übergeben als `RESTIC_FROM_PASSWORD`.
 * @property fromKeyHint Schlüssel-ID, die bei der Quelle zuerst probiert wird (`--from-key-hint`).
 * @property fromInsecureNoPassword Quelle ohne Passwort (`--from-insecure-no-password`).
 * @property cancellationToken Token, über das sich der Aufruf abbrechen lässt.
 */
public class CopyOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val snapshotIds: List<String> = builder.snapshotIds.toList()
    public val fromRepository: String? = builder.fromRepository
    public val fromRepositoryFile: Path? = builder.fromRepositoryFile
    public val fromPasswordFile: Path? = builder.fromPasswordFile
    public val fromPasswordCommand: String? = builder.fromPasswordCommand
    public val fromPassword: String? = builder.fromPassword
    public val fromKeyHint: String? = builder.fromKeyHint
    public val fromInsecureNoPassword: Boolean = builder.fromInsecureNoPassword
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalValue("--from-repo", fromRepository)
            optionalValue("--from-repository-file", fromRepositoryFile)
            optionalValue("--from-password-file", fromPasswordFile)
            optionalValue("--from-password-command", fromPasswordCommand)
            optionalValue("--from-key-hint", fromKeyHint)
            optionalFlag("--from-insecure-no-password", fromInsecureNoPassword)
            addAll(snapshotIds)
        }

    internal fun environment(): Map<String, String> = fromPassword?.let { mapOf("RESTIC_FROM_PASSWORD" to it) }.orEmpty()

    /** Stellt [CopyOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    @Suppress("TooManyFunctions") // eine Methode je restic-Flag; das Builder-API bildet die CLI vollständig ab
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal var fromRepository: String? = null
        internal var fromRepositoryFile: Path? = null
        internal var fromPasswordFile: Path? = null
        internal var fromPasswordCommand: String? = null
        internal var fromPassword: String? = null
        internal var fromKeyHint: String? = null
        internal var fromInsecureNoPassword: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        /** Kopiert nur Snapshots dieses Hosts (`--host`); mehrfach möglich. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Kopiert nur Snapshots, die genau diesen Pfad gesichert haben (`--path`); mehrfach möglich. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Kopiert nur Snapshots, die alle diese Tags tragen (`--tag`); mehrere Aufrufe sind ODER-verknüpft. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Kopiert nur diesen Snapshot (mehrfach möglich); ohne Angabe werden alle kopiert. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Quell-Repository als Connection-String. */
        public fun fromRepository(repository: String): Builder = apply { fromRepository = repository }

        /** Datei mit dem Connection-String des Quell-Repositorys (`--from-repository-file`). */
        public fun fromRepositoryFile(file: Path): Builder = apply { fromRepositoryFile = file }

        /** Datei mit dem Passwort des Quell-Repositorys (`--from-password-file`). */
        public fun fromPasswordFile(file: Path): Builder = apply { fromPasswordFile = file }

        /** Kommando, das das Passwort des Quell-Repositorys ausgibt (`--from-password-command`). */
        public fun fromPasswordCommand(command: String): Builder = apply { fromPasswordCommand = command }

        /** Wird über `RESTIC_FROM_PASSWORD` übergeben. */
        public fun fromPassword(password: String): Builder = apply { fromPassword = password }

        /** Schlüssel-ID, die beim Quell-Repository zuerst probiert wird (`--from-key-hint`). */
        public fun fromKeyHint(keyId: String): Builder = apply { fromKeyHint = keyId }

        /** Quell-Repository ohne Passwort (`--from-insecure-no-password`). */
        @JvmOverloads
        public fun fromInsecureNoPassword(enabled: Boolean = true): Builder = apply { fromInsecureNoPassword = enabled }

        /** Token, über das sich der Aufruf von einem anderen Thread aus abbrechen lässt. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): CopyOptions = CopyOptions(this)
    }

    /** Einstieg in den [Builder]. */
    public companion object {
        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Ergebnis von [Restic.copy]. `restic copy` kennt kein JSON; [newSnapshotIds] sind die aus der Textausgabe gelesenen
 * Kurz-IDs im Ziel.
 *
 * @property outputLines Die Textausgabe von restic.
 * @property newSnapshotIds Kurz-IDs der im Ziel angelegten Snapshots.
 */
public data class CopyResult(
    val outputLines: List<String>,
    val newSnapshotIds: List<String>,
)

internal class RewriteAndCopyCommand(
    private val runner: ProcessRunner,
) {
    fun rewrite(
        options: RewriteOptions,
        listener: OutputLineListener?,
    ): RewriteResult {
        val request =
            ProcessRequest(
                listOf("rewrite") + options.arguments(),
                cancellation = options.cancellationToken,
                onStandardErrorLine = readableErrorLines(listener),
            )
        val lines = collectLines(request, listener)
        return RewriteResult(lines, idsMatching(SAVED_REWRITE_PATTERN, lines))
    }

    fun copy(
        options: CopyOptions,
        listener: OutputLineListener?,
    ): CopyResult {
        val request =
            ProcessRequest(
                listOf("copy") + options.arguments(),
                options.environment(),
                cancellation = options.cancellationToken,
                onStandardErrorLine = readableErrorLines(listener),
            )
        val lines = collectLines(request, listener)
        return CopyResult(lines, idsMatching(SAVED_SNAPSHOT_PATTERN, lines))
    }

    private fun collectLines(
        request: ProcessRequest,
        listener: OutputLineListener?,
    ): List<String> {
        val lines = mutableListOf<String>()
        runner.runChecked(request) { line ->
            lines += line
            listener?.onLine(line)
        }
        return lines
    }

    private fun idsMatching(
        pattern: Regex,
        lines: List<String>,
    ): List<String> = lines.mapNotNull { pattern.find(it)?.groupValues?.get(1) }
}
