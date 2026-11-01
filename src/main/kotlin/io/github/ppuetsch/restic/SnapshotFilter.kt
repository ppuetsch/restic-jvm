package io.github.ppuetsch.restic

/**
 * Gemeinsame Snapshot-Auswahl vieler Befehle (`--host`, `--path`, `--tag`).
 * Mehrere Hosts oder Pfade sind eine ODER-Auswahl; innerhalb einer Tag-Gruppe müssen alle Tags zutreffen,
 * mehrere Tag-Gruppen sind ODER-verknüpft.
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

    public class Builder internal constructor() {
        internal val hosts: MutableList<String> = mutableListOf()
        internal val paths: MutableList<String> = mutableListOf()
        internal val tagGroups: MutableList<List<String>> = mutableListOf()

        public fun host(hostname: String): Builder = apply { hosts.add(hostname) }

        public fun path(absolutePath: String): Builder = apply { paths.add(absolutePath) }

        /** Alle übergebenen Tags müssen auf den Snapshot zutreffen. */
        public fun tag(vararg tags: String): Builder = apply { tagGroups.add(tags.toList()) }

        public fun build(): SnapshotFilter = SnapshotFilter(this)
    }

    public companion object {
        @JvmField
        public val NONE: SnapshotFilter = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
