package io.github.ppuetsch.restic

/**
 * Options for [Restic.snapshots] and [Restic.snapshotGroups] (`restic snapshots`).
 *
 * @property cancellationToken Token that cancels the call.
 * @property filter Selects snapshots by host, path and tags.
 * @property groupBy Grouping (`--group-by`).
 * @property latest Only the last n snapshots per group (`--latest`).
 * @property compact Shorter output (`--compact`).
 * @property snapshotIds Restriction to certain snapshots.
 */
public class SnapshotsOptions private constructor(
    builder: Builder,
) {
    public val cancellationToken: CancellationToken? = builder.cancellationToken
    public val filter: SnapshotFilter = builder.filter.build()
    public val groupBy: String? = builder.groupBy
    public val latest: Int? = builder.latest
    public val compact: Boolean = builder.compact
    public val snapshotIds: List<String> = builder.snapshotIds.toList()

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalValue("--group-by", groupBy)
            optionalValue("--latest", latest)
            optionalFlag("--compact", compact)
            addAll(snapshotIds)
        }

    /** Builds [SnapshotsOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal var cancellationToken: CancellationToken? = null
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var groupBy: String? = null
        internal var latest: Int? = null
        internal var compact: Boolean = false
        internal val snapshotIds: MutableList<String> = mutableListOf()

        /** Only snapshots of this host (`--host`); may be repeated. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Only snapshots that backed up exactly this path (`--path`); may be repeated. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Only snapshots carrying all of these tags (`--tag`); several calls are combined with OR. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** A combination of `host`, `paths`, `tags`; empty turns grouping off. */
        public fun groupBy(group: String): Builder = apply { groupBy = group }

        /** Only the last n snapshots per host and path. */
        public fun latest(count: Int): Builder = apply { latest = count }

        /** Shorter text output (`--compact`); the JSON result stays the same. */
        @JvmOverloads
        public fun compact(enabled: Boolean = true): Builder = apply { compact = enabled }

        /** Restricts the output to certain snapshots (ID, short ID or `latest`). */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Cancels the call; a token passed directly to [Restic] takes precedence. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): SnapshotsOptions = SnapshotsOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: all snapshots. */
        @JvmField
        public val DEFAULT: SnapshotsOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
