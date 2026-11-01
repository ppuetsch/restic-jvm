package io.github.ppuetsch.restic

import com.fasterxml.jackson.databind.JsonNode
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path

public enum class ArchiveFormat(internal val cliValue: String) {
    TAR("tar"),
    ZIP("zip"),
}

/** Optionen für `restic dump`. */
public class DumpOptions private constructor(builder: Builder) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val archiveFormat: ArchiveFormat? = builder.archiveFormat

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalValue("--archive", archiveFormat?.cliValue)
        }

    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var archiveFormat: ArchiveFormat? = null

        /** Wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Archivformat, wenn ein Verzeichnis ausgegeben wird (restic-Standard: tar). */
        public fun archiveFormat(format: ArchiveFormat): Builder = apply { archiveFormat = format }

        public fun build(): DumpOptions = DumpOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: DumpOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Objekttypen, die `restic cat` ausgeben kann. */
public enum class CatObjectType(internal val cliValue: String, internal val needsId: Boolean) {
    MASTERKEY("masterkey", false),
    CONFIG("config", false),
    PACK("pack", true),
    BLOB("blob", true),
    SNAPSHOT("snapshot", true),
    INDEX("index", true),
    KEY("key", true),
    LOCK("lock", true),

    /** ID hat die Form `snapshotId:unterordner`. */
    TREE("tree", true),
}

private const val SHORT_ID_LENGTH: Int = 8

internal class DumpAndCatCommand(private val runner: ProcessRunner) {
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
