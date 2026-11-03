package io.github.ppuetsch.restic

/**
 * Options for [MaintenanceCommands.prune] (`restic prune`).
 *
 * @property tuning Trade-off between freed space and data to repack.
 * @property dryRun Only show what would happen (`--dry-run`).
 * @property unsafeRecoverNoFreeSpace Emergency mode for a full backend (`--unsafe-recover-no-free-space`).
 * @property cancellationToken Token that cancels the call.
 */
public class PruneOptions private constructor(
    builder: Builder,
) {
    public val tuning: PruneTuning = builder.tuning.build()
    public val dryRun: Boolean = builder.dryRun
    public val unsafeRecoverNoFreeSpace: String? = builder.unsafeRecoverNoFreeSpace
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            addAll(tuning.arguments())
            optionalFlag("--dry-run", dryRun)
            optionalValue("--unsafe-recover-no-free-space", unsafeRecoverNoFreeSpace)
        }

    /** Builds [PruneOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal val tuning: PruneTuning.Builder = PruneTuning.builder()
        internal var dryRun: Boolean = false
        internal var unsafeRecoverNoFreeSpace: String? = null
        internal var cancellationToken: CancellationToken? = null

        /** Only shows what would be removed and repacked (`--dry-run`). */
        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /** Allowed share of unused data, see [PruneTuning.Builder.maxUnused]. */
        public fun maxUnused(limit: String): Builder = apply { tuning.maxUnused(limit) }

        /** Upper limit of data to repack, see [PruneTuning.Builder.maxRepackSize]. */
        public fun maxRepackSize(size: String): Builder = apply { tuning.maxRepackSize(size) }

        /** Repacks only cacheable packs, see [PruneTuning.Builder.repackCacheableOnly]. */
        @JvmOverloads
        public fun repackCacheableOnly(enabled: Boolean = true): Builder = apply { tuning.repackCacheableOnly(enabled) }

        /** Deprecated since restic 0.19, see [PruneTuning.Builder.repackSmall]. */
        @JvmOverloads
        public fun repackSmall(enabled: Boolean = true): Builder = apply { tuning.repackSmall(enabled) }

        /** Repacks packs below this size, see [PruneTuning.Builder.repackSmallerThan]. */
        public fun repackSmallerThan(size: String): Builder = apply { tuning.repackSmallerThan(size) }

        /** Compresses uncompressed data, see [PruneTuning.Builder.repackUncompressed]. */
        @JvmOverloads
        public fun repackUncompressed(enabled: Boolean = true): Builder = apply { tuning.repackUncompressed(enabled) }

        /** UNSAFE: read the restic documentation before using this. */
        public fun unsafeRecoverNoFreeSpace(argument: String): Builder = apply { unsafeRecoverNoFreeSpace = argument }

        /** Token that cancels the call from another thread. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): PruneOptions = PruneOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: restic's default behaviour. */
        @JvmField
        public val DEFAULT: PruneOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
