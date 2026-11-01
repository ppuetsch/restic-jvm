package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/** Optionen für `restic diff`. */
public class DiffOptions private constructor(
    builder: Builder,
) {
    public val metadata: Boolean = builder.metadata
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> = buildList { optionalFlag("--metadata", metadata) }

    public class Builder internal constructor() {
        internal var metadata: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        /** Meldet auch reine Metadaten-Änderungen. */
        @JvmOverloads
        public fun metadata(enabled: Boolean = true): Builder = apply { metadata = enabled }

        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        public fun build(): DiffOptions = DiffOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: DiffOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Eine Änderung zwischen zwei Snapshots. [modifier]: `+` hinzugefügt, `-` entfernt, `M` Inhalt geändert,
 * `U` Metadaten geändert, `T` Typ geändert, `?` unbekannt.
 */
public data class DiffChange(
    val path: String,
    val modifier: String,
)

public data class DiffCounts(
    val files: Long,
    val directories: Long,
    val others: Long,
    val dataBlobs: Long,
    val treeBlobs: Long,
    val bytes: Long,
)

public data class DiffStatistics(
    val sourceSnapshotId: String,
    val targetSnapshotId: String,
    val changedFiles: Long,
    val added: DiffCounts,
    val removed: DiffCounts,
)

public data class DiffResult(
    val changes: List<DiffChange>,
    val statistics: DiffStatistics,
)

public fun interface DiffListener {
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
