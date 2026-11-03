package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Instant

/** Kind of objects [Restic.findObjects] searches for. */
public enum class FindObjectKind(
    internal val flag: String,
) {
    /** A data blob (`--blob`). */
    BLOB("--blob"),

    /** A tree blob, that is a directory (`--tree`). */
    TREE("--tree"),

    /** A pack file; finds the files whose blobs are stored in it (`--pack`). */
    PACK("--pack"),
}

/**
 * Options for [Restic.find] and [Restic.findObjects] (`restic find`).
 *
 * @property cancellationToken Token that cancels the call.
 * @property filter Selects snapshots by host, path and tags.
 * @property snapshotIds Searches only these snapshots (`--snapshot`).
 * @property ignoreCase Ignores the case of the patterns (`--ignore-case`).
 * @property long Returns details such as size and permissions (`--long`).
 * @property humanReadable Human-readable sizes (`--human-readable`).
 * @property newest Only entries modified up to this time (`--newest`).
 * @property oldest Only entries modified from this time on (`--oldest`).
 * @property reverse Searches the snapshots from oldest to newest (`--reverse`).
 * @property showPackId Also names the pack when searching for blobs (`--show-pack-id`).
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

    /** Builds [FindOptions]; every method sets one flag and returns the builder. */
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

        /** Searches only snapshots of this host (`--host`); may be repeated. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Searches only snapshots that backed up exactly this path (`--path`); may be repeated. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Searches only snapshots carrying all of these tags (`--tag`); several calls are combined with OR. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Searches only this snapshot (`--snapshot`); may be repeated. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Ignores the case of the patterns (`--ignore-case`). */
        @JvmOverloads
        public fun ignoreCase(enabled: Boolean = true): Builder = apply { ignoreCase = enabled }

        /** Returns details such as size, permissions and modification time in [FindMatch] (`--long`). */
        @JvmOverloads
        public fun long(enabled: Boolean = true): Builder = apply { long = enabled }

        /** Prints sizes in human-readable form (`--human-readable`); only affects restic's text output. */
        @JvmOverloads
        public fun humanReadable(enabled: Boolean = true): Builder = apply { humanReadable = enabled }

        /** Latest modification time, format `YYYY-MM-DD[ HH:MM[:SS]]` (`--newest`). */
        public fun newest(dateTime: String): Builder = apply { newest = dateTime }

        /** Earliest modification time, format `YYYY-MM-DD[ HH:MM[:SS]]` (`--oldest`). */
        public fun oldest(dateTime: String): Builder = apply { oldest = dateTime }

        /** Searches the snapshots from oldest to newest instead of the other way round (`--reverse`). */
        @JvmOverloads
        public fun reverse(enabled: Boolean = true): Builder = apply { reverse = enabled }

        /** When searching for blobs, also names the pack they are stored in (`--show-pack-id`). */
        @JvmOverloads
        public fun showPackId(enabled: Boolean = true): Builder = apply { showPackId = enabled }

        /** Cancels the call; a token passed directly to [Restic] takes precedence. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): FindOptions = FindOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: searches all snapshots. */
        @JvmField
        public val DEFAULT: FindOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
