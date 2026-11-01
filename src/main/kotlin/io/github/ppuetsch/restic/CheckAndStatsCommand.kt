package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/** Optionen für `restic check`. */
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

    public class Builder internal constructor() {
        internal var readData: Boolean = false
        internal var readDataSubset: String? = null
        internal var withCache: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        /** Liest alle Datenblobs und prüft ihre Prüfsummen. */
        @JvmOverloads
        public fun readData(enabled: Boolean = true): Builder = apply { readData = enabled }

        /** `n/t` für einen Teil, `x%` oder `x.y%` oder eine Größe in Bytes mit Suffix. */
        public fun readDataSubset(subset: String): Builder = apply { readDataSubset = subset }

        @JvmOverloads
        public fun withCache(enabled: Boolean = true): Builder = apply { withCache = enabled }

        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        public fun build(): CheckOptions = CheckOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: CheckOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Eine Meldung aus der JSON-Ausgabe von `restic check`. */
public sealed interface CheckMessage {
    public data class Summary(
        val errorCount: Long,
        val brokenPacks: List<String>,
        val suggestRepairIndex: Boolean,
        val suggestPrune: Boolean,
    ) : CheckMessage

    public data class Error(
        val message: String,
    ) : CheckMessage

    /** Eine Zeile, die kein JSON war. */
    public data class Text(
        val line: String,
    ) : CheckMessage

    /** Ein JSON-Objekt mit einem Meldungstyp, den die Library nicht kennt. */
    public data class Other(
        val messageType: String,
    ) : CheckMessage
}

/** Ergebnis von `check`: Ein Repository mit Fehlern führt nicht zu einer Exception, sondern zu [isHealthy] = false. */
public data class CheckResult
    @JvmOverloads
    constructor(
        val errorCount: Long,
        val brokenPacks: List<String>,
        val suggestRepairIndex: Boolean,
        val suggestPrune: Boolean,
        val errorMessages: List<String>,
        /** Die Standardausgabe in Lesbarkeit aufbereitet (nie rohes JSON), in der Reihenfolge, in der restic sie schrieb. */
        val outputLines: List<String> = emptyList(),
        /** Alle Zeilen der Fehlerausgabe. */
        val errorOutputLines: List<String> = emptyList(),
        /** Die Meldungen von restic als Typen, in Ausgabereihenfolge. */
        val messages: List<CheckMessage> = emptyList(),
    ) {
        public fun isHealthy(): Boolean = errorCount == 0L && errorMessages.isEmpty()
    }

public enum class StatsMode(
    internal val cliValue: String,
) {
    RESTORE_SIZE("restore-size"),
    FILES_BY_CONTENTS("files-by-contents"),
    BLOBS_PER_FILE("blobs-per-file"),
    RAW_DATA("raw-data"),
}

/** Optionen für `restic stats`. */
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

    public class Builder internal constructor() {
        internal var cancellationToken: CancellationToken? = null
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var mode: StatsMode? = null
        internal val snapshotIds: MutableList<String> = mutableListOf()

        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        public fun mode(mode: StatsMode): Builder = apply { this.mode = mode }

        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Bricht den Aufruf ab; ein direkt an [Restic] übergebenes Token hat Vorrang. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        public fun build(): StatsOptions = StatsOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: StatsOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Je nach [StatsMode] sind nur einige Felder gesetzt. */
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
