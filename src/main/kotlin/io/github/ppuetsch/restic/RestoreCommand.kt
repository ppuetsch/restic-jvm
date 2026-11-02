package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.nio.file.Path

/** Was [Restic.restore] mit bereits vorhandenen Dateien im Ziel tut (`--overwrite`). */
public enum class OverwriteBehavior(
    internal val cliValue: String,
) {
    /** Überschreibt immer; Standard von restic. */
    ALWAYS("always"),

    /** Überschreibt nur, wenn sich der Inhalt unterscheidet. */
    IF_CHANGED("if-changed"),

    /** Überschreibt nur, wenn die Datei im Snapshot neuer ist. */
    IF_NEWER("if-newer"),

    /** Überschreibt nie. */
    NEVER("never"),
}

/**
 * Optionen für [Restic.restore] (`restic restore`).
 *
 * @property filter Auswahl des Snapshots, wenn die ID `latest` ist.
 * @property delete Löscht Dateien im Ziel, die im Snapshot fehlen (`--delete`).
 * @property dryRun Nur zeigen, was wiederhergestellt würde (`--dry-run`).
 * @property excludes Ausschlussmuster (`--exclude`).
 * @property caseInsensitiveExcludes Ausschlussmuster ohne Groß-/Kleinschreibung (`--iexclude`).
 * @property excludeFiles Dateien mit Ausschlussmustern (`--exclude-file`).
 * @property caseInsensitiveExcludeFiles Dateien mit Ausschlussmustern ohne Groß-/Kleinschreibung (`--iexclude-file`).
 * @property excludeXattrs Nicht wiederherzustellende erweiterte Attribute (`--exclude-xattr`).
 * @property includes Einschlussmuster (`--include`).
 * @property caseInsensitiveIncludes Einschlussmuster ohne Groß-/Kleinschreibung (`--iinclude`).
 * @property includeFiles Dateien mit Einschlussmustern (`--include-file`).
 * @property caseInsensitiveIncludeFiles Dateien mit Einschlussmustern ohne Groß-/Kleinschreibung (`--iinclude-file`).
 * @property includeXattrs Wiederherzustellende erweiterte Attribute (`--include-xattr`).
 * @property overwrite Umgang mit vorhandenen Dateien (`--overwrite`).
 * @property sparse Stellt Dateien mit Lücken platzsparend wieder her (`--sparse`).
 * @property verify Prüft die wiederhergestellten Dateien (`--verify`).
 * @property verbosity Anzahl `--verbose`.
 * @property cancellationToken Token, über das sich der Aufruf abbrechen lässt.
 */
