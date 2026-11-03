package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val SAVED_SNAPSHOT_PATTERN = Regex("""snapshot (\w+) saved""")
private val SAVED_REWRITE_PATTERN = Regex("""saved new snapshot (\w+)""")

/**
 * Result of [ModifyCommands.rewrite]. `restic rewrite` has no JSON output; [newSnapshotIds] are the short IDs read from the
 * text output.
 *
 * @property outputLines restic's text output.
 * @property newSnapshotIds Short IDs of the newly created snapshots.
 */
public data class RewriteResult(
    val outputLines: List<String>,
    val newSnapshotIds: List<String>,
)

/**
 * Result of [ModifyCommands.copy]. `restic copy` has no JSON output; [newSnapshotIds] are the short IDs in the target, read
 * from the text output.
 *
 * @property outputLines restic's text output.
 * @property newSnapshotIds Short IDs of the snapshots created in the target.
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
