package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Duration
import java.time.Instant

/** Ereignisse, die `restic backup --json` während des Laufs meldet. */
public sealed interface BackupEvent {
    public data class Status(
        val percentDone: Double,
        val totalFiles: Long,
        val filesDone: Long,
        val totalBytes: Long,
        val bytesDone: Long,
        val errorCount: Long,
        val currentFiles: List<String>,
    ) : BackupEvent

    /** Nur mit `verbose`: was mit einer einzelnen Datei oder Verzeichnis passiert ist. */
    public data class FileAction(
        val action: String,
        val item: String?,
        val dataSize: Long,
        val dataSizeInRepository: Long,
    ) : BackupEvent

    public data class Error(
        val item: String?,
        val during: String?,
        val message: String,
    ) : BackupEvent {
        /** Die Meldung als eine Zeile `pfad: meldung`, wie sie auch Listener und Fehlermeldungen anderer Befehle zeigen. */
        public fun readable(): String = readableError(item, message)
    }

    public data class Summary(
        val summary: BackupSummary,
    ) : BackupEvent
}

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
    /** `null` bei `skipIfUnchanged`, wenn kein neuer Snapshot entstand. */
    val snapshotId: String?,
    val dryRun: Boolean,
)

/** Ergebnis eines Backups. Bei [hasUnreadableSources] wurde trotzdem ein Snapshot angelegt (restic-Exit-Code 3). */
public data class BackupResult(
    val summary: BackupSummary,
    val errors: List<BackupEvent.Error>,
    val hasUnreadableSources: Boolean,
)

public fun interface BackupListener {
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
