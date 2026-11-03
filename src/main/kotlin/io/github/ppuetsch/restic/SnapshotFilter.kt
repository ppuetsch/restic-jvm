package io.github.ppuetsch.restic

/**
 * Snapshot selection shared by many commands (`--host`, `--path`, `--tag`).
 * Several hosts or paths form an OR selection; within one tag group all tags must match,
 * several tag groups are combined with OR.
 *
 * @property hosts Allowed hosts.
 * @property paths Allowed backed-up paths.
 * @property tagGroups Tag groups, one of which must match completely.
 */
public class SnapshotFilter private constructor(
    builder: Builder,
) {
    public val hosts: List<String> = builder.hosts.toList()
    public val paths: List<String> = builder.paths.toList()
    public val tagGroups: List<List<String>> = builder.tagGroups.map { it.toList() }

    internal fun arguments(): List<String> =
        buildList {
            hosts.forEach { optionalValue("--host", it) }
            paths.forEach { optionalValue("--path", it) }
            tagGroups.forEach { optionalValue("--tag", it.joinToString(",")) }
        }

    /** Builds a [SnapshotFilter]; every method widens the selection and returns the builder. */
    public class Builder internal constructor() {
        internal val hosts: MutableList<String> = mutableListOf()
        internal val paths: MutableList<String> = mutableListOf()
        internal val tagGroups: MutableList<List<String>> = mutableListOf()

        /** Allows snapshots of this host; may be repeated. */
        public fun host(hostname: String): Builder = apply { hosts.add(hostname) }

        /** Allows snapshots that backed up exactly this absolute path; may be repeated. */
        public fun path(absolutePath: String): Builder = apply { paths.add(absolutePath) }

        /** All given tags must apply to the snapshot. */
        public fun tag(vararg tags: String): Builder = apply { tagGroups.add(tags.toList()) }

        /** Creates the immutable filter. */
        public fun build(): SnapshotFilter = SnapshotFilter(this)
    }

    /** The empty filter and entry point to the [Builder]. */
    public companion object {
        /** Selects all snapshots. */
        @JvmField
        public val NONE: SnapshotFilter = Builder().build()

        /** A new [Builder] without restrictions. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
