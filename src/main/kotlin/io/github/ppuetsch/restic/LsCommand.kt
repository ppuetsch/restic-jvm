package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Instant

/** Sortierung der Einträge von [Restic.ls] (`--sort`). */
public enum class LsSort(
    internal val cliValue: String,
) {
    /** Nach Name; Standard von restic. */
    NAME("name"),

    /** Nach Größe. */
    SIZE("size"),

    /** Nach Änderungszeit. */
    MODIFICATION_TIME("time"),

    /** Nach Zugriffszeit. */
    ACCESS_TIME("atime"),

    /** Nach Zeit der letzten Statusänderung (ctime). */
    CHANGE_TIME("ctime"),

    /** Nach Dateiendung. */
    EXTENSION("extension"),
}

/**
 * Optionen für [Restic.ls] und [Restic.listNodes] (`restic ls`).
 *
 * @property cancellationToken Token, über das sich der Aufruf abbrechen lässt.
 * @property filter Auswahl des Snapshots, wenn die ID `latest` ist.
 * @property directories Verzeichnisse innerhalb des Snapshots, auf die sich die Auflistung beschränkt.
 * @property recursive Listet auch Unterverzeichnisse der angegebenen [directories] (`--recursive`).
 * @property reverse Kehrt die Sortierung um (`--reverse`).
 * @property sort Sortierung (`--sort`).
 * @property long Ausführliches Format (`--long`).
 * @property humanReadable Größen menschenlesbar (`--human-readable`).
 */
public class LsOptions private constructor(
    builder: Builder,
) {
    public val cancellationToken: CancellationToken? = builder.cancellationToken
    public val filter: SnapshotFilter = builder.filter.build()
    public val directories: List<String> = builder.directories.toList()
    public val recursive: Boolean = builder.recursive
    public val reverse: Boolean = builder.reverse
    public val sort: LsSort? = builder.sort
    public val long: Boolean = builder.long
    public val humanReadable: Boolean = builder.humanReadable

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalFlag("--recursive", recursive)
            optionalFlag("--reverse", reverse)
            optionalValue("--sort", sort?.cliValue)
            optionalFlag("--long", long)
            optionalFlag("--human-readable", humanReadable)
        }

    /** Stellt [LsOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal var cancellationToken: CancellationToken? = null
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val directories: MutableList<String> = mutableListOf()
        internal var recursive: Boolean = false
        internal var reverse: Boolean = false
        internal var sort: LsSort? = null
        internal var long: Boolean = false
        internal var humanReadable: Boolean = false

        /** Wählt den neuesten Snapshot dieses Hosts (`--host`); wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Wählt den neuesten Snapshot mit diesem Pfad (`--path`); wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Wählt den neuesten Snapshot mit diesen Tags (`--tag`); wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Verzeichnis innerhalb des Snapshots, das aufgelistet werden soll. */
        public fun directory(pathInSnapshot: String): Builder = apply { directories.add(pathInSnapshot) }

        /** Listet auch die Unterverzeichnisse der mit [directory] gewählten Verzeichnisse (`--recursive`). */
        @JvmOverloads
        public fun recursive(enabled: Boolean = true): Builder = apply { recursive = enabled }

        /** Kehrt die Sortierung um (`--reverse`). */
        @JvmOverloads
        public fun reverse(enabled: Boolean = true): Builder = apply { reverse = enabled }

        /** Sortierung der Einträge (`--sort`). */
        public fun sort(sort: LsSort): Builder = apply { this.sort = sort }

        /** Ausführliches Format (`--long`); betrifft nur die Textausgabe, [LsNode] ist immer vollständig. */
        @JvmOverloads
        public fun long(enabled: Boolean = true): Builder = apply { long = enabled }

        /** Größen menschenlesbar (`--human-readable`); betrifft nur die Textausgabe von restic. */
        @JvmOverloads
        public fun humanReadable(enabled: Boolean = true): Builder = apply { humanReadable = enabled }

        /** Bricht den Aufruf ab; ein direkt an [Restic] übergebenes Token hat Vorrang. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): LsOptions = LsOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: listet den ganzen Snapshot. */
        @JvmField
        public val DEFAULT: LsOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Ein Eintrag eines Snapshots: Datei, Verzeichnis oder anderer Dateityp.
 *
 * @property name Name ohne Pfad.
 * @property type `file`, `dir`, `symlink` oder ein anderer Dateityp.
 * @property path Vollständiger Pfad im Snapshot.
 * @property size Größe in Bytes, nur bei Dateien.
 * @property mode Unix-Dateimodus.
 * @property permissions Rechte wie `-rw-r--r--`.
 * @property uid Benutzer-ID des Eigentümers.
 * @property gid Gruppen-ID des Eigentümers.
 * @property user Name des Eigentümers.
 * @property group Name der Gruppe.
 * @property inode Inode-Nummer zum Zeitpunkt des Backups.
 * @property links Anzahl der Hardlinks.
 * @property modificationTime Letzte Änderung (mtime).
 * @property accessTime Letzter Zugriff (atime).
 * @property changeTime Letzte Statusänderung (ctime).
 */
