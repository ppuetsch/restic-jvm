package io.github.ppuetsch.restic

/**
 * Changing tags creates a new snapshot; the old one is removed.
 *
 * @property oldSnapshotId ID of the removed snapshot.
 * @property newSnapshotId ID of the new snapshot with the changed tags.
 */
public data class TagChange(
    val oldSnapshotId: String,
    val newSnapshotId: String,
)

/**
 * Result of [ModifyCommands.tag].
 *
 * @property changes Old and new ID of each changed snapshot.
 * @property changedSnapshots Number of changed snapshots according to restic.
 */
public data class TagResult(
    val changes: List<TagChange>,
    val changedSnapshots: Long,
)

internal class TagCommand(
    private val runner: ProcessRunner,
) {
    fun tag(options: TagOptions): TagResult {
        val changes = mutableListOf<TagChange>()
        var changedSnapshots = 0L
        runner.runChecked(listOf("tag", "--json") + options.arguments()) { line ->
            Json.parseOrNull(line)?.let { node ->
                when (node.messageType()) {
                    "changed" -> changes += TagChange(node.text("old_snapshot_id"), node.text("new_snapshot_id"))
                    "summary" -> changedSnapshots = node.long("changed_snapshots")
                }
            }
        }
        return TagResult(changes, changedSnapshots)
    }
}
