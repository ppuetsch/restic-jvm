package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/** Archive format in which [FileCommands.dump] writes a directory (`--archive`). */
public enum class ArchiveFormat(
    internal val cliValue: String,
) {
    /** Tar archive; restic's default. */
    TAR("tar"),

    /** Zip archive. */
    ZIP("zip"),
}

/**
 * Options for [FileCommands.dump] and [FileCommands.dumpToFile] (`restic dump`).
 *
 * @property filter Selects the snapshot when the ID is `latest`.
 * @property archiveFormat Archive format for directories (`--archive`).
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

    /** Builds [DumpOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var archiveFormat: ArchiveFormat? = null

        /** Picks the newest snapshot of this host (`--host`); only has an effect when the snapshot ID is `latest`. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Picks the newest snapshot with this path (`--path`); only has an effect when the snapshot ID is `latest`. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Picks the newest snapshot with these tags (`--tag`); only has an effect when the snapshot ID is `latest`. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Archive format when a directory is written (`--archive`, restic's default: tar). */
        public fun archiveFormat(format: ArchiveFormat): Builder = apply { archiveFormat = format }

        /** Creates the immutable options. */
        public fun build(): DumpOptions = DumpOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: restic's default behaviour. */
        @JvmField
        public val DEFAULT: DumpOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