public class RestoreOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val delete: Boolean = builder.delete
    public val dryRun: Boolean = builder.dryRun
    public val excludes: List<String> = builder.excludes.toList()
    public val caseInsensitiveExcludes: List<String> = builder.caseInsensitiveExcludes.toList()
    public val excludeFiles: List<Path> = builder.excludeFiles.toList()
    public val caseInsensitiveExcludeFiles: List<Path> = builder.caseInsensitiveExcludeFiles.toList()
    public val excludeXattrs: List<String> = builder.excludeXattrs.toList()
    public val includes: List<String> = builder.includes.toList()
    public val caseInsensitiveIncludes: List<String> = builder.caseInsensitiveIncludes.toList()
    public val includeFiles: List<Path> = builder.includeFiles.toList()
    public val caseInsensitiveIncludeFiles: List<Path> = builder.caseInsensitiveIncludeFiles.toList()
    public val includeXattrs: List<String> = builder.includeXattrs.toList()
    public val overwrite: OverwriteBehavior? = builder.overwrite
    public val sparse: Boolean = builder.sparse
    public val verify: Boolean = builder.verify
    public val verbosity: Int = builder.verbosity
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalFlag("--delete", delete)
            optionalFlag("--dry-run", dryRun)
            excludes.forEach { optionalValue("--exclude", it) }
            caseInsensitiveExcludes.forEach { optionalValue("--iexclude", it) }
            excludeFiles.forEach { optionalValue("--exclude-file", it) }
            caseInsensitiveExcludeFiles.forEach { optionalValue("--iexclude-file", it) }
            excludeXattrs.forEach { optionalValue("--exclude-xattr", it) }
            includes.forEach { optionalValue("--include", it) }
            caseInsensitiveIncludes.forEach { optionalValue("--iinclude", it) }
            includeFiles.forEach { optionalValue("--include-file", it) }
            caseInsensitiveIncludeFiles.forEach { optionalValue("--iinclude-file", it) }
            includeXattrs.forEach { optionalValue("--include-xattr", it) }
            optionalValue("--overwrite", overwrite?.cliValue)
            optionalFlag("--sparse", sparse)
            optionalFlag("--verify", verify)
            repeat(verbosity) { add("--verbose") }
        }

    /** Stellt [RestoreOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    @Suppress("TooManyFunctions") // eine Methode je restic-Flag; das Builder-API bildet die CLI vollständig ab
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var delete: Boolean = false
        internal var dryRun: Boolean = false
        internal val excludes: MutableList<String> = mutableListOf()
        internal val caseInsensitiveExcludes: MutableList<String> = mutableListOf()
        internal val excludeFiles: MutableList<Path> = mutableListOf()
        internal val caseInsensitiveExcludeFiles: MutableList<Path> = mutableListOf()
        internal val excludeXattrs: MutableList<String> = mutableListOf()
        internal val includes: MutableList<String> = mutableListOf()
        internal val caseInsensitiveIncludes: MutableList<String> = mutableListOf()
        internal val includeFiles: MutableList<Path> = mutableListOf()
        internal val caseInsensitiveIncludeFiles: MutableList<Path> = mutableListOf()
        internal val includeXattrs: MutableList<String> = mutableListOf()
        internal var overwrite: OverwriteBehavior? = null
        internal var sparse: Boolean = false
        internal var verify: Boolean = false
        internal var verbosity: Int = 0
        internal var cancellationToken: CancellationToken? = null

        /** Wählt den neuesten Snapshot dieses Hosts (`--host`); wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Wählt den neuesten Snapshot mit diesem Pfad (`--path`); wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Wählt den neuesten Snapshot mit diesen Tags (`--tag`); wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Löscht Dateien im Ziel, die im Snapshot nicht existieren (`--delete`). */
        @JvmOverloads
        public fun delete(enabled: Boolean = true): Builder = apply { delete = enabled }

        /** Zeigt nur, was wiederhergestellt würde (`--dry-run`); sinnvoll mit [verbosity] 2. */
        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /** Stellt passende Pfade nicht wieder her (`--exclude`); mehrfach möglich. */
        public fun exclude(pattern: String): Builder = apply { excludes.add(pattern) }

        /** Wie [exclude], ohne Beachtung der Groß-/Kleinschreibung (`--iexclude`). */
        public fun caseInsensitiveExclude(pattern: String): Builder = apply { caseInsensitiveExcludes.add(pattern) }

        /** Liest Ausschlussmuster zeilenweise aus einer Datei (`--exclude-file`). */
        public fun excludeFile(file: Path): Builder = apply { excludeFiles.add(file) }

        /** Wie [excludeFile], ohne Beachtung der Groß-/Kleinschreibung (`--iexclude-file`). */
        public fun caseInsensitiveExcludeFile(file: Path): Builder = apply { caseInsensitiveExcludeFiles.add(file) }

        /** Stellt passende erweiterte Attribute nicht wieder her, z.B. `user.*` (`--exclude-xattr`). */
        public fun excludeXattr(pattern: String): Builder = apply { excludeXattrs.add(pattern) }

        /** Stellt nur passende Pfade wieder her (`--include`); mehrfach möglich. */
        public fun include(pattern: String): Builder = apply { includes.add(pattern) }

        /** Wie [include], ohne Beachtung der Groß-/Kleinschreibung (`--iinclude`). */
        public fun caseInsensitiveInclude(pattern: String): Builder = apply { caseInsensitiveIncludes.add(pattern) }

        /** Liest Einschlussmuster zeilenweise aus einer Datei (`--include-file`). */
        public fun includeFile(file: Path): Builder = apply { includeFiles.add(file) }

        /** Wie [includeFile], ohne Beachtung der Groß-/Kleinschreibung (`--iinclude-file`). */
        public fun caseInsensitiveIncludeFile(file: Path): Builder = apply { caseInsensitiveIncludeFiles.add(file) }

        /** Stellt nur passende erweiterte Attribute wieder her (`--include-xattr`). */
        public fun includeXattr(pattern: String): Builder = apply { includeXattrs.add(pattern) }

        /** Umgang mit Dateien, die im Ziel schon existieren (`--overwrite`). */
        public fun overwrite(behavior: OverwriteBehavior): Builder = apply { overwrite = behavior }

        /** Legt Bereiche aus Nullbytes als Lücken an, statt sie zu schreiben (`--sparse`). */
        @JvmOverloads
        public fun sparse(enabled: Boolean = true): Builder = apply { sparse = enabled }

        /** Liest die wiederhergestellten Dateien zurück und vergleicht den Inhalt. */
        @JvmOverloads
        public fun verify(enabled: Boolean = true): Builder = apply { verify = enabled }

        /** 2 liefert [RestoreEvent.FileAction] pro Datei. */
        public fun verbosity(level: Int): Builder = apply { verbosity = level }

        /** Token, über das sich der Aufruf von einem anderen Thread aus abbrechen lässt. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): RestoreOptions = RestoreOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: stellt den ganzen Snapshot wieder her. */
        @JvmField
        public val DEFAULT: RestoreOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Ereignisse, die `restic restore --json` während des Laufs meldet; empfangen über [RestoreListener]. */
