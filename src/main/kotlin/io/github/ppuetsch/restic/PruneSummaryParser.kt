package io.github.ppuetsch.restic

import kotlin.math.roundToLong

private val VERBOSE_PACK_COUNT_LINE = Regex("""^\s*(to keep|to repack|to delete):\s+(\d+)\s+packs?\s*$""")
private val REMOVING_PACKS_LINE = Regex("""^\s*removing (\d+) old packs?\s*$""")
private val REPACK_PROGRESS_LINE = Regex("""(\d+) / \d+ packs repacked""")
private val TOTAL_PRUNE_LINE = Regex("""^\s*total prune:\s+\d+\s+blobs?\s*/\s*(\S+)\s+(\S+)\s*$""")

private const val KEEP_LABEL: String = "to keep"
private const val REPACK_LABEL: String = "to repack"
private const val DELETE_LABEL: String = "to delete"

private val BYTES_PER_UNIT: Map<String, Long> =
    mapOf(
        "B" to 1L,
        "KiB" to (1L shl 10),
        "MiB" to (1L shl 20),
        "GiB" to (1L shl 30),
        "TiB" to (1L shl 40),
    )

/**
 * Reads the figures from the text output of `restic prune` (tested with restic 0.18).
 *
 * Without `--verbose`, restic mentions packs only where it does something ("removing N old packs", progress line
 * "N / M packs repacked"). If these lines are missing although the summary ("total prune") is present, there was
 * nothing to do and the count is 0. Without a summary the values stay `null`.
 */
internal object PruneSummaryParser {
    fun parse(lines: List<String>): PruneResult {
        val bytesFreed = lines.firstNotNullOfOrNull(::freedBytesOrNull)
        val hasSummary = bytesFreed != null
        val verbose = lines.mapNotNull(::verbosePackCountOrNull).toMap()
        return PruneResult(
            outputLines = lines,
            packsToKeep = verbose[KEEP_LABEL],
            packsRepacked = verbose[REPACK_LABEL] ?: lastRepackProgress(lines) ?: zeroIf(hasSummary),
            packsDeleted = verbose[DELETE_LABEL] ?: removedPacks(lines) ?: zeroIf(hasSummary),
            bytesFreed = bytesFreed,
        )
    }

    private fun zeroIf(condition: Boolean): Int? = if (condition) 0 else null

    private fun verbosePackCountOrNull(line: String): Pair<String, Int>? =
        VERBOSE_PACK_COUNT_LINE.matchEntire(line)?.let { it.groupValues[1] to it.groupValues[2].toInt() }

    private fun removedPacks(lines: List<String>): Int? =
        lines.firstNotNullOfOrNull {
            REMOVING_PACKS_LINE
                .matchEntire(it)
                ?.groupValues
                ?.get(1)
                ?.toInt()
        }

    private fun lastRepackProgress(lines: List<String>): Int? =
        lines
            .mapNotNull {
                REPACK_PROGRESS_LINE
                    .find(it)
                    ?.groupValues
                    ?.get(1)
                    ?.toInt()
            }.lastOrNull()

    private fun freedBytesOrNull(line: String): Long? {
        val match = TOTAL_PRUNE_LINE.matchEntire(line) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull()
        val bytesPerUnit = BYTES_PER_UNIT[match.groupValues[2]]
        return if (amount == null || bytesPerUnit == null) null else (amount * bytesPerUnit).roundToLong()
    }
}
