package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Instant

/** Art der Objekte, nach denen [Restic.findObjects] sucht. */
public enum class FindObjectKind(
    internal val flag: String,
) {
    /** Ein Daten-Blob (`--blob`). */
    BLOB("--blob"),

    /** Ein Tree-Blob, also ein Verzeichnis (`--tree`). */
    TREE("--tree"),

    /** Eine Pack-Datei; gefunden werden die Dateien, deren Blobs darin liegen (`--pack`). */
    PACK("--pack"),
}

/**
 * Optionen für [Restic.find] und [Restic.findObjects] (`restic find`).
 *
 * @property cancellationToken Token, über das sich der Aufruf abbrechen lässt.
 * @property filter Auswahl der Snapshots nach Host, Pfad und Tags.
 * @property snapshotIds Durchsucht nur diese Snapshots (`--snapshot`).
 * @property ignoreCase Ignoriert Groß-/Kleinschreibung der Muster (`--ignore-case`).
 * @property long Liefert Details wie Größe und Rechte (`--long`).
 * @property humanReadable Größen menschenlesbar (`--human-readable`).
 * @property newest Nur Einträge, die bis zu diesem Zeitpunkt geändert wurden (`--newest`).
 * @property oldest Nur Einträge, die ab diesem Zeitpunkt geändert wurden (`--oldest`).
 * @property reverse Durchsucht die Snapshots vom ältesten zum neuesten (`--reverse`).
 * @property showPackId Nennt bei der Blob-Suche auch das Pack (`--show-pack-id`).
 */
public class FindOptions private constructor(
    builder: Builder,
) {
    public val cancellationToken: CancellationToken? = builder.cancellationToken
    public val filter: SnapshotFilter = builder.filter.build()
    public val snapshotIds: List<String> = builder.snapshotIds.toList()
    public val ignoreCase: Boolean = builder.ignoreCase
    public val long: Boolean = builder.long
    public val humanReadable: Boolean = builder.humanReadable
    public val newest: String? = builder.newest
    public val oldest: String? = builder.oldest
    public val reverse: Boolean = builder.reverse
    public val showPackId: Boolean = builder.showPackId

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            snapshotIds.forEach { optionalValue("--snapshot", it) }
            optionalFlag("--ignore-case", ignoreCase)
            optionalFlag("--long", long)
            optionalFlag("--human-readable", humanReadable)
            optionalValue("--newest", newest)
            optionalValue("--oldest", oldest)
            optionalFlag("--reverse", reverse)
            optionalFlag("--show-pack-id", showPackId)
        }

    /** Stellt [FindOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    @Suppress("TooManyFunctions") // eine Methode je restic-Flag; das Builder-API bildet die CLI vollständig ab
    public class Builder internal constructor() {
        internal var cancellationToken: CancellationToken? = null
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal var ignoreCase: Boolean = false
        internal var long: Boolean = false
        internal var humanReadable: Boolean = false
        internal var newest: String? = null
        internal var oldest: String? = null
        internal var reverse: Boolean = false
        internal var showPackId: Boolean = false

        /** Durchsucht nur Snapshots dieses Hosts (`--host`); mehrfach möglich. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Durchsucht nur Snapshots, die genau diesen Pfad gesichert haben (`--path`); mehrfach möglich. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Durchsucht nur Snapshots, die alle diese Tags tragen (`--tag`); mehrere Aufrufe sind ODER-verknüpft. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Sucht nur in diesem Snapshot (`--snapshot`); mehrfach möglich. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Ignoriert Groß-/Kleinschreibung der Muster (`--ignore-case`). */
        @JvmOverloads
        public fun ignoreCase(enabled: Boolean = true): Builder = apply { ignoreCase = enabled }

        /** Liefert Details wie Größe, Rechte und Änderungszeit in [FindMatch] (`--long`). */
        @JvmOverloads
        public fun long(enabled: Boolean = true): Builder = apply { long = enabled }

        /** Gibt Größen menschenlesbar aus (`--human-readable`); betrifft nur die Textausgabe von restic. */
        @JvmOverloads
        public fun humanReadable(enabled: Boolean = true): Builder = apply { humanReadable = enabled }

        /** Neuester Änderungszeitpunkt, Format `YYYY-MM-DD[ HH:MM[:SS]]` (`--newest`). */
        public fun newest(dateTime: String): Builder = apply { newest = dateTime }

        /** Ältester Änderungszeitpunkt, Format `YYYY-MM-DD[ HH:MM[:SS]]` (`--oldest`). */
        public fun oldest(dateTime: String): Builder = apply { oldest = dateTime }

        /** Durchsucht die Snapshots vom ältesten zum neuesten statt umgekehrt (`--reverse`). */
        @JvmOverloads
        public fun reverse(enabled: Boolean = true): Builder = apply { reverse = enabled }

        /** Nennt bei der Suche nach Blobs auch das Pack, in dem sie liegen (`--show-pack-id`). */
        @JvmOverloads
        public fun showPackId(enabled: Boolean = true): Builder = apply { showPackId = enabled }

        /** Bricht den Aufruf ab; ein direkt an [Restic] übergebenes Token hat Vorrang. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): FindOptions = FindOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: durchsucht alle Snapshots. */
        @JvmField
        public val DEFAULT: FindOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Ein Treffer einer Namenssuche.
 *
 * @property path Pfad im Snapshot.
 * @property type `file`, `dir` oder ein anderer Dateityp; nur mit [FindOptions.Builder.long].
 * @property size Größe in Bytes; nur mit [FindOptions.Builder.long].
 * @property permissions Rechte wie `-rw-r--r--`; nur mit [FindOptions.Builder.long].
 * @property modificationTime Letzte Änderung; nur mit [FindOptions.Builder.long].
 */
