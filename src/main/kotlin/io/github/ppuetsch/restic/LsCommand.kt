package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * An entry of a snapshot: a file, a directory or another file type.
 *
 * @property name Name without path.
 * @property type `file`, `dir`, `symlink` or another file type.
 * @property path Full path in the snapshot.
 * @property size Size in bytes, files only.
 * @property mode Unix file mode.
 * @property permissions Permissions such as `-rw-r--r--`.
 * @property uid User ID of the owner.
 * @property gid Group ID of the owner.
 * @property user Name of the owner.
 * @property group Name of the group.
 * @property inode Inode number at the time of the backup.
 * @property links Number of hard links.
 * @property modificationTime Last modification (mtime).
 * @property accessTime Last access (atime).
 * @property changeTime Last status change (ctime).
 */
public data class LsNode(
    val name: String,
    val type: String,
    val path: String,
    val size: Long?,
    val mode: Long?,
    val permissions: String?,
    val uid: Long?,
    val gid: Long?,
    val user: String?,
    val group: String?,
    val inode: Long?,
    val links: Long?,
    val modificationTime: Instant?,
    val accessTime: Instant?,
    val changeTime: Instant?,
) {
    /** `true` for a directory. */
    public fun isDirectory(): Boolean = type == "dir"

    /** `true` for a regular file. */
    public fun isFile(): Boolean = type == "file"
}

/** Receives the entries of a running [FileCommands.ls]; called on the thread that reads restic's output. */
public fun interface LsListener {
    /** An entry of the snapshot. */
    public fun onNode(node: LsNode)
}

/**
 * Result of [FileCommands.listNodes].
 *
 * @property snapshot The listed snapshot.
 * @property nodes All entries, in output order.
 */
public data class LsResult(
    val snapshot: Snapshot,
    val nodes: List<LsNode>,
)

private fun lsNodeFrom(node: JsonNode) =
    LsNode(
        name = node.text("name"),
        type = node.text("type"),
        path = node.text("path"),
        size = node.longOrNull("size"),
        mode = node.longOrNull("mode"),
        permissions = node.textOrNull("permissions"),
        uid = node.longOrNull("uid"),
        gid = node.longOrNull("gid"),
        user = node.textOrNull("user"),
        group = node.textOrNull("group"),
        inode = node.longOrNull("inode"),
        links = node.longOrNull("links"),
        modificationTime = node.instantOrNull("mtime"),
        accessTime = node.instantOrNull("atime"),
        changeTime = node.instantOrNull("ctime"),
    )

internal class LsCommand(
    private val runner: ProcessRunner,
) {
    fun ls(
        snapshotId: String,
        options: LsOptions,
        listener: LsListener?,
    ): Snapshot {
        var snapshot: Snapshot? = null
        val arguments = listOf("ls", "--json") + options.arguments() + snapshotId + options.directories
        runner.runChecked(arguments) { line ->
            Json.parseOrNull(line)?.let { node ->
                when (node.get("struct_type")?.asString() ?: node.messageType()) {
                    "snapshot" -> snapshot = snapshotFrom(node)
                    "node" -> listener?.onNode(lsNodeFrom(node))
                }
            }
        }
        return snapshot ?: throw ResticOutputException("restic ls produced no snapshot header", null)
    }
}
