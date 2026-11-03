package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.nio.file.Path

/** Events that `restic restore --json` reports while it runs; received through [RestoreListener]. */
public sealed interface RestoreEvent {
    /**
     * Progress, roughly once per second.
     *
     * @property percentDone Share from 0.0 to 1.0.
     * @property totalFiles Total number of files.
     * @property filesRestored Files restored so far.
     * @property filesSkipped Skipped files, for example because of [OverwriteBehavior].
     * @property totalBytes Total size.
     * @property bytesRestored Bytes restored so far.
     * @property bytesSkipped Skipped bytes.
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
     * Only with [RestoreOptions.Builder.verbosity] 2: what happened to a single file.
     *
     * @property action `restored`, `updated`, `unchanged` or `deleted`.
     * @property item Path of the file.
     * @property size Size in bytes.
     */
    public data class FileAction(
        val action: String,
        val item: String?,
        val size: Long?,
    ) : RestoreEvent

    /**
     * A file could not be restored; the run continues.
     *
     * @property item Affected path, if known.
     * @property during Step in which the error occurred.
     * @property message Error message from restic.
     */
    public data class Error(
        val item: String?,
        val during: String?,
        val message: String,
    ) : RestoreEvent {
        /** The message as one line `path: message`, the same form listeners and error messages of other commands show. */
        public fun readable(): String = readableError(item, message)
    }

    /**
     * End of the restore.
     *
     * @property summary The summary of the run.
     */
    public data class Summary(
        val summary: RestoreSummary,
    ) : RestoreEvent
}

/**
 * Summary of a restore.
 *
 * @property totalFiles Total number of files.
 * @property filesRestored Restored files.
 * @property filesSkipped Skipped files.
 * @property filesDeleted Files deleted with [RestoreOptions.Builder.delete].
 * @property totalBytes Total size.
 * @property bytesRestored Restored bytes.
 * @property bytesSkipped Skipped bytes.
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

/** Receives the events of a running [Restic.restore]; called on the thread that reads restic's output. */
public fun interface RestoreListener {
    /** A new event; should return quickly, otherwise restic's output backs up. */
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
