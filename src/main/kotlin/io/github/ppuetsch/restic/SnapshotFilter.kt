package io.github.ppuetsch.restic

/**
 * Gemeinsame Snapshot-Auswahl vieler Befehle (`--host`, `--path`, `--tag`).
 * Mehrere Hosts oder Pfade sind eine ODER-Auswahl; innerhalb einer Tag-Gruppe müssen alle Tags zutreffen,
 * mehrere Tag-Gruppen sind ODER-verknüpft.
 *
 * @property hosts Zulässige Hosts.
 * @property paths Zulässige gesicherte Pfade.
 * @property tagGroups Tag-Gruppen, von denen eine vollständig zutreffen muss.
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

    /** Stellt einen [SnapshotFilter] zusammen; jede Methode erweitert die Auswahl und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal val hosts: MutableList<String> = mutableListOf()
        internal val paths: MutableList<String> = mutableListOf()
        internal val tagGroups: MutableList<List<String>> = mutableListOf()

        /** Lässt Snapshots dieses Hosts zu; mehrfach möglich. */
        public fun host(hostname: String): Builder = apply { hosts.add(hostname) }

        /** Lässt Snapshots zu, die genau diesen absoluten Pfad gesichert haben; mehrfach möglich. */
        public fun path(absolutePath: String): Builder = apply { paths.add(absolutePath) }

        /** Alle übergebenen Tags müssen auf den Snapshot zutreffen. */
        public fun tag(vararg tags: String): Builder = apply { tagGroups.add(tags.toList()) }

        /** Erzeugt den unveränderlichen Filter. */
        public fun build(): SnapshotFilter = SnapshotFilter(this)
    }

    /** Der leere Filter und Einstieg in den [Builder]. */
    public companion object {
        /** Wählt alle Snapshots. */
        @JvmField
        public val NONE: SnapshotFilter = Builder().build()

        /** Ein neuer [Builder] ohne Einschränkung. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
