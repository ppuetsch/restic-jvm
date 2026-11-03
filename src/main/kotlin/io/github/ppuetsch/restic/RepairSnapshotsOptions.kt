package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime

/**
 * Options for [RepairCommands.snapshots] (`restic repair snapshots`).
 *
 * @property filter Selects snapshots by host, path and tags.
 * @property snapshotIds Selected snapshots; all if none are given.
 * @property dryRun Only show what would be repaired (`--dry-run`).
 * @property forget Removes the broken originals after the repair (`--forget`).
 */
public class RepairSnapshotsOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val snapshotIds: List<String> = builder.snapshotIds.toList()
    public val dryRun: Boolean = builder.dryRun
    public val forget: Boolean = builder.forget

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalFlag("--dry-run", dryRun)
            optionalFlag("--forget", forget)
            addAll(snapshotIds)
        }

    /** Builds [RepairSnapshotsOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal var dryRun: Boolean = false
        internal var forget: Boolean = false

        /** Selects only snapshots of this host (`--host`); may be repeated. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Selects only snapshots that backed up exactly this path (`--path`); may be repeated. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Selects only snapshots carrying all of these tags (`--tag`); several calls are combined with OR. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Repairs only this snapshot (ID, short ID or `latest`); may be repeated. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Only shows what would be repaired (`--dry-run`). */
        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /** Removes the broken original snapshots after the repair (`--forget`). */
        @JvmOverloads
        public fun forget(enabled: Boolean = true): Builder = apply { forget = enabled }

        /** Creates the immutable options. */
        public fun build(): RepairSnapshotsOptions = RepairSnapshotsOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: repairs all snapshots. */
        @JvmField
        public val DEFAULT: RepairSnapshotsOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
