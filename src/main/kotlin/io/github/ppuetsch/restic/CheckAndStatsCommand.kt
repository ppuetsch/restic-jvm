package io.github.ppuetsch.restic

/** Optionen für `restic check`. */
public class CheckOptions private constructor(builder: Builder) {
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

/** Ergebnis von `check`: Ein Repository mit Fehlern führt nicht zu einer Exception, sondern zu [isHealthy] = false. */
public data class CheckResult(
    val errorCount: Long,
    val brokenPacks: List<String>,
    val suggestRepairIndex: Boolean,
    val suggestPrune: Boolean,
    val errorMessages: List<String>,
) {
    public fun isHealthy(): Boolean = errorCount == 0L && errorMessages.isEmpty()
}

public enum class StatsMode(internal val cliValue: String) {
    RESTORE_SIZE("restore-size"),
    FILES_BY_CONTENTS("files-by-contents"),
    BLOBS_PER_FILE("blobs-per-file"),
    RAW_DATA("raw-data"),
}

/** Optionen für `restic stats`. */
public class StatsOptions private constructor(builder: Builder) {
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
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var mode: StatsMode? = null
        internal val snapshotIds: MutableList<String> = mutableListOf()

        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        public fun mode(mode: StatsMode): Builder = apply { this.mode = mode }

        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

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

internal class CheckAndStatsCommand(private val runner: ProcessRunner) {
    fun check(options: CheckOptions): CheckResult {
        var summary: com.fasterxml.jackson.databind.JsonNode? = null
        val errorMessages = mutableListOf<String>()
        val request = ProcessRequest(listOf("check", "--json") + options.arguments(), cancellation = options.cancellationToken)
        val outcome =
            runner.run(request) { line ->
                if (line.isNotBlank()) {
                    val node = Json.parse(line)
                    when (node.messageType()) {
                        "summary" -> summary = node
                        "error" -> errorMessages += (node.get("error")?.get("message")?.asText() ?: node.text("message"))
                    }
                }
            }
        val checkSummary = summary
        if (checkSummary == null) throw commandExceptionFor(outcome.exitCode, outcome.standardError)
        return CheckResult(
            errorCount = checkSummary.long("num_errors"),
            brokenPacks = checkSummary.textList("broken_packs"),
            suggestRepairIndex = checkSummary.booleanOrNull("suggest_repair_index") ?: false,
            suggestPrune = checkSummary.booleanOrNull("suggest_prune") ?: false,
            errorMessages = errorMessages,
        )
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

internal class PruneCommand(private val runner: ProcessRunner) {
    fun prune(
        options: PruneOptions,
        listener: OutputLineListener?,
    ): PruneResult {
        val lines = mutableListOf<String>()
        val request = ProcessRequest(listOf("prune") + options.arguments(), cancellation = options.cancellationToken)
        runner.runChecked(request) { line ->
            lines += line
            listener?.onLine(line)
        }
        return PruneResult(lines)
    }
}
