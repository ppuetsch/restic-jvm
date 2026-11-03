package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/**
 * Options for [Restic.forget] (`restic forget`). Without a keep rule and without snapshot IDs restic rejects the
 * call, so that nothing is deleted by accident.
 *
 * The `keep*` rules apply per group (see [groupBy]) and are combined with OR: a snapshot stays as soon as one rule
 * wants to keep it.
 *
 * @property filter Selects snapshots by host, path and tags.
 * @property snapshotIds Snapshots to remove explicitly.
 * @property keepLast Keeps the last n snapshots (`--keep-last`).
 * @property keepHourly Keeps the last one per hour for n hours (`--keep-hourly`).
 * @property keepDaily Keeps the last one per day for n days (`--keep-daily`).
 * @property keepWeekly Keeps the last one per week for n weeks (`--keep-weekly`).
 * @property keepMonthly Keeps the last one per month for n months (`--keep-monthly`).
 * @property keepYearly Keeps the last one per year for n years (`--keep-yearly`).
 * @property keepWithin Keeps all snapshots within this period (`--keep-within`).
 * @property keepWithinHourly Keeps hourly snapshots within this period (`--keep-within-hourly`).
 * @property keepWithinDaily Keeps daily snapshots within this period (`--keep-within-daily`).
 * @property keepWithinWeekly Keeps weekly snapshots within this period (`--keep-within-weekly`).
 * @property keepWithinMonthly Keeps monthly snapshots within this period (`--keep-within-monthly`).
 * @property keepWithinYearly Keeps yearly snapshots within this period (`--keep-within-yearly`).
 * @property keepTagGroups Keeps snapshots with these tags (`--keep-tag`); each group must match completely.
 * @property unsafeAllowRemoveAll Allows removing all snapshots (`--unsafe-allow-remove-all`).
 * @property groupBy Grouping within which the rules apply (`--group-by`).
 * @property compact Shorter output (`--compact`).
 * @property dryRun Only show what would be removed (`--dry-run`).
 * @property prune Runs `prune` afterwards (`--prune`).
 * @property tuning Settings of the subsequent prune run.
 * @property cancellationToken Token that cancels the call.
 */
