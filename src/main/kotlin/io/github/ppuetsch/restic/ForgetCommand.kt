package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/** Optionen für `restic forget`. Ohne Aufbewahrungsregel und ohne Snapshot-IDs lehnt restic den Aufruf ab. */
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

    @Suppress("TooManyFunctions") // eine Methode je restic-Flag; das Builder-API bildet die CLI vollständig ab
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

        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Entfernt gezielt diesen Snapshot (mehrfach möglich). */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        public fun keepLast(count: Int): Builder = apply { keepLast = count.toString() }

        public fun keepLastUnlimited(): Builder = apply { keepLast = UNLIMITED }

        public fun keepHourly(count: Int): Builder = apply { keepHourly = count.toString() }

        public fun keepHourlyUnlimited(): Builder = apply { keepHourly = UNLIMITED }

        public fun keepDaily(count: Int): Builder = apply { keepDaily = count.toString() }

        public fun keepDailyUnlimited(): Builder = apply { keepDaily = UNLIMITED }

        public fun keepWeekly(count: Int): Builder = apply { keepWeekly = count.toString() }

        public fun keepWeeklyUnlimited(): Builder = apply { keepWeekly = UNLIMITED }

        public fun keepMonthly(count: Int): Builder = apply { keepMonthly = count.toString() }

        public fun keepMonthlyUnlimited(): Builder = apply { keepMonthly = UNLIMITED }

        public fun keepYearly(count: Int): Builder = apply { keepYearly = count.toString() }

        public fun keepYearlyUnlimited(): Builder = apply { keepYearly = UNLIMITED }

        /** Dauer im restic-Format, z.B. `1y5m7d2h`, relativ zum neuesten Snapshot. */
        public fun keepWithin(duration: String): Builder = apply { keepWithin = duration }

        public fun keepWithinHourly(duration: String): Builder = apply { keepWithinHourly = duration }

        public fun keepWithinDaily(duration: String): Builder = apply { keepWithinDaily = duration }

        public fun keepWithinWeekly(duration: String): Builder = apply { keepWithinWeekly = duration }

        public fun keepWithinMonthly(duration: String): Builder = apply { keepWithinMonthly = duration }

        public fun keepWithinYearly(duration: String): Builder = apply { keepWithinYearly = duration }

        /** Behält Snapshots, die alle genannten Tags tragen (mehrere Aufrufe sind ODER-verknüpft). */
        public fun keepTag(vararg tags: String): Builder = apply { keepTagGroups.add(tags.toList()) }

        @JvmOverloads
        public fun unsafeAllowRemoveAll(enabled: Boolean = true): Builder = apply { unsafeAllowRemoveAll = enabled }

        public fun groupBy(group: String): Builder = apply { groupBy = group }

        @JvmOverloads
        public fun compact(enabled: Boolean = true): Builder = apply { compact = enabled }

        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /** Führt nach dem Löschen automatisch `prune` aus; dessen Textausgabe landet in [ForgetResult.pruneOutputLines]. */
        @JvmOverloads
        public fun prune(enabled: Boolean = true): Builder = apply { prune = enabled }

        public fun maxUnused(limit: String): Builder = apply { tuning.maxUnused(limit) }

        public fun maxRepackSize(size: String): Builder = apply { tuning.maxRepackSize(size) }

        @JvmOverloads
        public fun repackCacheableOnly(enabled: Boolean = true): Builder = apply { tuning.repackCacheableOnly(enabled) }

        @JvmOverloads
        public fun repackSmall(enabled: Boolean = true): Builder = apply { tuning.repackSmall(enabled) }

        public fun repackSmallerThan(size: String): Builder = apply { tuning.repackSmallerThan(size) }

        @JvmOverloads
        public fun repackUncompressed(enabled: Boolean = true): Builder = apply { tuning.repackUncompressed(enabled) }

        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        public fun build(): ForgetOptions = ForgetOptions(this)
    }

    public companion object {
        private const val UNLIMITED: String = "unlimited"

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public data class ForgetReason(
    val snapshotId: String,
    val matches: List<String>,
)

public data class ForgetGroup(
    val host: String?,
    val paths: List<String>,
    val tags: List<String>,
    val keep: List<Snapshot>,
    val remove: List<Snapshot>,
    val reasons: List<ForgetReason>,
)

public data class ForgetResult(
    val groups: List<ForgetGroup>,
    /** Nur mit `prune`: die Textausgabe des anschließenden Prune-Laufs. */
    val pruneOutputLines: List<String>,
) {
    public fun removedSnapshots(): List<Snapshot> = groups.flatMap { it.remove }
}

private fun forgetGroupFrom(node: JsonNode) =
    ForgetGroup(
        host = node.textOrNull("host"),
        paths = node.textList("paths"),
        tags = node.textList("tags"),
        keep = node.get("keep")?.mapElements(::snapshotFrom).orEmpty(),
        remove = node.get("remove")?.mapElements(::snapshotFrom).orEmpty(),
        reasons =
            node
                .get("reasons")
                ?.mapElements { reason ->
                    ForgetReason(reason.get("snapshot")?.textOrNull("id").orEmpty(), reason.textList("matches"))
                }.orEmpty(),
    )

/** Prune-Fortschritt wie `[0:00] 100.00%` beginnt ebenfalls mit `[`, ist aber kein JSON. */
private fun isGroupDocument(line: String): Boolean = Json.parseOrNull(line)?.isArray == true

internal class ForgetCommand(
    private val runner: ProcessRunner,
) {
    fun forget(
        options: ForgetOptions,
        outputListener: OutputLineListener?,
    ): ForgetResult {
        var groups: List<ForgetGroup>? = null
        val pruneOutput = mutableListOf<String>()
        val request =
            ProcessRequest(
                listOf("forget", "--json") + options.arguments(),
                cancellation = options.cancellationToken,
                onStandardErrorLine = readableErrorLines(outputListener),
            )
        runner.runChecked(request) { line ->
            when {
                line.isBlank() -> {}

                groups == null && isGroupDocument(line) -> {
                    groups = Json.parse(line).mapElements(::forgetGroupFrom)
                }

                else -> {
                    pruneOutput += line
                    outputListener?.onLine(line)
                }
            }
        }
        return ForgetResult(groups.orEmpty(), pruneOutput)
    }
}