public data class LsNode(
    val name: String,
    val type: String,
    val path: String,
    val size: Long?,
    val mode: Long?,
    val permissions: String?,
    val uid: Long?,
    val gid: Long?,
    val user: String?,
    val group: String?,
    val inode: Long?,
    val links: Long?,
    val modificationTime: Instant?,
    val accessTime: Instant?,
    val changeTime: Instant?,
) {
    /** `true` bei einem Verzeichnis. */
    public fun isDirectory(): Boolean = type == "dir"

    /** `true` bei einer regulären Datei. */
    public fun isFile(): Boolean = type == "file"
}

/** Empfängt die Einträge eines laufenden [Restic.ls]; wird im Thread aufgerufen, der die restic-Ausgabe liest. */
public fun interface LsListener {
    /** Ein Eintrag des Snapshots. */
    public fun onNode(node: LsNode)
}

/**
 * Ergebnis von [Restic.listNodes].
 *
 * @property snapshot Der aufgelistete Snapshot.
 * @property nodes Alle Einträge in Ausgabereihenfolge.
 */
public data class LsResult(
    val snapshot: Snapshot,
    val nodes: List<LsNode>,
)

private fun lsNodeFrom(node: JsonNode) =
    LsNode(
        name = node.text("name"),
        type = node.text("type"),
        path = node.text("path"),
        size = node.longOrNull("size"),
        mode = node.longOrNull("mode"),
        permissions = node.textOrNull("permissions"),
        uid = node.longOrNull("uid"),
        gid = node.longOrNull("gid"),
        user = node.textOrNull("user"),
        group = node.textOrNull("group"),
        inode = node.longOrNull("inode"),
        links = node.longOrNull("links"),
        modificationTime = node.instantOrNull("mtime"),
        accessTime = node.instantOrNull("atime"),
        changeTime = node.instantOrNull("ctime"),
    )

internal class LsCommand(
    private val runner: ProcessRunner,
) {
    fun ls(
        snapshotId: String,
        options: LsOptions,
        listener: LsListener?,
    ): Snapshot {
        var snapshot: Snapshot? = null
        val arguments = listOf("ls", "--json") + options.arguments() + snapshotId + options.directories
        runner.runChecked(arguments) { line ->
            Json.parseOrNull(line)?.let { node ->
                when (node.get("struct_type")?.asString() ?: node.messageType()) {
                    "snapshot" -> snapshot = snapshotFrom(node)
                    "node" -> listener?.onNode(lsNodeFrom(node))
                }
            }
        }
        return snapshot ?: throw ResticOutputException("restic ls produced no snapshot header", null)
    }
}