public sealed interface RestoreEvent {
    /**
     * Fortschritt, etwa einmal pro Sekunde.
     *
     * @property percentDone Anteil von 0.0 bis 1.0.
     * @property totalFiles Gesamtzahl der Dateien.
     * @property filesRestored Bereits wiederhergestellte Dateien.
     * @property filesSkipped Übersprungene Dateien, etwa wegen [OverwriteBehavior].
     * @property totalBytes Gesamtgröße.
     * @property bytesRestored Bereits wiederhergestellte Bytes.
     * @property bytesSkipped Übersprungene Bytes.
     */
    public data class Status(
        val percentDone: Double,
        val totalFiles: Long,
        val filesRestored: Long,
        val filesSkipped: Long,
        val totalBytes: Long,
        val bytesRestored: Long,
        val bytesSkipped: Long,
    ) : RestoreEvent

    /**
     * Nur mit [RestoreOptions.Builder.verbosity] 2: was mit einer einzelnen Datei passiert ist.
     *
     * @property action `restored`, `updated`, `unchanged` oder `deleted`.
     * @property item Pfad der Datei.
     * @property size Größe in Bytes.
     */
    public data class FileAction(
        val action: String,
        val item: String?,
        val size: Long?,
    ) : RestoreEvent

    /**
     * Eine Datei konnte nicht wiederhergestellt werden; der Lauf geht weiter.
     *
     * @property item Betroffener Pfad, falls bekannt.
     * @property during Arbeitsschritt, in dem der Fehler auftrat.
     * @property message Fehlermeldung von restic.
     */
    public data class Error(
        val item: String?,
        val during: String?,
        val message: String,
    ) : RestoreEvent {
        /** Die Meldung als eine Zeile `pfad: meldung`, wie sie auch Listener und Fehlermeldungen anderer Befehle zeigen. */
        public fun readable(): String = readableError(item, message)
    }

    /**
     * Abschluss der Wiederherstellung.
     *
     * @property summary Die Zusammenfassung des Laufs.
     */
    public data class Summary(
        val summary: RestoreSummary,
    ) : RestoreEvent
}

/**
 * Zusammenfassung einer Wiederherstellung.
 *
 * @property totalFiles Gesamtzahl der Dateien.
 * @property filesRestored Wiederhergestellte Dateien.
 * @property filesSkipped Übersprungene Dateien.
 * @property filesDeleted Mit [RestoreOptions.Builder.delete] gelöschte Dateien.
 * @property totalBytes Gesamtgröße.
 * @property bytesRestored Wiederhergestellte Bytes.
 * @property bytesSkipped Übersprungene Bytes.
 */
public data class RestoreSummary(
    val totalFiles: Long,
    val filesRestored: Long,
    val filesSkipped: Long,
    val filesDeleted: Long,
    val totalBytes: Long,
    val bytesRestored: Long,
    val bytesSkipped: Long,
)

/** Empfängt die Ereignisse eines laufenden [Restic.restore]; wird im Thread aufgerufen, der die restic-Ausgabe liest. */
public fun interface RestoreListener {
    /** Ein neues Ereignis; sollte schnell zurückkehren, sonst staut sich die Ausgabe von restic. */
    public fun onEvent(event: RestoreEvent)
}

private fun restoreEventFrom(node: JsonNode): RestoreEvent? =
    when (node.messageType()) {
        "status" -> {
            RestoreEvent.Status(
                percentDone = node.doubleOrNull("percent_done") ?: 0.0,
                totalFiles = node.long("total_files"),
                filesRestored = node.long("files_restored"),
                filesSkipped = node.long("files_skipped"),
                totalBytes = node.long("total_bytes"),
                bytesRestored = node.long("bytes_restored"),
                bytesSkipped = node.long("bytes_skipped"),
            )
        }

        "verbose_status" -> {
            RestoreEvent.FileAction(node.text("action"), node.textOrNull("item"), node.longOrNull("size"))
        }

        "error" -> {
            RestoreEvent.Error(
                node.get("error")?.get("item")?.asString() ?: node.textOrNull("item"),
                node.textOrNull("during"),
                node.get("error")?.get("message")?.asString() ?: node.text("message"),
            )
        }

        "summary" -> {
            RestoreEvent.Summary(
                RestoreSummary(
                    totalFiles = node.long("total_files"),
                    filesRestored = node.long("files_restored"),
                    filesSkipped = node.long("files_skipped"),
                    filesDeleted = node.long("files_deleted"),
                    totalBytes = node.long("total_bytes"),
                    bytesRestored = node.long("bytes_restored"),
                    bytesSkipped = node.long("bytes_skipped"),
                ),
            )
        }

        else -> {
            null
        }
    }

internal class RestoreCommand(
    private val runner: ProcessRunner,
) {
    fun restore(
        snapshotId: String,
        target: Path,
        options: RestoreOptions,
        listener: RestoreListener?,
    ): RestoreSummary {
        var summary: RestoreSummary? = null
        val arguments = listOf("restore", "--json", "--target", target.toString()) + options.arguments() + snapshotId
        runner.runChecked(ProcessRequest(arguments, cancellation = options.cancellationToken)) { line ->
            Json.parseOrNull(line)?.let(::restoreEventFrom)?.let { event ->
                if (event is RestoreEvent.Summary) summary = event.summary
                listener?.onEvent(event)
            }
        }
        return summary ?: throw ResticOutputException("restic restore produced no summary", null)
    }
}
