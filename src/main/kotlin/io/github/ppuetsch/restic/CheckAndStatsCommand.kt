package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/** A message from the JSON output of `restic check`. */
public sealed interface CheckMessage {
    /**
     * End of the check.
     *
     * @property errorCount Number of errors found.
     * @property brokenPacks IDs of damaged pack files.
     * @property suggestRepairIndex restic recommends [RepairCommands.index].
     * @property suggestPrune restic recommends [MaintenanceCommands.prune].
     */
    public data class Summary(
        val errorCount: Long,
        val brokenPacks: List<String>,
        val suggestRepairIndex: Boolean,
        val suggestPrune: Boolean,
    ) : CheckMessage

    /**
     * An error that was found.
     *
     * @property message Error message from restic.
     */
    public data class Error(
        val message: String,
    ) : CheckMessage

    /**
     * A line that was not JSON.
     *
     * @property line The line, unchanged.
     */
    public data class Text(
        val line: String,
    ) : CheckMessage

    /**
     * A JSON object with a message type the library does not know.
     *
     * @property messageType The value of `message_type`.
     */
    public data class Other(
        val messageType: String,
    ) : CheckMessage
}

/**
 * Result of [MaintenanceCommands.check]: a repository with errors does not cause an exception but [isHealthy] = `false`.
 *
 * @property errorCount Number of errors found according to the summary.
 * @property brokenPacks IDs of damaged pack files.
 * @property suggestRepairIndex restic recommends [RepairCommands.index].
 * @property suggestPrune restic recommends [MaintenanceCommands.prune].
 * @property errorMessages The error messages as plain text.
 * @property outputLines Standard output made readable (never raw JSON), in the order restic wrote it.
 * @property errorOutputLines All lines of the error output.
 * @property messages restic's messages as types, in output order.
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
        /** `true` if restic neither counted errors nor printed error messages. */
        public fun isHealthy(): Boolean = errorCount == 0L && errorMessages.isEmpty()
    }

/**
 * Result of [MaintenanceCommands.stats]; depending on the [StatsMode] only some fields are set.
 *
 * @property totalSize Total size in bytes.
 * @property totalFileCount Number of files; not with [StatsMode.RAW_DATA].
 * @property totalBlobCount Number of blobs; only with [StatsMode.RAW_DATA] and [StatsMode.BLOBS_PER_FILE].
 * @property snapshotsCount Number of snapshots considered.
 * @property totalUncompressedSize Size before compression; only with [StatsMode.RAW_DATA].
 * @property compressionRatio Compression factor; only with [StatsMode.RAW_DATA].
 * @property compressionProgress Share of already compressed data in percent; only with [StatsMode.RAW_DATA].
 * @property compressionSpaceSaving Space saved by compression in percent; only with [StatsMode.RAW_DATA].
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

    /** Returns the readable line for the parsed message so listeners never see raw JSON. */
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
