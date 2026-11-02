package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/**
 * Optionen für [Restic.check] (`restic check`).
 *
 * @property readData Liest und prüft alle Daten-Blobs (`--read-data`).
 * @property readDataSubset Liest und prüft nur einen Teil der Daten (`--read-data-subset`).
 * @property withCache Nutzt den lokalen Cache statt eines frischen (`--with-cache`).
 * @property cancellationToken Token, über das sich der Aufruf abbrechen lässt.
 */
public class CheckOptions private constructor(
    builder: Builder,
) {
    public val readData: Boolean = builder.readData
    public val readDataSubset: String? = builder.readDataSubset
    public val withCache: Boolean = builder.withCache
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            optionalFlag("--read-data", readData)
            optionalValue("--read-data-subset", readDataSubset)
            optionalFlag("--with-cache", withCache)
        }

    /** Stellt [CheckOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal var readData: Boolean = false
        internal var readDataSubset: String? = null
        internal var withCache: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        /** Liest alle Daten-Blobs und prüft ihre Prüfsummen (`--read-data`); lädt das ganze Repository herunter. */
        @JvmOverloads
        public fun readData(enabled: Boolean = true): Builder = apply { readData = enabled }

        /**
         * Liest und prüft nur einen Teil der Daten (`--read-data-subset`): `n/t` für den n-ten von t Teilen,
         * `x%` oder `x.y%` für einen zufälligen Anteil oder eine Größe mit Suffix wie `500M`.
         */
        public fun readDataSubset(subset: String): Builder = apply { readDataSubset = subset }

        /** Nutzt den vorhandenen lokalen Cache, statt in einem frischen zu prüfen (`--with-cache`); schneller, aber weniger gründlich. */
        @JvmOverloads
        public fun withCache(enabled: Boolean = true): Builder = apply { withCache = enabled }

        /** Token, über das sich der Aufruf von einem anderen Thread aus abbrechen lässt. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): CheckOptions = CheckOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: restic-Standardverhalten. */
        @JvmField
        public val DEFAULT: CheckOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Eine Meldung aus der JSON-Ausgabe von `restic check`. */
public sealed interface CheckMessage {
    /**
     * Abschluss der Prüfung.
     *
     * @property errorCount Anzahl gefundener Fehler.
     * @property brokenPacks IDs beschädigter Pack-Dateien.
     * @property suggestRepairIndex restic empfiehlt [Restic.repairIndex].
     * @property suggestPrune restic empfiehlt [Restic.prune].
     */
    public data class Summary(
        val errorCount: Long,
        val brokenPacks: List<String>,
        val suggestRepairIndex: Boolean,
        val suggestPrune: Boolean,
    ) : CheckMessage

    /**
     * Ein gefundener Fehler.
     *
     * @property message Fehlermeldung von restic.
     */
    public data class Error(
        val message: String,
    ) : CheckMessage

    /**
     * Eine Zeile, die kein JSON war.
     *
     * @property line Die Zeile unverändert.
     */
    public data class Text(
        val line: String,
    ) : CheckMessage

    /**
     * Ein JSON-Objekt mit einem Meldungstyp, den die Library nicht kennt.
     *
     * @property messageType Der Wert von `message_type`.
     */
    public data class Other(
        val messageType: String,
    ) : CheckMessage
}

/**
 * Ergebnis von [Restic.check]: Ein Repository mit Fehlern führt nicht zu einer Exception, sondern zu [isHealthy] = `false`.
 *
 * @property errorCount Anzahl gefundener Fehler laut Zusammenfassung.
 * @property brokenPacks IDs beschädigter Pack-Dateien.
 * @property suggestRepairIndex restic empfiehlt [Restic.repairIndex].
 * @property suggestPrune restic empfiehlt [Restic.prune].
 * @property errorMessages Die Fehlermeldungen im Klartext.
 * @property outputLines Die Standardausgabe in Lesbarkeit aufbereitet (nie rohes JSON), in der Reihenfolge, in der restic sie schrieb.
 * @property errorOutputLines Alle Zeilen der Fehlerausgabe.
 * @property messages Die Meldungen von restic als Typen, in Ausgabereihenfolge.
 */
