package io.github.ppuetsch.restic

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

public enum class FindObjectKind(internal val flag: String) {
    BLOB("--blob"),
    TREE("--tree"),
    PACK("--pack"),
}

/** Optionen für `restic find`. */
public class FindOptions private constructor(builder: Builder) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val snapshotIds: List<String> = builder.snapshotIds.toList()
    public val ignoreCase: Boolean = builder.ignoreCase
    public val long: Boolean = builder.long
    public val humanReadable: Boolean = builder.humanReadable
    public val newest: String? = builder.newest
    public val oldest: String? = builder.oldest
    public val reverse: Boolean = builder.reverse
    public val showPackId: Boolean = builder.showPackId

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            snapshotIds.forEach { optionalValue("--snapshot", it) }
            optionalFlag("--ignore-case", ignoreCase)
            optionalFlag("--long", long)
            optionalFlag("--human-readable", humanReadable)
            optionalValue("--newest", newest)
            optionalValue("--oldest", oldest)
            optionalFlag("--reverse", reverse)
            optionalFlag("--show-pack-id", showPackId)
        }

    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal var ignoreCase: Boolean = false
        internal var long: Boolean = false
        internal var humanReadable: Boolean = false
        internal var newest: String? = null
        internal var oldest: String? = null
        internal var reverse: Boolean = false
        internal var showPackId: Boolean = false

        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Sucht nur in diesem Snapshot (mehrfach möglich). */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        public fun ignoreCase(enabled: Boolean = true): Builder = apply { ignoreCase = enabled }

        public fun long(enabled: Boolean = true): Builder = apply { long = enabled }

        public fun humanReadable(enabled: Boolean = true): Builder = apply { humanReadable = enabled }

        /** Neuester Änderungszeitpunkt, Format `YYYY-MM-DD[ HH:MM[:SS]]`. */
        public fun newest(dateTime: String): Builder = apply { newest = dateTime }

        /** Ältester Änderungszeitpunkt, Format `YYYY-MM-DD[ HH:MM[:SS]]`. */
        public fun oldest(dateTime: String): Builder = apply { oldest = dateTime }

        public fun reverse(enabled: Boolean = true): Builder = apply { reverse = enabled }

        public fun showPackId(enabled: Boolean = true): Builder = apply { showPackId = enabled }

        public fun build(): FindOptions = FindOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: FindOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public data class FindMatch(
    val path: String,
    val type: String?,
    val size: Long?,
    val permissions: String?,
    val modificationTime: Instant?,
)

/** Treffer einer Namenssuche in einem Snapshot. */
public data class FindSnapshotMatches(
    val snapshotId: String,
    val hits: Long,
    val matches: List<FindMatch>,
)

/** Fundstelle eines Blobs, Trees oder Pack-Inhalts. */
public data class FindObject(
    val objectType: String,
    val id: String,
    val path: String?,
    val parentTree: String?,
    val snapshotId: String,
    val time: Instant?,
)

internal class FindCommand(private val runner: ProcessRunner) {
    fun findNames(
        patterns: List<String>,
        options: FindOptions,
    ): List<FindSnapshotMatches> =
        readDocument(options.arguments() + patterns).map { node ->
            FindSnapshotMatches(
                snapshotId = node.text("snapshot"),
                hits = node.long("hits"),
                matches = node.get("matches")?.map(::matchFrom).orEmpty(),
            )
        }

    fun findObjects(
        kind: FindObjectKind,
        ids: List<String>,
        options: FindOptions,
    ): List<FindObject> =
        readDocument(options.arguments() + kind.flag + ids).map { node ->
            FindObject(
                objectType = node.text("object_type"),
                id = node.text("id"),
                path = node.textOrNull("path"),
                parentTree = node.textOrNull("parent_tree"),
                snapshotId = node.text("snapshot"),
                time = node.instantOrNull("time"),
            )
        }

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
        val text = lines.joinToString("\n").trim()
        return if (text.isEmpty() || text == "null") emptyList() else Json.parse(text).toList()
    }
}
