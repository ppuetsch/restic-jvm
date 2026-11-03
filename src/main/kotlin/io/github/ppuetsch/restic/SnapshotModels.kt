package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * The summary restic stores with every snapshot since 0.17.
 *
 * @property backupStart Start time of the backup.
 * @property backupEnd End time of the backup.
 * @property filesNew New files.
 * @property filesChanged Changed files.
 * @property filesUnmodified Unchanged files.
 * @property directoriesNew New directories.
 * @property directoriesChanged Changed directories.
 * @property directoriesUnmodified Unchanged directories.
 * @property dataBlobs Newly written data blobs.
 * @property treeBlobs Newly written tree blobs.
 * @property dataAdded Newly added bytes before compression.
 * @property dataAddedPacked Newly added bytes after compression.
 * @property totalFilesProcessed All processed files.
 * @property totalBytesProcessed Size of all processed files.
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
 * A snapshot: the state of the backed-up paths at one point in time.
 *
 * @property id Full ID.
 * @property shortId The first eight characters of the ID, as restic shows them.
 * @property time Time of the backup.
 * @property parent ID of the parent snapshot it was compared against.
 * @property tree ID of the root directory.
 * @property paths Backed-up paths.
 * @property hostname Host on which the backup ran.
 * @property username User who ran the backup.
 * @property uid User ID on Unix.
 * @property gid Group ID on Unix.
 * @property excludes Exclude patterns used for the backup.
 * @property tags Tags of the snapshot.
 * @property programVersion restic version that created the snapshot.
 * @property original Snapshot ID of the original, if this snapshot was created by `copy` or `rewrite`.
 * @property summary Summary of the backup; missing for snapshots made by restic before 0.17.
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
 * What a snapshot group has in common; only the fields used for grouping are set.
 *
 * @property hostname Host of the group.
 * @property paths Paths of the group.
 * @property tags Tags of the group.
 */
public data class SnapshotGroupKey(
    val hostname: String?,
    val paths: List<String>,
    val tags: List<String>,
)

/**
 * A group of snapshots from [Restic.snapshotGroups].
 *
 * @property key What the group has in common.
 * @property snapshots The snapshots of the group.
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
