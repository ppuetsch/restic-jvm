package io.github.ppuetsch.restic

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

public enum class LsSort(internal val cliValue: String) {
    NAME("name"),
    SIZE("size"),
    MODIFICATION_TIME("time"),
    ACCESS_TIME("atime"),
    CHANGE_TIME("ctime"),
    EXTENSION("extension"),
}

/** Optionen für `restic ls`. */
public class LsOptions private constructor(builder: Builder) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val directories: List<String> = builder.directories.toList()
    public val recursive: Boolean = builder.recursive
    public val reverse: Boolean = builder.reverse
    public val sort: LsSort? = builder.sort
    public val long: Boolean = builder.long
    public val humanReadable: Boolean = builder.humanReadable

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalFlag("--recursive", recursive)
            optionalFlag("--reverse", reverse)
            optionalValue("--sort", sort?.cliValue)
            optionalFlag("--long", long)
            optionalFlag("--human-readable", humanReadable)
        }

    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val directories: MutableList<String> = mutableListOf()
        internal var recursive: Boolean = false
        internal var reverse: Boolean = false
        internal var sort: LsSort? = null
        internal var long: Boolean = false
        internal var humanReadable: Boolean = false

        /** Wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Verzeichnis innerhalb des Snapshots, das aufgelistet werden soll. */
        public fun directory(pathInSnapshot: String): Builder = apply { directories.add(pathInSnapshot) }

        public fun recursive(enabled: Boolean = true): Builder = apply { recursive = enabled }

        public fun reverse(enabled: Boolean = true): Builder = apply { reverse = enabled }

        public fun sort(sort: LsSort): Builder = apply { this.sort = sort }

        public fun long(enabled: Boolean = true): Builder = apply { long = enabled }

        public fun humanReadable(enabled: Boolean = true): Builder = apply { humanReadable = enabled }

        public fun build(): LsOptions = LsOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: LsOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

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
    public fun isDirectory(): Boolean = type == "dir"

    public fun isFile(): Boolean = type == "file"
}

public fun interface LsListener {
    public fun onNode(node: LsNode)
}

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

internal class LsCommand(private val runner: ProcessRunner) {
    fun ls(
        snapshotId: String,
        options: LsOptions,
        listener: LsListener?,
    ): Snapshot {
        var snapshot: Snapshot? = null
        val arguments = listOf("ls", "--json") + options.arguments() + snapshotId + options.directories
        runner.runChecked(arguments) { line ->
            if (line.isNotBlank()) {
                val node = Json.parse(line)
                when (node.get("struct_type")?.asText() ?: node.messageType()) {
                    "snapshot" -> snapshot = snapshotFrom(node)
                    "node" -> listener?.onNode(lsNodeFrom(node))
                }
            }
        }
        return snapshot ?: throw ResticOutputException("restic ls produced no snapshot header", null)
    }

    fun ncdu(
        snapshotId: String,
        options: LsOptions,
    ): String {
        val lines = mutableListOf<String>()
        runner.runChecked(listOf("ls", "--ncdu") + options.arguments() + snapshotId + options.directories) { lines += it }
        return lines.joinToString("\n")
    }
}