public data class CheckResult
    @JvmOverloads
    constructor(
        val errorCount: Long,
        val brokenPacks: List<String>,
        val suggestRepairIndex: Boolean,
        val suggestPrune: Boolean,
        val errorMessages: List<String>,
        val outputLines: List<String> = emptyList(),
        val errorOutputLines: List<String> = emptyList(),
        val messages: List<CheckMessage> = emptyList(),
    ) {
        /** `true`, wenn restic weder Fehler gezählt noch Fehlermeldungen ausgegeben hat. */
        public fun isHealthy(): Boolean = errorCount == 0L && errorMessages.isEmpty()
    }

/** Was `restic stats` zählt (`--mode`). */
public enum class StatsMode(
    internal val cliValue: String,
) {
    /** Größe, die eine Wiederherstellung belegen würde; Standard von restic. */
    RESTORE_SIZE("restore-size"),

    /** Größe der Dateien, wobei inhaltsgleiche Dateien nur einmal zählen. */
    FILES_BY_CONTENTS("files-by-contents"),

    /** Größe der Blobs, die die Dateien referenzieren. */
    BLOBS_PER_FILE("blobs-per-file"),

    /** Tatsächlich belegter Speicher im Repository, mit Kompressionsangaben. */
    RAW_DATA("raw-data"),
}

/**
 * Optionen für [Restic.stats] (`restic stats`).
 *
 * @property cancellationToken Token, über das sich der Aufruf abbrechen lässt.
 * @property filter Auswahl der Snapshots nach Host, Pfad und Tags.
 * @property mode Was gezählt wird (`--mode`).
 * @property snapshotIds Ausgewählte Snapshots; ohne Angabe alle.
 */
public class StatsOptions private constructor(
    builder: Builder,
) {
    public val cancellationToken: CancellationToken? = builder.cancellationToken
    public val filter: SnapshotFilter = builder.filter.build()
    public val mode: StatsMode? = builder.mode
    public val snapshotIds: List<String> = builder.snapshotIds.toList()

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalValue("--mode", mode?.cliValue)
            addAll(snapshotIds)
        }

    /** Stellt [StatsOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal var cancellationToken: CancellationToken? = null
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var mode: StatsMode? = null
        internal val snapshotIds: MutableList<String> = mutableListOf()

        /** Wählt nur Snapshots dieses Hosts (`--host`); mehrfach möglich. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Wählt nur Snapshots, die genau diesen Pfad gesichert haben (`--path`); mehrfach möglich. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Wählt nur Snapshots, die alle diese Tags tragen (`--tag`); mehrere Aufrufe sind ODER-verknüpft. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Was gezählt wird (`--mode`); Standard ist [StatsMode.RESTORE_SIZE]. */
        public fun mode(mode: StatsMode): Builder = apply { this.mode = mode }

        /** Beschränkt den Befehl auf diesen Snapshot (ID, Kurz-ID oder `latest`); mehrfach möglich. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Bricht den Aufruf ab; ein direkt an [Restic] übergebenes Token hat Vorrang. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): StatsOptions = StatsOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: restic-Standardverhalten. */
        @JvmField
        public val DEFAULT: StatsOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Ergebnis von [Restic.stats]; je nach [StatsMode] sind nur einige Felder gesetzt.
 *
 * @property totalSize Gesamtgröße in Bytes.
 * @property totalFileCount Anzahl der Dateien; nicht bei [StatsMode.RAW_DATA].
 * @property totalBlobCount Anzahl der Blobs; nur bei [StatsMode.RAW_DATA] und [StatsMode.BLOBS_PER_FILE].
 * @property snapshotsCount Anzahl der betrachteten Snapshots.
 * @property totalUncompressedSize Größe vor Kompression; nur bei [StatsMode.RAW_DATA].
 * @property compressionRatio Kompressionsfaktor; nur bei [StatsMode.RAW_DATA].
 * @property compressionProgress Anteil bereits komprimierter Daten in Prozent; nur bei [StatsMode.RAW_DATA].
 * @property compressionSpaceSaving Durch Kompression gesparter Platz in Prozent; nur bei [StatsMode.RAW_DATA].
 */
public data class StatsResult(
    val totalSize: Long,
    val totalFileCount: Long?,
    val totalBlobCount: Long?,
    val snapshotsCount: Long,
    val totalUncompressedSize: Long?,
    val compressionRatio: Double?,
    val compressionProgress: Double?,
    val compressionSpaceSaving: Double?,
)

