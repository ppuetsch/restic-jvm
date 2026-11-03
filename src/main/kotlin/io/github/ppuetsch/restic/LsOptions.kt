package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Instant

/** Sort order of the entries of [FileCommands.ls] (`--sort`). */
public enum class LsSort(
    internal val cliValue: String,
) {
    /** By name; restic's default. */
    NAME("name"),

    /** By size. */
    SIZE("size"),

    /** By modification time. */
    MODIFICATION_TIME("time"),

    /** By access time. */
    ACCESS_TIME("atime"),

    /** By time of the last status change (ctime). */
    CHANGE_TIME("ctime"),

    /** By file extension. */
    EXTENSION("extension"),
}

/**
 * Options for [FileCommands.ls] and [FileCommands.listNodes] (`restic ls`).
 *
 * @property cancellationToken Token that cancels the call.
 * @property filter Selects the snapshot when the ID is `latest`.
 * @property directories Directories inside the snapshot the listing is restricted to.
 * @property recursive Also lists subdirectories of the given [directories] (`--recursive`).
 * @property reverse Reverses the sort order (`--reverse`).
 * @property sort Sort order (`--sort`).
 * @property long Long format (`--long`).
 * @property humanReadable Human-readable sizes (`--human-readable`).
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

    /** Builds [LsOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal var cancellationToken: CancellationToken? = null
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val directories: MutableList<String> = mutableListOf()
        internal var recursive: Boolean = false
        internal var reverse: Boolean = false
        internal var sort: LsSort? = null
        internal var long: Boolean = false
        internal var humanReadable: Boolean = false

        /** Picks the newest snapshot of this host (`--host`); only has an effect when the snapshot ID is `latest`. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Picks the newest snapshot with this path (`--path`); only has an effect when the snapshot ID is `latest`. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Picks the newest snapshot with these tags (`--tag`); only has an effect when the snapshot ID is `latest`. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Directory inside the snapshot to list. */
        public fun directory(pathInSnapshot: String): Builder = apply { directories.add(pathInSnapshot) }

        /** Also lists the subdirectories of the directories chosen with [directory] (`--recursive`). */
        @JvmOverloads
        public fun recursive(enabled: Boolean = true): Builder = apply { recursive = enabled }

        /** Reverses the sort order (`--reverse`). */
        @JvmOverloads
        public fun reverse(enabled: Boolean = true): Builder = apply { reverse = enabled }

        /** Sort order of the entries (`--sort`). */
        public fun sort(sort: LsSort): Builder = apply { this.sort = sort }

        /** Long format (`--long`); only affects the text output, [LsNode] is always complete. */
        @JvmOverloads
        public fun long(enabled: Boolean = true): Builder = apply { long = enabled }

        /** Human-readable sizes (`--human-readable`); only affects restic's text output. */
        @JvmOverloads
        public fun humanReadable(enabled: Boolean = true): Builder = apply { humanReadable = enabled }

        /** Cancels the call; a token passed directly to [Restic] takes precedence. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): LsOptions = LsOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: lists the whole snapshot. */
        @JvmField
        public val DEFAULT: LsOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
