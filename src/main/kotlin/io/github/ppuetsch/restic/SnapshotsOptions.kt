package io.github.ppuetsch.restic

/**
 * Optionen für [Restic.snapshots] und [Restic.snapshotGroups] (`restic snapshots`).
 *
 * @property cancellationToken Token, über das sich der Aufruf abbrechen lässt.
 * @property filter Auswahl der Snapshots nach Host, Pfad und Tags.
 * @property groupBy Gruppierung (`--group-by`).
 * @property latest Nur die letzten n Snapshots je Gruppe (`--latest`).
 * @property compact Kürzere Ausgabe (`--compact`).
 * @property snapshotIds Beschränkung auf bestimmte Snapshots.
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

    /** Stellt [SnapshotsOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal var cancellationToken: CancellationToken? = null
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var groupBy: String? = null
        internal var latest: Int? = null
        internal var compact: Boolean = false
        internal val snapshotIds: MutableList<String> = mutableListOf()

        /** Nur Snapshots dieses Hosts (`--host`); mehrfach möglich. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Nur Snapshots, die genau diesen Pfad gesichert haben (`--path`); mehrfach möglich. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Nur Snapshots, die alle diese Tags tragen (`--tag`); mehrere Aufrufe sind ODER-verknüpft. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Kombination aus `host`, `paths`, `tags`; leer deaktiviert die Gruppierung. */
        public fun groupBy(group: String): Builder = apply { groupBy = group }

        /** Nur die letzten n Snapshots je Host und Pfad. */
        public fun latest(count: Int): Builder = apply { latest = count }

        /** Kürzere Textausgabe (`--compact`); das JSON-Ergebnis bleibt gleich. */
        @JvmOverloads
        public fun compact(enabled: Boolean = true): Builder = apply { compact = enabled }

        /** Beschränkt die Ausgabe auf bestimmte Snapshots (ID, Kurz-ID oder `latest`). */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Bricht den Aufruf ab; ein direkt an [Restic] übergebenes Token hat Vorrang. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): SnapshotsOptions = SnapshotsOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: alle Snapshots. */
        @JvmField
        public val DEFAULT: SnapshotsOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
