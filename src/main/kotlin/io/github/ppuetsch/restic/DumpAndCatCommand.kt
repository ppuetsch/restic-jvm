package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/** Object types `restic cat` can print; see [CatCommands.raw] and [CatCommands.text]. */
public enum class CatObjectType(
    internal val cliValue: String,
    internal val needsId: Boolean,
) {
    /** The decrypted master key; needs no ID. */
    MASTERKEY("masterkey", false),

    /** The repository configuration; needs no ID. */
    CONFIG("config", false),

    /** A pack file, raw. */
    PACK("pack", true),

    /** A single blob, decrypted. */
    BLOB("blob", true),

    /** A snapshot as JSON. */
    SNAPSHOT("snapshot", true),

    /** An index file as JSON. */
    INDEX("index", true),

    /** A key file as JSON. */
    KEY("key", true),

    /** A lock file as JSON. */
    LOCK("lock", true),

    /** A directory as JSON; the ID has the form `snapshotId:subfolder`. */
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
