package io.github.ppuetsch.restic

/**
 * Settings shared by `restic prune` and `restic forget --prune`: they trade freed space against the amount of data to
 * repack.
 *
 * @property maxUnused Allowed share of unused data (`--max-unused`).
 * @property maxRepackSize Upper limit of data to repack (`--max-repack-size`).
 * @property repackCacheableOnly Repacks only packs with tree blobs (`--repack-cacheable-only`).
 * @property repackSmall Repacks small packs (`--repack-small`); has no effect since restic 0.19.
 * @property repackSmallerThan Repacks packs below this size (`--repack-smaller-than`).
 * @property repackUncompressed Compresses data that is still uncompressed (`--repack-uncompressed`).
 */
public class PruneTuning private constructor(
    builder: Builder,
) {
    public val maxUnused: String? = builder.maxUnused
    public val maxRepackSize: String? = builder.maxRepackSize
    public val repackCacheableOnly: Boolean = builder.repackCacheableOnly
    public val repackSmall: Boolean = builder.repackSmall
    public val repackSmallerThan: String? = builder.repackSmallerThan
    public val repackUncompressed: Boolean = builder.repackUncompressed

    internal fun arguments(): List<String> =
        buildList {
            optionalValue("--max-unused", maxUnused)
            optionalValue("--max-repack-size", maxRepackSize)
            optionalFlag("--repack-cacheable-only", repackCacheableOnly)
            optionalFlag("--repack-small", repackSmall)
            optionalValue("--repack-smaller-than", repackSmallerThan)
            optionalFlag("--repack-uncompressed", repackUncompressed)
        }

    /** Builds [PruneTuning]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal var maxUnused: String? = null
        internal var maxRepackSize: String? = null
        internal var repackCacheableOnly: Boolean = false
        internal var repackSmall: Boolean = false
        internal var repackSmallerThan: String? = null
        internal var repackUncompressed: Boolean = false

        /** Allowed share of unused data: bytes with a suffix (`5G`) or a percentage (`5%`); `unlimited` is allowed. */
        public fun maxUnused(limit: String): Builder = apply { maxUnused = limit }

        /** Upper limit of the data to repack, a size with a suffix such as `10G` (`--max-repack-size`). */
        public fun maxRepackSize(size: String): Builder = apply { maxRepackSize = size }

        /** Repacks only packs with tree blobs, which are in the cache (`--repack-cacheable-only`); saves downloads. */
        @JvmOverloads
        public fun repackCacheableOnly(enabled: Boolean = true): Builder = apply { repackCacheableOnly = enabled }

        /** Deprecated since restic 0.19, which always repacks small packs; see [repackSmallerThan]. */
        @JvmOverloads
        public fun repackSmall(enabled: Boolean = true): Builder = apply { repackSmall = enabled }

        /** Repacks packs below this size, for example `5M` (`--repack-smaller-than`). */
        public fun repackSmallerThan(size: String): Builder = apply { repackSmallerThan = size }

        /** Compresses data that is still uncompressed, for example after migrating to format 2 (`--repack-uncompressed`). */
        @JvmOverloads
        public fun repackUncompressed(enabled: Boolean = true): Builder = apply { repackUncompressed = enabled }

        /** Creates the immutable settings. */
        public fun build(): PruneTuning = PruneTuning(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: restic's default behaviour. */
        @JvmField
        public val DEFAULT: PruneTuning = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * `restic prune` has no JSON output; the figures are read from the text summary and are `null` when restic does not
 * print them (for example if future versions word them differently). Sizes come from figures such as "1.234 KiB"
 * and are therefore rounded to the displayed precision.
 *
 * @property outputLines The complete text output.
 * @property packsToKeep Packs that stay unchanged.
 * @property packsRepacked Packs that were repacked.
 * @property packsDeleted Packs that were deleted.
 * @property bytesFreed Freed space in bytes.
 */
public data class PruneResult
    @JvmOverloads
    constructor(
        val outputLines: List<String>,
        val packsToKeep: Int? = null,
        val packsRepacked: Int? = null,
        val packsDeleted: Int? = null,
        val bytesFreed: Long? = null,
    )

/** Receives a command's output line by line while it runs. */
public fun interface OutputLineListener {
    /** A line of standard output. */
    public fun onLine(line: String)

    /** A line of error output; ignored by default. */
    public fun onErrorLine(line: String) {
        // Intentionally empty: whoever needs only standard output implements onLine alone.
    }
}
