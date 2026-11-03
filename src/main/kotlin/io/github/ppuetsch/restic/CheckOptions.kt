package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/**
 * Options for [MaintenanceCommands.check] (`restic check`).
 *
 * @property readData Reads and verifies all data blobs (`--read-data`).
 * @property readDataSubset Reads and verifies only part of the data (`--read-data-subset`).
 * @property withCache Uses the local cache instead of a fresh one (`--with-cache`).
 * @property cancellationToken Token that cancels the call.
 */
public class CheckOptions private constructor(
    builder: Builder,
) {
    public val readData: Boolean = builder.readData
    public val readDataSubset: String? = builder.readDataSubset
    public val withCache: Boolean = builder.withCache
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            optionalFlag("--read-data", readData)
            optionalValue("--read-data-subset", readDataSubset)
            optionalFlag("--with-cache", withCache)
        }

    /** Builds [CheckOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal var readData: Boolean = false
        internal var readDataSubset: String? = null
        internal var withCache: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        /** Reads all data blobs and verifies their checksums (`--read-data`); downloads the whole repository. */
        @JvmOverloads
        public fun readData(enabled: Boolean = true): Builder = apply { readData = enabled }

        /**
         * Reads and verifies only part of the data (`--read-data-subset`): `n/t` for the n-th of t parts,
         * `x%` or `x.y%` for a random share, or a size with a suffix such as `500M`.
         */
        public fun readDataSubset(subset: String): Builder = apply { readDataSubset = subset }

        /** Uses the existing local cache instead of checking with a fresh one (`--with-cache`); faster, but less thorough. */
        @JvmOverloads
        public fun withCache(enabled: Boolean = true): Builder = apply { withCache = enabled }

        /** Token that cancels the call from another thread. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): CheckOptions = CheckOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: restic's default behaviour. */
        @JvmField
        public val DEFAULT: CheckOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
