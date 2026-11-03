package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/** What `restic stats` counts (`--mode`). */
public enum class StatsMode(
    internal val cliValue: String,
) {
    /** Size a restore would take; restic's default. */
    RESTORE_SIZE("restore-size"),

    /** Size of the files, counting files with identical content only once. */
    FILES_BY_CONTENTS("files-by-contents"),

    /** Size of the blobs the files reference. */
    BLOBS_PER_FILE("blobs-per-file"),

    /** Storage actually used in the repository, with compression figures. */
    RAW_DATA("raw-data"),
}

/**
 * Options for [MaintenanceCommands.stats] (`restic stats`).
 *
 * @property cancellationToken Token that cancels the call.
 * @property filter Selects snapshots by host, path and tags.
 * @property mode What is counted (`--mode`).
 * @property snapshotIds Selected snapshots; all if none are given.
 */
public class StatsOptions private constructor(
    builder: Builder,
) {
    public val cancellationToken: CancellationToken? = builder.cancellationToken
    public val filter: SnapshotFilter = builder.filter.build()
    public val mode: StatsMode? = builder.mode
    public val snapshotIds: List<String> = builder.snapshotIds.toList()

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalValue("--mode", mode?.cliValue)
            addAll(snapshotIds)
        }

    /** Builds [StatsOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal var cancellationToken: CancellationToken? = null
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var mode: StatsMode? = null
        internal val snapshotIds: MutableList<String> = mutableListOf()

        /** Selects only snapshots of this host (`--host`); may be repeated. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Selects only snapshots that backed up exactly this path (`--path`); may be repeated. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Selects only snapshots carrying all of these tags (`--tag`); several calls are combined with OR. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** What is counted (`--mode`); defaults to [StatsMode.RESTORE_SIZE]. */
        public fun mode(mode: StatsMode): Builder = apply { this.mode = mode }

        /** Restricts the command to this snapshot (ID, short ID or `latest`); may be repeated. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Cancels the call; a token passed directly to [Restic] takes precedence. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): StatsOptions = StatsOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: restic's default behaviour. */
        @JvmField
        public val DEFAULT: StatsOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