private class CheckCollector {
    private var summary: CheckMessage.Summary? = null
    private val messages = mutableListOf<CheckMessage>()
    private val readableLines = mutableListOf<String>()

    /** Liefert die lesbare Zeile zur gelesenen Meldung, damit Listener nie rohes JSON sehen. */
    fun accept(line: String): String {
        val message = checkMessageFrom(line)
        messages += message
        if (message is CheckMessage.Summary) summary = message
        return message.readableLine().also(readableLines::add)
    }

    fun resultOrThrow(
        outcome: ProcessOutcome,
        errorLines: List<String>,
    ): CheckResult {
        val checkSummary = summary ?: throw commandExceptionFor(outcome.exitCode, outcome.standardError)
        return CheckResult(
            errorCount = checkSummary.errorCount,
            brokenPacks = checkSummary.brokenPacks,
            suggestRepairIndex = checkSummary.suggestRepairIndex,
            suggestPrune = checkSummary.suggestPrune,
            errorMessages = messages.filterIsInstance<CheckMessage.Error>().map(CheckMessage.Error::message),
            outputLines = readableLines,
            errorOutputLines = errorLines,
            messages = messages,
        )
    }
}

private fun checkMessageFrom(line: String): CheckMessage {
    val node = Json.parseOrNull(line)?.takeIf(JsonNode::isObject) ?: return CheckMessage.Text(line)
    return when (val type = node.messageType()) {
        "summary" -> {
            CheckMessage.Summary(
                errorCount = node.long("num_errors"),
                brokenPacks = node.textList("broken_packs"),
                suggestRepairIndex = node.booleanOrNull("suggest_repair_index") ?: false,
                suggestPrune = node.booleanOrNull("suggest_prune") ?: false,
            )
        }

        "error" -> {
            CheckMessage.Error(node.get("error")?.get("message")?.asString() ?: node.text("message"))
        }

        else -> {
            CheckMessage.Other(type.orEmpty())
        }
    }
}

private fun CheckMessage.readableLine(): String =
    when (this) {
        is CheckMessage.Summary -> "check finished: $errorCount errors, ${brokenPacks.size} broken packs"
        is CheckMessage.Error -> "error: $message"
        is CheckMessage.Text -> line
        is CheckMessage.Other -> "check message: $messageType"
    }

internal class CheckAndStatsCommand(
    private val runner: ProcessRunner,
) {
    fun check(
        options: CheckOptions,
        listener: OutputLineListener?,
    ): CheckResult {
        val collector = CheckCollector()
        val errorLines = mutableListOf<String>()
        val request =
            ProcessRequest(
                listOf("check", "--json") + options.arguments(),
                cancellation = options.cancellationToken,
                onStandardErrorLine = readableErrorLines(listener, errorLines),
            )
        val outcome =
            runner.run(request) { line ->
                if (line.isNotBlank()) {
                    val readableLine = collector.accept(line)
                    listener?.onLine(readableLine)
                }
            }
        return collector.resultOrThrow(outcome, errorLines)
    }

    fun stats(options: StatsOptions): StatsResult {
        var lastLine = ""
        runner.runChecked(listOf("stats", "--json") + options.arguments()) { line -> if (line.isNotBlank()) lastLine = line }
        val node = Json.parse(lastLine)
        return StatsResult(
            totalSize = node.long("total_size"),
            totalFileCount = node.longOrNull("total_file_count"),
            totalBlobCount = node.longOrNull("total_blob_count"),
            snapshotsCount = node.long("snapshots_count"),
            totalUncompressedSize = node.longOrNull("total_uncompressed_size"),
            compressionRatio = node.doubleOrNull("compression_ratio"),
            compressionProgress = node.doubleOrNull("compression_progress"),
            compressionSpaceSaving = node.doubleOrNull("compression_space_saving"),
        )
    }
}

internal class PruneCommand(
    private val runner: ProcessRunner,
) {
    fun prune(
        options: PruneOptions,
        listener: OutputLineListener?,
    ): PruneResult {
        val lines = mutableListOf<String>()
        val request =
            ProcessRequest(
                listOf("prune") + options.arguments(),
                cancellation = options.cancellationToken,
                onStandardErrorLine = readableErrorLines(listener),
            )
        runner.runChecked(request) { line ->
            lines += line
            listener?.onLine(line)
        }
        return PruneSummaryParser.parse(lines)
    }
}
