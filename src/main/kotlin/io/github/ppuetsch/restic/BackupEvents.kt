package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Duration
import java.time.Instant

/** Events that `restic backup --json` reports while it runs; received through [BackupListener]. */
public sealed interface BackupEvent {
    /**
     * Progress, roughly once per second.
     *
     * @property percentDone Share from 0.0 to 1.0; only rough without the initial scan (`noScan`).
     * @property totalFiles Total number of files according to the scan.
     * @property filesDone Files processed so far.
     * @property totalBytes Total size according to the scan.
     * @property bytesDone Bytes processed so far.
     * @property errorCount Errors so far, see [Error].
     * @property currentFiles Files being read right now.
     */
    public data class Status(
        val percentDone: Double,
        val totalFiles: Long,
        val filesDone: Long,
        val totalBytes: Long,
        val bytesDone: Long,
        val errorCount: Long,
        val currentFiles: List<String>,
    ) : BackupEvent

    /**
     * Only with [BackupOptions.Builder.verbose]: what happened to a single file or directory.
     *
     * @property action `new`, `unchanged`, `modified` or `scan_finished`.
     * @property item Path of the file or directory.
     * @property dataSize Size of the file.
     * @property dataSizeInRepository Bytes of it newly written to the repository.
     */
    public data class FileAction(
        val action: String,
        val item: String?,
        val dataSize: Long,
        val dataSizeInRepository: Long,
    ) : BackupEvent

    /**
     * A file or directory could not be backed up; the backup continues.
     *
     * @property item Affected path, if known.
     * @property during Step in which the error occurred, for example `scan` or `archival`.
     * @property message Error message from restic.
     */
    public data class Error(
        val item: String?,
        val during: String?,
        val message: String,
    ) : BackupEvent {
        /** The message as one line `path: message`, the same form listeners and error messages of other commands show. */
        public fun readable(): String = readableError(item, message)
    }

    /**
     * End of the backup; the numbers are also in [BackupResult.summary].
     *
     * @property summary The summary of the run.
     */
    public data class Summary(
        val summary: BackupSummary,
    ) : BackupEvent
}

/**
 * Summary of a backup.
 *
 * @property filesNew New files.
 * @property filesChanged Changed files.
 * @property filesUnmodified Unchanged files.
 * @property directoriesNew New directories.
 * @property directoriesChanged Changed directories.
 * @property directoriesUnmodified Unchanged directories.
 * @property dataBlobs Newly written data blobs.
 * @property treeBlobs Newly written tree blobs.
 * @property dataAdded Newly added bytes before compression.
 * @property dataAddedPacked Newly added bytes after compression, as stored in the repository.
 * @property totalFilesProcessed All processed files.
 * @property totalBytesProcessed Size of all processed files.
 * @property duration Duration of the backup.
 * @property backupStart Start time; missing with restic before 0.17.
 * @property backupEnd End time; missing with restic before 0.17.
 * @property snapshotId ID of the new snapshot; `null` with `skipIfUnchanged` when no new snapshot was created.
 * @property dryRun `true` if the backup was only a dry run.
 */
public data class BackupSummary(
    val filesNew: Long,
    val filesChanged: Long,
    val filesUnmodified: Long,
    val directoriesNew: Long,
    val directoriesChanged: Long,
    val directoriesUnmodified: Long,
    val dataBlobs: Long,
    val treeBlobs: Long,
    val dataAdded: Long,
    val dataAddedPacked: Long,
    val totalFilesProcessed: Long,
    val totalBytesProcessed: Long,
    val duration: Duration,
    val backupStart: Instant?,
    val backupEnd: Instant?,
    val snapshotId: String?,
    val dryRun: Boolean,
)

/**
 * Result of a backup.
 *
 * @property summary The summary of the run.
 * @property errors All files that could not be backed up.
 * @property hasUnreadableSources `true` if some sources were unreadable (restic exit code 3); the snapshot was still
 *   created and contains everything readable.
 */
public data class BackupResult(
    val summary: BackupSummary,
    val errors: List<BackupEvent.Error>,
    val hasUnreadableSources: Boolean,
)

/** Receives the events of a running backup; called on the thread that reads restic's output. */
public fun interface BackupListener {
    /** A new event; should return quickly, otherwise restic's output backs up. */
    public fun onEvent(event: BackupEvent)
}

internal fun backupEventFrom(node: JsonNode): BackupEvent? =
    when (node.messageType()) {
        "status" -> statusFrom(node)
        "verbose_status" -> fileActionFrom(node)
        "error" -> errorFrom(node)
        "summary" -> BackupEvent.Summary(summaryFrom(node))
        else -> null
    }

private fun statusFrom(node: JsonNode) =
    BackupEvent.Status(
        percentDone = node.doubleOrNull("percent_done") ?: 0.0,
        totalFiles = node.long("total_files"),
        filesDone = node.long("files_done"),
        totalBytes = node.long("total_bytes"),
        bytesDone = node.long("bytes_done"),
        errorCount = node.long("error_count"),
        currentFiles = node.textList("current_files"),
    )

private fun fileActionFrom(node: JsonNode) =
    BackupEvent.FileAction(
        action = node.text("action"),
        item = node.textOrNull("item"),
        dataSize = node.long("data_size"),
        dataSizeInRepository = node.long("data_size_in_repo"),
    )

private fun errorFrom(node: JsonNode) =
    BackupEvent.Error(
        item = node.get("error")?.get("item")?.asString() ?: node.textOrNull("item"),
        during = node.textOrNull("during"),
        message = node.get("error")?.get("message")?.asString() ?: node.text("message"),
    )

private fun summaryFrom(node: JsonNode) =
    BackupSummary(
        filesNew = node.long("files_new"),
        filesChanged = node.long("files_changed"),
        filesUnmodified = node.long("files_unmodified"),
        directoriesNew = node.long("dirs_new"),
        directoriesChanged = node.long("dirs_changed"),
        directoriesUnmodified = node.long("dirs_unmodified"),
        dataBlobs = node.long("data_blobs"),
        treeBlobs = node.long("tree_blobs"),
        dataAdded = node.long("data_added"),
        dataAddedPacked = node.long("data_added_packed"),
        totalFilesProcessed = node.long("total_files_processed"),
        totalBytesProcessed = node.long("total_bytes_processed"),
        duration = durationFromSeconds(node.doubleOrNull("total_duration") ?: 0.0),
        backupStart = node.instantOrNull("backup_start"),
        backupEnd = node.instantOrNull("backup_end"),
        snapshotId = node.textOrNull("snapshot_id"),
        dryRun = node.booleanOrNull("dry_run") ?: false,
    )

private fun durationFromSeconds(seconds: Double): Duration =
    if (seconds <= 0.0) Duration.ZERO else Duration.ofNanos((seconds * NANOS_PER_SECOND).toLong())

private const val NANOS_PER_SECOND: Double = 1_000_000_000.0
