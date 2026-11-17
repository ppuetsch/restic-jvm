package io.github.ppuetsch.restic

import java.time.Duration

/**
 * Progress of a running [MaintenanceCommands.check], taken from restic's progress lines such as
 * `[0:12] 28.77%  353 / 1227 snapshots` or `[1:02:03] 4.10%  276 / 6727 packs`.
 *
 * restic checks in phases (`load indexes`, `check all packs`, `check snapshots, trees and blobs`, `read all data`);
 * the progress always refers to the current phase, so [percentDone] starts again at 0 in every phase.
 *
 * @property phase The last line restic printed before the progress, for example `read all data`.
 * @property elapsed Time since restic started the phase.
 * @property percentDone Share done in the current phase from 0.0 to 1.0; `null` while restic does not know the total yet.
 * @property done Units done in the current phase.
 * @property total Units in the current phase; `null` while restic is still counting.
 * @property unit What restic counts, for example `snapshots` or `packs`.
 */
public data class CheckProgress(
    val phase: String,
    val elapsed: Duration,
    val percentDone: Double?,
    val done: Long,
    val total: Long?,
    val unit: String,
)

/** Receives the progress of a running check; called on the thread that reads restic's output. */
public fun interface CheckProgressListener {
    /** A new progress line; should return quickly, otherwise restic's output backs up. */
    public fun onProgress(progress: CheckProgress)
}

private const val SECONDS_PER_MINUTE: Long = 60
private const val HOURS_GROUP: Int = 1
private const val MINUTES_GROUP: Int = 2
private const val SECONDS_GROUP: Int = 3
private const val PERCENT_GROUP: Int = 4
private const val DONE_GROUP: Int = 5
private const val TOTAL_GROUP: Int = 6
private const val UNIT_GROUP: Int = 7
private const val PERCENT: Double = 100.0
private const val REPOSITORY_CONTAINS_ERRORS: String = "Fatal: repository contains errors"
private val PROGRESS_LINE =
    Regex("""^\[(?:(\d+):)?(\d+):(\d+)]\s+(?:(\d+(?:\.\d+)?)%\s+)?(\d+)(?:\s*/\s*(\d+))?\s+(\S+).*$""")
private val DAMAGED_PACK_LINE = Regex("""^pack ([0-9a-f]{64}) contains""")
private val REPAIR_PACKS_LINE = Regex("""^restic repair packs ((?:[0-9a-f]{64}\s*)+)$""")

internal fun checkProgressFrom(
    line: String,
    phase: String,
): CheckProgress? {
    val groups = PROGRESS_LINE.matchEntire(line.trim())?.groupValues ?: return null
    val hours = groups[HOURS_GROUP].toLongOrNull() ?: 0
    val minutes = hours * SECONDS_PER_MINUTE + groups[MINUTES_GROUP].toLong()
    return CheckProgress(
        phase = phase,
        elapsed = Duration.ofSeconds(minutes * SECONDS_PER_MINUTE + groups[SECONDS_GROUP].toLong()),
        percentDone = groups[PERCENT_GROUP].toDoubleOrNull()?.div(PERCENT),
        done = groups[DONE_GROUP].toLong(),
        total = groups[TOTAL_GROUP].toLongOrNull(),
        unit = groups[UNIT_GROUP],
    )
}

/**
 * Collects the text output of `restic check` (without `--json`, the only mode in which restic reports progress).
 * Progress lines go to the [progressListener] only; every other line is a phase or a remark and goes to the result.
 */
internal class CheckTextCollector(
    private val progressListener: CheckProgressListener,
    private val listener: OutputLineListener?,
) {
    private val outputLines = mutableListOf<String>()
    private var phase = ""

    fun accept(line: String) {
        if (line.isBlank()) return
        val progress = checkProgressFrom(line, phase)
        if (progress != null) {
            progressListener.onProgress(progress)
            return
        }
        phase = line.trim()
        outputLines += phase
        listener?.onLine(phase)
    }

    /**
     * Exit code 1 with restic's closing line [REPOSITORY_CONTAINS_ERRORS] means a damaged repository and becomes a
     * result; every other failure (locked repository, wrong password, network) is an exception, as in JSON mode.
     */
    fun resultOrThrow(
        outcome: ProcessOutcome,
        errorLines: List<String>,
    ): CheckResult {
        val damaged = outcome.exitCode == 1 && errorLines.any { it.trim() == REPOSITORY_CONTAINS_ERRORS }
        if (outcome.exitCode != 0 && !damaged) throw commandExceptionFor(outcome.exitCode, outcome.standardError)
        val errorMessages =
            if (damaged) errorLines.map(String::trim).filter { it.isNotEmpty() && it != REPOSITORY_CONTAINS_ERRORS } else emptyList()
        val brokenPacks = brokenPacksIn(errorMessages)
        return CheckResult(
            errorCount = if (damaged) maxOf(1, brokenPacks.size).toLong() else 0,
            brokenPacks = brokenPacks,
            suggestRepairIndex = errorMessages.any { it.contains("repair index") },
            suggestPrune = errorMessages.any { it.contains("restic prune") },
            errorMessages = errorMessages,
            outputLines = outputLines.toList(),
            errorOutputLines = errorLines,
            messages = outputLines.map(CheckMessage::Text) + errorMessages.map(CheckMessage::Error),
        )
    }

    private fun brokenPacksIn(errorMessages: List<String>): List<String> =
        errorMessages
            .flatMap { message ->
                DAMAGED_PACK_LINE.find(message)?.let { listOf(it.groupValues[1]) }
                    ?: REPAIR_PACKS_LINE
                        .find(message)
                        ?.groupValues
                        ?.get(1)
                        ?.split(Regex("""\s+"""))
                        ?.filter(String::isNotEmpty)
                    ?: emptyList()
            }.distinct()
}
