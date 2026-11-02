package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * Die Zusammenfassung, die restic ab 0.17 mit jedem Snapshot speichert.
 *
 * @property backupStart Startzeitpunkt des Backups.
 * @property backupEnd Endzeitpunkt des Backups.
 * @property filesNew Neu hinzugekommene Dateien.
 * @property filesChanged Geänderte Dateien.
 * @property filesUnmodified Unveränderte Dateien.
 * @property directoriesNew Neu hinzugekommene Verzeichnisse.
 * @property directoriesChanged Geänderte Verzeichnisse.
 * @property directoriesUnmodified Unveränderte Verzeichnisse.
 * @property dataBlobs Neu geschriebene Daten-Blobs.
 * @property treeBlobs Neu geschriebene Tree-Blobs.
 * @property dataAdded Neu hinzugefügte Bytes vor Kompression.
 * @property dataAddedPacked Neu hinzugefügte Bytes nach Kompression.
 * @property totalFilesProcessed Alle verarbeiteten Dateien.
 * @property totalBytesProcessed Größe aller verarbeiteten Dateien.
 */
public data class SnapshotSummary(
    val backupStart: Instant?,
    val backupEnd: Instant?,
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
)

/**
 * Ein Snapshot: der Zustand der gesicherten Pfade zu einem Zeitpunkt.
 *
 * @property id Vollständige ID.
 * @property shortId Die ersten acht Zeichen der ID, wie restic sie anzeigt.
 * @property time Zeitpunkt des Backups.
 * @property parent ID des Eltern-Snapshots, gegen den verglichen wurde.
 * @property tree ID des Wurzelverzeichnisses.
 * @property paths Gesicherte Pfade.
 * @property hostname Host, auf dem gesichert wurde.
 * @property username Benutzer, der gesichert hat.
 * @property uid Benutzer-ID unter Unix.
 * @property gid Gruppen-ID unter Unix.
 * @property excludes Beim Backup verwendete Ausschlussmuster.
 * @property tags Tags des Snapshots.
 * @property programVersion restic-Version, die den Snapshot angelegt hat.
 * @property original Snapshot-ID des Originals, wenn dieser Snapshot per `copy` oder `rewrite` entstanden ist.
 * @property summary Zusammenfassung des Backups; fehlt bei Snapshots von restic vor 0.17.
 */
public data class Snapshot(
    val id: String,
    val shortId: String,
    val time: Instant?,
    val parent: String?,
    val tree: String,
    val paths: List<String>,
    val hostname: String,
    val username: String,
    val uid: Long?,
    val gid: Long?,
    val excludes: List<String>,
    val tags: List<String>,
    val programVersion: String?,
    val original: String?,
    val summary: SnapshotSummary?,
)

/**
 * Das gemeinsame Merkmal einer Snapshot-Gruppe; nur die Felder der Gruppierung sind gesetzt.
 *
 * @property hostname Host der Gruppe.
 * @property paths Pfade der Gruppe.
 * @property tags Tags der Gruppe.
 */
public data class SnapshotGroupKey(
    val hostname: String?,
    val paths: List<String>,
    val tags: List<String>,
)

/**
 * Eine Gruppe von Snapshots aus [Restic.snapshotGroups].
 *
 * @property key Das gemeinsame Merkmal.
 * @property snapshots Die Snapshots der Gruppe.
 */
public data class SnapshotGroup(
    val key: SnapshotGroupKey,
    val snapshots: List<Snapshot>,
)

internal fun snapshotFrom(node: JsonNode): Snapshot =
    Snapshot(
        id = node.text("id"),
        shortId = node.text("short_id"),
        time = node.instantOrNull("time"),
        parent = node.textOrNull("parent"),
        tree = node.text("tree"),
        paths = node.textList("paths"),
        hostname = node.text("hostname"),
        username = node.text("username"),
        uid = node.longOrNull("uid"),
        gid = node.longOrNull("gid"),
        excludes = node.textList("excludes"),
        tags = node.textList("tags"),
        programVersion = node.textOrNull("program_version"),
        original = node.textOrNull("original"),
        summary = node.get("summary")?.takeUnless { it.isNull }?.let(::snapshotSummaryFrom),
    )

private fun snapshotSummaryFrom(node: JsonNode) =
    SnapshotSummary(
        backupStart = node.instantOrNull("backup_start"),
        backupEnd = node.instantOrNull("backup_end"),
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
    )

internal fun snapshotGroupFrom(node: JsonNode): SnapshotGroup {
    val key = node.get("group_key")
    return SnapshotGroup(
        key = SnapshotGroupKey(key?.textOrNull("hostname"), key?.textList("paths").orEmpty(), key?.textList("tags").orEmpty()),
        snapshots = node.get("snapshots")?.mapElements(::snapshotFrom).orEmpty(),
    )
}
