package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * A match of a name search.
 *
 * @property path Path in the snapshot.
 * @property type `file`, `dir` or another file type; only with [FindOptions.Builder.long].
 * @property size Size in bytes; only with [FindOptions.Builder.long].
 * @property permissions Permissions such as `-rw-r--r--`; only with [FindOptions.Builder.long].
 * @property modificationTime Last modification; only with [FindOptions.Builder.long].
 */
public data class FindMatch(
    val path: String,
    val type: String?,
    val size: Long?,
    val permissions: String?,
    val modificationTime: Instant?,
)

/**
 * The matches of a name search in one snapshot.
 *
 * @property snapshotId ID of the snapshot.
 * @property hits Number of matches.
 * @property matches The matches.
 * @property snapshot The snapshot itself, read on first access (`restic cat snapshot`), once.
 */
public class FindSnapshotMatches internal constructor(
    public val snapshotId: String,
    public val hits: Long,
    public val matches: List<FindMatch>,
    snapshot: Lazy<Snapshot>,
) {
    public val snapshot: Snapshot by snapshot

    /** The fields known without reading the snapshot. */
    override fun toString(): String = "FindSnapshotMatches(snapshotId=$snapshotId, hits=$hits, matches=$matches)"
}

/** Where a blob, tree or pack content was found; see [FindObject]. */
internal data class ObjectLocation(
    val objectType: String,
    val id: String,
    val path: String?,
    val parentTree: String?,
    val snapshotId: String,
    val time: Instant?,
)

/**
 * Where a blob, tree or pack content was found.
 *
 * @property objectType `blob`, `tree` or `pack`.
 * @property id ID of the object searched for.
 * @property path Path of the file or directory containing the object.
 * @property parentTree ID of the tree the location is in.
 * @property snapshotId ID of the snapshot.
 * @property time Time of the snapshot.
 * @property snapshot The snapshot itself, read on first access (`restic cat snapshot`), once.
 */
public class FindObject internal constructor(
    private val location: ObjectLocation,
    snapshot: Lazy<Snapshot>,
) {
    public val objectType: String get() = location.objectType
    public val id: String get() = location.id
    public val path: String? get() = location.path
    public val parentTree: String? get() = location.parentTree
    public val snapshotId: String get() = location.snapshotId
    public val time: Instant? get() = location.time
    public val snapshot: Snapshot by snapshot

    /** The fields known without reading the snapshot. */
    override fun toString(): String = "FindObject(objectType=$objectType, id=$id, path=$path, snapshotId=$snapshotId)"
}

internal class FindCommand(
    private val runner: ProcessRunner,
) {
    fun findNames(
        patterns: List<String>,
        options: FindOptions,
    ): List<FindSnapshotMatches> =
        readDocument(options.arguments() + patterns).map { node ->
            FindSnapshotMatches(
                snapshotId = node.text("snapshot"),
                hits = node.long("hits"),
                matches = node.get("matches")?.mapElements(::matchFrom).orEmpty(),
                snapshot = snapshotOnDemand(node.text("snapshot")),
            )
        }

    fun findObjects(
        kind: FindObjectKind,
        ids: List<String>,
        options: FindOptions,
    ): List<FindObject> =
        readDocument(options.arguments() + kind.flag + ids).map { node ->
            FindObject(
                ObjectLocation(
                    objectType = node.text("object_type"),
                    id = node.text("id"),
                    path = node.textOrNull("path"),
                    parentTree = node.textOrNull("parent_tree"),
                    snapshotId = node.text("snapshot"),
                    time = node.instantOrNull("time"),
                ),
                snapshot = snapshotOnDemand(node.text("snapshot")),
            )
        }

    private fun snapshotOnDemand(snapshotId: String): Lazy<Snapshot> = lazy { DumpAndCatCommand(runner).catSnapshot(snapshotId) }

    private fun matchFrom(node: JsonNode) =
        FindMatch(
            path = node.text("path"),
            type = node.textOrNull("type"),
            size = node.longOrNull("size"),
            permissions = node.textOrNull("permissions"),
            modificationTime = node.instantOrNull("mtime"),
        )

    private fun readDocument(arguments: List<String>): List<JsonNode> {
        val lines = mutableListOf<String>()
        runner.runChecked(listOf("find", "--json") + arguments) { lines += it }
        return Json.arrayElements(lines)
    }
}
