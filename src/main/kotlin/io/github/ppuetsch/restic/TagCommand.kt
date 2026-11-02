package io.github.ppuetsch.restic

/**
 * Optionen für [Restic.tag] (`restic tag`). Mindestens eine der Aktionen `add`, `remove` oder `set` ist nötig.
 *
 * @property filter Auswahl der Snapshots nach Host, Pfad und Tags.
 * @property snapshotIds Zu ändernde Snapshots; ohne Angabe alle, die zum Filter passen.
 * @property addTagGroups Hinzuzufügende Tags (`--add`).
 * @property removeTagGroups Zu entfernende Tags (`--remove`).
 * @property setTagGroups Tags, die die vorhandenen ersetzen (`--set`).
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

    /** Stellt [TagOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal val addTagGroups: MutableList<List<String>> = mutableListOf()
        internal val removeTagGroups: MutableList<List<String>> = mutableListOf()
        internal val setTagGroups: MutableList<List<String>> = mutableListOf()

        /** Wählt Snapshots nach Host aus (Filter, nicht Änderung). */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Wählt Snapshots nach gesichertem Pfad aus (Filter, nicht Änderung). */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Wählt Snapshots, die alle genannten Tags tragen (Filter, nicht Änderung). */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Ändert diesen Snapshot (ID, Kurz-ID oder `latest`); mehrfach möglich. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Fügt diese Tags hinzu (`--add`). */
        public fun add(vararg tags: String): Builder = apply { addTagGroups.add(tags.toList()) }

        /** Entfernt diese Tags (`--remove`). */
        public fun remove(vararg tags: String): Builder = apply { removeTagGroups.add(tags.toList()) }

        /** Ersetzt die vorhandenen Tags vollständig. */
        public fun set(vararg tags: String): Builder = apply { setTagGroups.add(tags.toList()) }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): TagOptions = TagOptions(this)
    }

    /** Einstieg in den [Builder]. */
    public companion object {
        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Beim Ändern der Tags entsteht ein neuer Snapshot; der alte wird entfernt.
 *
 * @property oldSnapshotId ID des entfernten Snapshots.
 * @property newSnapshotId ID des neuen Snapshots mit den geänderten Tags.
 */
public data class TagChange(
    val oldSnapshotId: String,
    val newSnapshotId: String,
)

/**
 * Ergebnis von [Restic.tag].
 *
 * @property changes Je geändertem Snapshot alte und neue ID.
 * @property changedSnapshots Anzahl geänderter Snapshots laut restic.
 */
public data class TagResult(
    val changes: List<TagChange>,
    val changedSnapshots: Long,
)

internal class TagCommand(
    private val runner: ProcessRunner,
) {
    fun tag(options: TagOptions): TagResult {
        val changes = mutableListOf<TagChange>()
        var changedSnapshots = 0L
        runner.runChecked(listOf("tag", "--json") + options.arguments()) { line ->
            Json.parseOrNull(line)?.let { node ->
                when (node.messageType()) {
                    "changed" -> changes += TagChange(node.text("old_snapshot_id"), node.text("new_snapshot_id"))
                    "summary" -> changedSnapshots = node.long("changed_snapshots")
                }
            }
        }
        return TagResult(changes, changedSnapshots)
    }
}
