package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime

/**
 * Options for [MaintenanceCommands.cache] (`restic cache`).
 *
 * @property cleanup Removes old cache directories (`--cleanup`).
 * @property maxAgeDays Age in days from which a cache counts as old (`--max-age`).
 * @property noSize Skips computing the sizes (`--no-size`).
 */
public class CacheOptions private constructor(
    builder: Builder,
) {
    public val cleanup: Boolean = builder.cleanup
    public val maxAgeDays: Int? = builder.maxAgeDays
    public val noSize: Boolean = builder.noSize

    internal fun arguments(): List<String> =
        buildList {
            optionalFlag("--cleanup", cleanup)
            optionalValue("--max-age", maxAgeDays)
            optionalFlag("--no-size", noSize)
        }

    /** Builds [CacheOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal var cleanup: Boolean = false
        internal var maxAgeDays: Int? = null
        internal var noSize: Boolean = false

        /** Removes old cache directories (`--cleanup`). */
        @JvmOverloads
        public fun cleanup(enabled: Boolean = true): Builder = apply { cleanup = enabled }

        /** Age in days from which a cache counts as old (`--max-age`); restic's default is 30. */
        public fun maxAgeDays(days: Int): Builder = apply { maxAgeDays = days }

        /** Skips computing the cache sizes, which saves time with large caches (`--no-size`). */
        @JvmOverloads
        public fun noSize(enabled: Boolean = true): Builder = apply { noSize = enabled }

        /** Creates the immutable options. */
        public fun build(): CacheOptions = CacheOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: lists the caches. */
        @JvmField
        public val DEFAULT: CacheOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
