package io.github.ppuetsch.restic

/**
 * Options for [ModifyCommands.tag] (`restic tag`). At least one of the actions `add`, `remove` or `set` is required.
 *
 * @property filter Selects snapshots by host, path and tags.
 * @property snapshotIds Snapshots to change; if none are given, all that match the filter.
 * @property addTagGroups Tags to add (`--add`).
 * @property removeTagGroups Tags to remove (`--remove`).
 * @property setTagGroups Tags that replace the existing ones (`--set`).
 */
public class TagOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val snapshotIds: List<String> = builder.snapshotIds.toList()
    public val addTagGroups: List<List<String>> = builder.addTagGroups.map { it.toList() }
    public val removeTagGroups: List<List<String>> = builder.removeTagGroups.map { it.toList() }
    public val setTagGroups: List<List<String>> = builder.setTagGroups.map { it.toList() }

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            addTagGroups.forEach { optionalValue("--add", it.joinToString(",")) }
            removeTagGroups.forEach { optionalValue("--remove", it.joinToString(",")) }
            setTagGroups.forEach { optionalValue("--set", it.joinToString(",")) }
            addAll(snapshotIds)
        }

    /** Builds [TagOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal val addTagGroups: MutableList<List<String>> = mutableListOf()
        internal val removeTagGroups: MutableList<List<String>> = mutableListOf()
        internal val setTagGroups: MutableList<List<String>> = mutableListOf()

        /** Selects snapshots by host (a filter, not a change). */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Selects snapshots by backed-up path (a filter, not a change). */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Selects snapshots carrying all of the given tags (a filter, not a change). */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Changes this snapshot (ID, short ID or `latest`); may be repeated. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Adds these tags (`--add`). */
        public fun add(vararg tags: String): Builder = apply { addTagGroups.add(tags.toList()) }

        /** Removes these tags (`--remove`). */
        public fun remove(vararg tags: String): Builder = apply { removeTagGroups.add(tags.toList()) }

        /** Replaces the existing tags completely. */
        public fun set(vararg tags: String): Builder = apply { setTagGroups.add(tags.toList()) }

        /** Creates the immutable options. */
        public fun build(): TagOptions = TagOptions(this)
    }

    /** Einstieg in den [Builder]. */
    public companion object {
        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
