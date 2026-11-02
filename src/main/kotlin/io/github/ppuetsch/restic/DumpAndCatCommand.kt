package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/** Archivformat, in dem [Restic.dump] ein Verzeichnis ausgibt (`--archive`). */
public enum class ArchiveFormat(
    internal val cliValue: String,
) {
    /** Tar-Archiv; Standard von restic. */
    TAR("tar"),

    /** Zip-Archiv. */
    ZIP("zip"),
}

/**
 * Optionen für [Restic.dump] und [Restic.dumpToFile] (`restic dump`).
 *
 * @property filter Auswahl des Snapshots, wenn die ID `latest` ist.
 * @property archiveFormat Archivformat für Verzeichnisse (`--archive`).
 */
public class DumpOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val archiveFormat: ArchiveFormat? = builder.archiveFormat

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalValue("--archive", archiveFormat?.cliValue)
        }

    /** Stellt [DumpOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var archiveFormat: ArchiveFormat? = null

        /** Wählt den neuesten Snapshot dieses Hosts (`--host`); wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Wählt den neuesten Snapshot mit diesem Pfad (`--path`); wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Wählt den neuesten Snapshot mit diesen Tags (`--tag`); wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Archivformat, wenn ein Verzeichnis ausgegeben wird (`--archive`, restic-Standard: tar). */
        public fun archiveFormat(format: ArchiveFormat): Builder = apply { archiveFormat = format }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): DumpOptions = DumpOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: restic-Standardverhalten. */
        @JvmField
        public val DEFAULT: DumpOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Objekttypen, die `restic cat` ausgeben kann; siehe [Restic.cat] und [Restic.catText]. */
public enum class CatObjectType(
    internal val cliValue: String,
    internal val needsId: Boolean,
) {
    /** Der entschlüsselte Hauptschlüssel; braucht keine ID. */
    MASTERKEY("masterkey", false),

    /** Die Repository-Konfiguration; braucht keine ID. */
    CONFIG("config", false),

    /** Eine Pack-Datei, roh. */
    PACK("pack", true),

    /** Ein einzelner Blob, entschlüsselt. */
    BLOB("blob", true),

    /** Ein Snapshot als JSON. */
    SNAPSHOT("snapshot", true),

    /** Eine Indexdatei als JSON. */
    INDEX("index", true),

    /** Eine Schlüsseldatei als JSON. */
    KEY("key", true),

    /** Eine Sperrdatei als JSON. */
    LOCK("lock", true),

    /** Ein Verzeichnis als JSON; die ID hat die Form `snapshotId:unterordner`. */
    TREE("tree", true),
}

private const val SHORT_ID_LENGTH: Int = 8

internal class DumpAndCatCommand(
    private val runner: ProcessRunner,
) {
    fun dump(
        snapshotId: String,
        file: String,
        options: DumpOptions,
        output: OutputStream,
    ) {
        runner.runChecked(ProcessRequest(listOf("dump") + options.arguments() + snapshotId + file, standardOutput = output))
    }

    fun dumpToFile(
        snapshotId: String,
        file: String,
        target: Path,
        options: DumpOptions,
    ) {
        runner.runChecked(listOf("dump", "--target", target.toString()) + options.arguments() + snapshotId + file)
    }

    fun cat(
        type: CatObjectType,
        id: String?,
        output: OutputStream,
    ) {
        require(!type.needsId || !id.isNullOrEmpty()) { "${type.cliValue} requires an id" }
        val arguments = listOf("cat", type.cliValue) + listOfNotNull(id)
        runner.runChecked(ProcessRequest(arguments, standardOutput = output))
    }

    fun catJson(
        type: CatObjectType,
        id: String?,
    ): JsonNode = Json.parse(catText(type, id))

    fun catSnapshot(id: String): Snapshot =
        snapshotFrom(catJson(CatObjectType.SNAPSHOT, id)).let { it.copy(id = id, shortId = id.take(SHORT_ID_LENGTH)) }

    fun catText(
        type: CatObjectType,
        id: String?,
    ): String {
        val buffer = ByteArrayOutputStream()
        cat(type, id, buffer)
        return buffer.toString(StandardCharsets.UTF_8)
    }
}
