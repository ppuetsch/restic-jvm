package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/**
 * A change between two snapshots.
 *
 * @property path Path of the changed file or directory.
 * @property modifier `+` added, `-` removed, `M` content changed, `U` metadata changed, `T` type changed,
 *   `?` unknown.
 */
public data class DiffChange(
    val path: String,
    val modifier: String,
)

/**
 * Amounts added or removed between two snapshots.
 *
 * @property files Number of files.
 * @property directories Number of directories.
 * @property others Number of other entries such as symlinks or devices.
 * @property dataBlobs Number of data blobs.
 * @property treeBlobs Number of tree blobs.
 * @property bytes Size of the blobs in bytes.
 */
public data class DiffCounts(
    val files: Long,
    val directories: Long,
    val others: Long,
    val dataBlobs: Long,
    val treeBlobs: Long,
    val bytes: Long,
)

/**
 * Summary of a comparison of two snapshots.
 *
 * @property sourceSnapshotId Full ID of the first snapshot.
 * @property targetSnapshotId Full ID of the second snapshot.
 * @property changedFiles Number of changed files.
 * @property added What the second snapshot added.
 * @property removed What the second snapshot lacks.
 */
public data class DiffStatistics(
    val sourceSnapshotId: String,
    val targetSnapshotId: String,
    val changedFiles: Long,
    val added: DiffCounts,
    val removed: DiffCounts,
)

/**
 * Result of [FileCommands.diffAll].
 *
 * @property changes All changes, in output order.
 * @property statistics The summary.
 */
public data class DiffResult(
    val changes: List<DiffChange>,
    val statistics: DiffStatistics,
)

/** Receives the changes of a running [FileCommands.diff]; called on the thread that reads restic's output. */
public fun interface DiffListener {
    /** A change that was found. */
    public fun onChange(change: DiffChange)
}

private fun diffCountsFrom(node: JsonNode?): DiffCounts =
    DiffCounts(
        files = node?.long("files") ?: 0,
        directories = node?.long("dirs") ?: 0,
        others = node?.long("others") ?: 0,
        dataBlobs = node?.long("data_blobs") ?: 0,
        treeBlobs = node?.long("tree_blobs") ?: 0,
        bytes = node?.long("bytes") ?: 0,
    )

internal class DiffCommand(
    private val runner: ProcessRunner,
) {
    fun diff(
        firstSnapshotId: String,
        secondSnapshotId: String,
        options: DiffOptions,
        listener: DiffListener?,
    ): DiffStatistics {
        var statistics: DiffStatistics? = null
        val arguments = listOf("diff", "--json") + options.arguments() + firstSnapshotId + secondSnapshotId
        runner.runChecked(ProcessRequest(arguments, cancellation = options.cancellationToken)) { line ->
            Json.parseOrNull(line)?.let { node ->
                when (node.messageType()) {
                    "change" -> {
                        listener?.onChange(DiffChange(node.text("path"), node.text("modifier")))
                    }

                    "statistics" -> {
                        statistics =
                            DiffStatistics(
                                sourceSnapshotId = node.text("source_snapshot"),
                                targetSnapshotId = node.text("target_snapshot"),
                                changedFiles = node.long("changed_files"),
                                added = diffCountsFrom(node.get("added")),
                                removed = diffCountsFrom(node.get("removed")),
                            )
                    }
                }
            }
        }
        return statistics ?: throw ResticOutputException("restic diff produced no statistics", null)
    }
}