public class ForgetOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val snapshotIds: List<String> = builder.snapshotIds.toList()
    public val keepLast: String? = builder.keepLast
    public val keepHourly: String? = builder.keepHourly
    public val keepDaily: String? = builder.keepDaily
    public val keepWeekly: String? = builder.keepWeekly
    public val keepMonthly: String? = builder.keepMonthly
    public val keepYearly: String? = builder.keepYearly
    public val keepWithin: String? = builder.keepWithin
    public val keepWithinHourly: String? = builder.keepWithinHourly
    public val keepWithinDaily: String? = builder.keepWithinDaily
    public val keepWithinWeekly: String? = builder.keepWithinWeekly
    public val keepWithinMonthly: String? = builder.keepWithinMonthly
    public val keepWithinYearly: String? = builder.keepWithinYearly
    public val keepTagGroups: List<List<String>> = builder.keepTagGroups.map { it.toList() }
    public val unsafeAllowRemoveAll: Boolean = builder.unsafeAllowRemoveAll
    public val groupBy: String? = builder.groupBy
    public val compact: Boolean = builder.compact
    public val dryRun: Boolean = builder.dryRun
    public val prune: Boolean = builder.prune
    public val tuning: PruneTuning = builder.tuning.build()
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalValue("--keep-last", keepLast)
            optionalValue("--keep-hourly", keepHourly)
            optionalValue("--keep-daily", keepDaily)
            optionalValue("--keep-weekly", keepWeekly)
            optionalValue("--keep-monthly", keepMonthly)
            optionalValue("--keep-yearly", keepYearly)
            optionalValue("--keep-within", keepWithin)
            optionalValue("--keep-within-hourly", keepWithinHourly)
            optionalValue("--keep-within-daily", keepWithinDaily)
            optionalValue("--keep-within-weekly", keepWithinWeekly)
            optionalValue("--keep-within-monthly", keepWithinMonthly)
            optionalValue("--keep-within-yearly", keepWithinYearly)
            keepTagGroups.forEach { optionalValue("--keep-tag", it.joinToString(",")) }
            optionalFlag("--unsafe-allow-remove-all", unsafeAllowRemoveAll)
            optionalValue("--group-by", groupBy)
            optionalFlag("--compact", compact)
            optionalFlag("--dry-run", dryRun)
            optionalFlag("--prune", prune)
            addAll(tuning.arguments())
            addAll(snapshotIds)
        }

    /** Builds [ForgetOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal var keepLast: String? = null
        internal var keepHourly: String? = null
        internal var keepDaily: String? = null
        internal var keepWeekly: String? = null
        internal var keepMonthly: String? = null
        internal var keepYearly: String? = null
        internal var keepWithin: String? = null
        internal var keepWithinHourly: String? = null
        internal var keepWithinDaily: String? = null
        internal var keepWithinWeekly: String? = null
        internal var keepWithinMonthly: String? = null
        internal var keepWithinYearly: String? = null
        internal val keepTagGroups: MutableList<List<String>> = mutableListOf()
        internal var unsafeAllowRemoveAll: Boolean = false
        internal var groupBy: String? = null
        internal var compact: Boolean = false
        internal var dryRun: Boolean = false
        internal var prune: Boolean = false
        internal val tuning: PruneTuning.Builder = PruneTuning.builder()
        internal var cancellationToken: CancellationToken? = null

        /** Applies the rules only to snapshots of this host (`--host`); may be repeated. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Applies the rules only to snapshots with exactly this path (`--path`); may be repeated. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Applies the rules only to snapshots carrying all of these tags (`--tag`); several calls are combined with OR. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Removes this snapshot explicitly (may be repeated). */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Keeps the last [count] snapshots (`--keep-last`). */
        public fun keepLast(count: Int): Builder = apply { keepLast = count.toString() }

        /** Keeps all snapshots (`--keep-last unlimited`). */
        public fun keepLastUnlimited(): Builder = apply { keepLast = UNLIMITED }

        /** Keeps the last snapshot per hour, for the last [count] hours that have snapshots (`--keep-hourly`). */
        public fun keepHourly(count: Int): Builder = apply { keepHourly = count.toString() }

        /** Keeps the last snapshot per hour, without limit (`--keep-hourly unlimited`). */
        public fun keepHourlyUnlimited(): Builder = apply { keepHourly = UNLIMITED }

        /** Keeps the last snapshot per day, for the last [count] days that have snapshots (`--keep-daily`). */
        public fun keepDaily(count: Int): Builder = apply { keepDaily = count.toString() }

        /** Keeps the last snapshot per day, without limit (`--keep-daily unlimited`). */
        public fun keepDailyUnlimited(): Builder = apply { keepDaily = UNLIMITED }

        /** Keeps the last snapshot per week, for the last [count] weeks that have snapshots (`--keep-weekly`). */
        public fun keepWeekly(count: Int): Builder = apply { keepWeekly = count.toString() }

        /** Keeps the last snapshot per week, without limit (`--keep-weekly unlimited`). */
        public fun keepWeeklyUnlimited(): Builder = apply { keepWeekly = UNLIMITED }

        /** Keeps the last snapshot per month, for the last [count] months that have snapshots (`--keep-monthly`). */
        public fun keepMonthly(count: Int): Builder = apply { keepMonthly = count.toString() }

        /** Keeps the last snapshot per month, without limit (`--keep-monthly unlimited`). */
        public fun keepMonthlyUnlimited(): Builder = apply { keepMonthly = UNLIMITED }

        /** Keeps the last snapshot per year, for the last [count] years that have snapshots (`--keep-yearly`). */
        public fun keepYearly(count: Int): Builder = apply { keepYearly = count.toString() }

        /** Keeps the last snapshot per year, without limit (`--keep-yearly unlimited`). */
        public fun keepYearlyUnlimited(): Builder = apply { keepYearly = UNLIMITED }

        /** Duration in restic's format, for example `1y5m7d2h`, relative to the newest snapshot. */
        public fun keepWithin(duration: String): Builder = apply { keepWithin = duration }

        /** Within this duration, keeps the last snapshot per hour (`--keep-within-hourly`); format as for [keepWithin]. */
        public fun keepWithinHourly(duration: String): Builder = apply { keepWithinHourly = duration }

        /** Within this duration, keeps the last snapshot per day (`--keep-within-daily`); format as for [keepWithin]. */
        public fun keepWithinDaily(duration: String): Builder = apply { keepWithinDaily = duration }

        /** Within this duration, keeps the last snapshot per week (`--keep-within-weekly`); format as for [keepWithin]. */
        public fun keepWithinWeekly(duration: String): Builder = apply { keepWithinWeekly = duration }

        /** Within this duration, keeps the last snapshot per month (`--keep-within-monthly`); format as for [keepWithin]. */
        public fun keepWithinMonthly(duration: String): Builder = apply { keepWithinMonthly = duration }

        /** Within this duration, keeps the last snapshot per year (`--keep-within-yearly`); format as for [keepWithin]. */
        public fun keepWithinYearly(duration: String): Builder = apply { keepWithinYearly = duration }

        /** Keeps snapshots carrying all of the given tags (several calls are combined with OR). */
        public fun keepTag(vararg tags: String): Builder = apply { keepTagGroups.add(tags.toList()) }

        /** Allows a rule that removes all snapshots, such as `keepLast(0)` (`--unsafe-allow-remove-all`). */
        @JvmOverloads
        public fun unsafeAllowRemoveAll(enabled: Boolean = true): Builder = apply { unsafeAllowRemoveAll = enabled }

        /** Grouping within which the rules apply (`--group-by`): a combination of `host`, `paths`, `tags`. */
        public fun groupBy(group: String): Builder = apply { groupBy = group }

        /** Shorter text output (`--compact`); the JSON result stays the same. */
        @JvmOverloads
        public fun compact(enabled: Boolean = true): Builder = apply { compact = enabled }

        /** Only shows what would be removed (`--dry-run`); [ForgetResult] still contains the decisions. */
        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /**
         * Runs `prune` automatically after removing. Its text output goes to [ForgetResult.pruneOutputLines];
         * restic 0.19 and later no longer print it together with `--json`, so the list is then empty.
         */
        @JvmOverloads
        public fun prune(enabled: Boolean = true): Builder = apply { prune = enabled }

        /** For the prune run: allowed share of unused data, see [PruneTuning.Builder.maxUnused]. */
        public fun maxUnused(limit: String): Builder = apply { tuning.maxUnused(limit) }

        /** For the prune run: upper limit of data to repack, see [PruneTuning.Builder.maxRepackSize]. */
        public fun maxRepackSize(size: String): Builder = apply { tuning.maxRepackSize(size) }

        /** For the prune run: repacks only cacheable packs, see [PruneTuning.Builder.repackCacheableOnly]. */
        @JvmOverloads
        public fun repackCacheableOnly(enabled: Boolean = true): Builder = apply { tuning.repackCacheableOnly(enabled) }

        /** Deprecated since restic 0.19, which always repacks small packs; see [repackSmallerThan]. */
        @JvmOverloads
        public fun repackSmall(enabled: Boolean = true): Builder = apply { tuning.repackSmall(enabled) }

        /** For the prune run: repacks packs below this size, see [PruneTuning.Builder.repackSmallerThan]. */
        public fun repackSmallerThan(size: String): Builder = apply { tuning.repackSmallerThan(size) }

        /** For the prune run: compresses uncompressed data, see [PruneTuning.Builder.repackUncompressed]. */
        @JvmOverloads
        public fun repackUncompressed(enabled: Boolean = true): Builder = apply { tuning.repackUncompressed(enabled) }

        /** Token that cancels the call from another thread. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): ForgetOptions = ForgetOptions(this)
    }

    /** Entry point to the [Builder]; there is no `DEFAULT` because restic does nothing without a rule. */
    public companion object {
        private const val UNLIMITED: String = "unlimited"

        /** A new [Builder] with no rules set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