public data class FindMatch(
    val path: String,
    val type: String?,
    val size: Long?,
    val permissions: String?,
    val modificationTime: Instant?,
)

/**
 * Treffer einer Namenssuche in einem Snapshot.
 *
 * @property snapshotId ID des Snapshots.
 * @property hits Anzahl der Treffer.
 * @property matches Die Treffer.
 */
public data class FindSnapshotMatches(
    val snapshotId: String,
    val hits: Long,
    val matches: List<FindMatch>,
)

/**
 * Fundstelle eines Blobs, Trees oder Pack-Inhalts.
 *
 * @property objectType `blob`, `tree` oder `pack`.
 * @property id ID des gesuchten Objekts.
 * @property path Pfad der Datei oder des Verzeichnisses, das das Objekt enthält.
 * @property parentTree ID des Trees, in dem die Fundstelle liegt.
 * @property snapshotId ID des Snapshots.
 * @property time Zeitpunkt des Snapshots.
 */
public data class FindObject(
    val objectType: String,
    val id: String,
    val path: String?,
    val parentTree: String?,
    val snapshotId: String,
    val time: Instant?,
)

internal class FindCommand(
    private val runner: ProcessRunner,
) {
    fun findNames(
        patterns: List<String>,
        options: FindOptions,
    ): List<FindSnapshotMatches> =
        readDocument(options.arguments() + patterns).map { node ->
            FindSnapshotMatches(
                snapshotId = node.text("snapshot"),
                hits = node.long("hits"),
                matches = node.get("matches")?.mapElements(::matchFrom).orEmpty(),
            )
        }

    fun findObjects(
        kind: FindObjectKind,
        ids: List<String>,
        options: FindOptions,
    ): List<FindObject> =
        readDocument(options.arguments() + kind.flag + ids).map { node ->
            FindObject(
                objectType = node.text("object_type"),
                id = node.text("id"),
                path = node.textOrNull("path"),
                parentTree = node.textOrNull("parent_tree"),
                snapshotId = node.text("snapshot"),
                time = node.instantOrNull("time"),
            )
        }

    private fun matchFrom(node: JsonNode) =
        FindMatch(
            path = node.text("path"),
            type = node.textOrNull("type"),
            size = node.longOrNull("size"),
            permissions = node.textOrNull("permissions"),
            modificationTime = node.instantOrNull("mtime"),
        )

    private fun readDocument(arguments: List<String>): List<JsonNode> {
        val lines = mutableListOf<String>()
        runner.runChecked(listOf("find", "--json") + arguments) { lines += it }
        return Json.arrayElements(lines)
    }
}
