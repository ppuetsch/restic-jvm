package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Duration
import java.time.Instant

/** Ereignisse, die `restic backup --json` während des Laufs meldet; empfangen über [BackupListener]. */
public sealed interface BackupEvent {
    /**
     * Fortschritt, etwa einmal pro Sekunde.
     *
     * @property percentDone Anteil von 0.0 bis 1.0; ohne Vorab-Scan (`noScan`) nur grob.
     * @property totalFiles Gesamtzahl der Dateien laut Scan.
     * @property filesDone Bereits verarbeitete Dateien.
     * @property totalBytes Gesamtgröße laut Scan.
     * @property bytesDone Bereits verarbeitete Bytes.
     * @property errorCount Bisher aufgetretene Fehler, siehe [Error].
     * @property currentFiles Dateien, die gerade gelesen werden.
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
     * Nur mit [BackupOptions.Builder.verbose]: was mit einer einzelnen Datei oder einem Verzeichnis passiert ist.
     *
     * @property action `new`, `unchanged`, `modified` oder `scan_finished`.
     * @property item Pfad der Datei oder des Verzeichnisses.
     * @property dataSize Größe der Datei.
     * @property dataSizeInRepository Davon neu ins Repository geschriebene Bytes.
     */
    public data class FileAction(
        val action: String,
        val item: String?,
        val dataSize: Long,
        val dataSizeInRepository: Long,
    ) : BackupEvent

    /**
     * Eine Datei oder ein Verzeichnis konnte nicht gesichert werden; das Backup läuft weiter.
     *
     * @property item Betroffener Pfad, falls bekannt.
     * @property during Arbeitsschritt, in dem der Fehler auftrat, z.B. `scan` oder `archival`.
     * @property message Fehlermeldung von restic.
     */
    public data class Error(
        val item: String?,
        val during: String?,
        val message: String,
    ) : BackupEvent {
        /** Die Meldung als eine Zeile `pfad: meldung`, wie sie auch Listener und Fehlermeldungen anderer Befehle zeigen. */
        public fun readable(): String = readableError(item, message)
    }

    /**
     * Abschluss des Backups; die Zahlen stehen auch in [BackupResult.summary].
     *
     * @property summary Die Zusammenfassung des Laufs.
     */
    public data class Summary(
        val summary: BackupSummary,
    ) : BackupEvent
}

/**
 * Zusammenfassung eines Backups.
 *
 * @property filesNew Neu hinzugekommene Dateien.
 * @property filesChanged Geänderte Dateien.
 * @property filesUnmodified Unveränderte Dateien.
 * @property directoriesNew Neu hinzugekommene Verzeichnisse.
 * @property directoriesChanged Geänderte Verzeichnisse.
 * @property directoriesUnmodified Unveränderte Verzeichnisse.
 * @property dataBlobs Neu geschriebene Daten-Blobs.
 * @property treeBlobs Neu geschriebene Tree-Blobs.
 * @property dataAdded Neu hinzugefügte Bytes vor Kompression.
 * @property dataAddedPacked Neu hinzugefügte Bytes nach Kompression, wie sie im Repository landen.
 * @property totalFilesProcessed Alle verarbeiteten Dateien.
 * @property totalBytesProcessed Größe aller verarbeiteten Dateien.
 * @property duration Laufzeit des Backups.
 * @property backupStart Startzeitpunkt; fehlt bei restic vor 0.17.
 * @property backupEnd Endzeitpunkt; fehlt bei restic vor 0.17.
 * @property snapshotId ID des neuen Snapshots; `null` bei `skipIfUnchanged`, wenn kein neuer Snapshot entstand.
 * @property dryRun `true`, wenn nur probehalber gesichert wurde.
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
 * Ergebnis eines Backups.
 *
 * @property summary Die Zusammenfassung des Laufs.
 * @property errors Alle Dateien, die nicht gesichert werden konnten.
 * @property hasUnreadableSources `true`, wenn einzelne Quellen unlesbar waren (restic-Exit-Code 3); der Snapshot
 *   wurde trotzdem angelegt und enthält alles Lesbare.
 */
public data class BackupResult(
    val summary: BackupSummary,
    val errors: List<BackupEvent.Error>,
    val hasUnreadableSources: Boolean,
)

/** Empfängt die Ereignisse eines laufenden Backups; wird im Thread aufgerufen, der die restic-Ausgabe liest. */
public fun interface BackupListener {
    /** Ein neues Ereignis; sollte schnell zurückkehren, sonst staut sich die Ausgabe von restic. */
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
