package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/**
 * Optionen für [Restic.forget] (`restic forget`). Ohne Aufbewahrungsregel und ohne Snapshot-IDs lehnt restic den
 * Aufruf ab, damit nicht versehentlich alles gelöscht wird.
 *
 * Die `keep*`-Regeln gelten je Gruppe (siehe [groupBy]) und sind ODER-verknüpft: Ein Snapshot bleibt, sobald eine
 * Regel ihn behalten will.
 *
 * @property filter Auswahl der Snapshots nach Host, Pfad und Tags.
 * @property snapshotIds Gezielt zu entfernende Snapshots.
 * @property keepLast Behält die letzten n Snapshots (`--keep-last`).
 * @property keepHourly Behält je Stunde den letzten für n Stunden (`--keep-hourly`).
 * @property keepDaily Behält je Tag den letzten für n Tage (`--keep-daily`).
 * @property keepWeekly Behält je Woche den letzten für n Wochen (`--keep-weekly`).
 * @property keepMonthly Behält je Monat den letzten für n Monate (`--keep-monthly`).
 * @property keepYearly Behält je Jahr den letzten für n Jahre (`--keep-yearly`).
 * @property keepWithin Behält alle Snapshots dieses Zeitraums (`--keep-within`).
 * @property keepWithinHourly Behält stündliche Snapshots dieses Zeitraums (`--keep-within-hourly`).
 * @property keepWithinDaily Behält tägliche Snapshots dieses Zeitraums (`--keep-within-daily`).
 * @property keepWithinWeekly Behält wöchentliche Snapshots dieses Zeitraums (`--keep-within-weekly`).
 * @property keepWithinMonthly Behält monatliche Snapshots dieses Zeitraums (`--keep-within-monthly`).
 * @property keepWithinYearly Behält jährliche Snapshots dieses Zeitraums (`--keep-within-yearly`).
 * @property keepTagGroups Behält Snapshots mit diesen Tags (`--keep-tag`); jede Gruppe muss vollständig zutreffen.
 * @property unsafeAllowRemoveAll Erlaubt das Entfernen aller Snapshots (`--unsafe-allow-remove-all`).
 * @property groupBy Gruppierung, innerhalb derer die Regeln gelten (`--group-by`).
 * @property compact Kürzere Ausgabe (`--compact`).
 * @property dryRun Nur zeigen, was entfernt würde (`--dry-run`).
 * @property prune Führt danach `prune` aus (`--prune`).
 * @property tuning Stellschrauben des anschließenden Prune-Laufs.
 * @property cancellationToken Token, über das sich der Aufruf abbrechen lässt.
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

    /** Stellt [ForgetOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
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

        /** Wendet die Regeln nur auf Snapshots dieses Hosts an (`--host`); mehrfach möglich. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Wendet die Regeln nur auf Snapshots mit genau diesem Pfad an (`--path`); mehrfach möglich. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Wendet die Regeln nur auf Snapshots an, die alle diese Tags tragen (`--tag`); mehrere Aufrufe sind ODER-verknüpft. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Entfernt gezielt diesen Snapshot (mehrfach möglich). */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Behält die letzten [count] Snapshots (`--keep-last`). */
        public fun keepLast(count: Int): Builder = apply { keepLast = count.toString() }

        /** Behält alle Snapshots (`--keep-last unlimited`). */
        public fun keepLastUnlimited(): Builder = apply { keepLast = UNLIMITED }

        /** Behält je Stunde den letzten Snapshot, für die letzten [count] Stunden mit Snapshots (`--keep-hourly`). */
        public fun keepHourly(count: Int): Builder = apply { keepHourly = count.toString() }

        /** Behält je Stunde den letzten Snapshot, ohne Begrenzung (`--keep-hourly unlimited`). */
        public fun keepHourlyUnlimited(): Builder = apply { keepHourly = UNLIMITED }

        /** Behält je Tag den letzten Snapshot, für die letzten [count] Tage mit Snapshots (`--keep-daily`). */
        public fun keepDaily(count: Int): Builder = apply { keepDaily = count.toString() }

        /** Behält je Tag den letzten Snapshot, ohne Begrenzung (`--keep-daily unlimited`). */
        public fun keepDailyUnlimited(): Builder = apply { keepDaily = UNLIMITED }

        /** Behält je Woche den letzten Snapshot, für die letzten [count] Wochen mit Snapshots (`--keep-weekly`). */
        public fun keepWeekly(count: Int): Builder = apply { keepWeekly = count.toString() }

        /** Behält je Woche den letzten Snapshot, ohne Begrenzung (`--keep-weekly unlimited`). */
        public fun keepWeeklyUnlimited(): Builder = apply { keepWeekly = UNLIMITED }

        /** Behält je Monat den letzten Snapshot, für die letzten [count] Monate mit Snapshots (`--keep-monthly`). */
        public fun keepMonthly(count: Int): Builder = apply { keepMonthly = count.toString() }

        /** Behält je Monat den letzten Snapshot, ohne Begrenzung (`--keep-monthly unlimited`). */
        public fun keepMonthlyUnlimited(): Builder = apply { keepMonthly = UNLIMITED }

        /** Behält je Jahr den letzten Snapshot, für die letzten [count] Jahre mit Snapshots (`--keep-yearly`). */
        public fun keepYearly(count: Int): Builder = apply { keepYearly = count.toString() }

        /** Behält je Jahr den letzten Snapshot, ohne Begrenzung (`--keep-yearly unlimited`). */
        public fun keepYearlyUnlimited(): Builder = apply { keepYearly = UNLIMITED }

        /** Dauer im restic-Format, z.B. `1y5m7d2h`, relativ zum neuesten Snapshot. */
        public fun keepWithin(duration: String): Builder = apply { keepWithin = duration }

        /** Behält innerhalb dieser Dauer je Stunde den letzten Snapshot (`--keep-within-hourly`), Format wie [keepWithin]. */
        public fun keepWithinHourly(duration: String): Builder = apply { keepWithinHourly = duration }

        /** Behält innerhalb dieser Dauer je Tag den letzten Snapshot (`--keep-within-daily`), Format wie [keepWithin]. */
        public fun keepWithinDaily(duration: String): Builder = apply { keepWithinDaily = duration }

        /** Behält innerhalb dieser Dauer je Woche den letzten Snapshot (`--keep-within-weekly`), Format wie [keepWithin]. */
        public fun keepWithinWeekly(duration: String): Builder = apply { keepWithinWeekly = duration }

        /** Behält innerhalb dieser Dauer je Monat den letzten Snapshot (`--keep-within-monthly`), Format wie [keepWithin]. */
        public fun keepWithinMonthly(duration: String): Builder = apply { keepWithinMonthly = duration }

        /** Behält innerhalb dieser Dauer je Jahr den letzten Snapshot (`--keep-within-yearly`), Format wie [keepWithin]. */
        public fun keepWithinYearly(duration: String): Builder = apply { keepWithinYearly = duration }

        /** Behält Snapshots, die alle genannten Tags tragen (mehrere Aufrufe sind ODER-verknüpft). */
        public fun keepTag(vararg tags: String): Builder = apply { keepTagGroups.add(tags.toList()) }

        /** Erlaubt eine Regel, die alle Snapshots entfernt, etwa `keepLast(0)` (`--unsafe-allow-remove-all`). */
        @JvmOverloads
        public fun unsafeAllowRemoveAll(enabled: Boolean = true): Builder = apply { unsafeAllowRemoveAll = enabled }

        /** Gruppierung, innerhalb derer die Regeln gelten (`--group-by`): Kombination aus `host`, `paths`, `tags`. */
        public fun groupBy(group: String): Builder = apply { groupBy = group }

        /** Kürzere Textausgabe (`--compact`); das JSON-Ergebnis bleibt gleich. */
        @JvmOverloads
        public fun compact(enabled: Boolean = true): Builder = apply { compact = enabled }

        /** Zeigt nur, was entfernt würde (`--dry-run`); [ForgetResult] enthält trotzdem die Entscheidungen. */
        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /**
         * Führt nach dem Löschen automatisch `prune` aus. Dessen Textausgabe landet in [ForgetResult.pruneOutputLines];
         * restic ab 0.19 schreibt sie zusammen mit `--json` nicht mehr, die Liste ist dann leer.
         */
        @JvmOverloads
        public fun prune(enabled: Boolean = true): Builder = apply { prune = enabled }

        /** Für den Prune-Lauf: erlaubter Anteil ungenutzter Daten, siehe [PruneTuning.Builder.maxUnused]. */
        public fun maxUnused(limit: String): Builder = apply { tuning.maxUnused(limit) }

        /** Für den Prune-Lauf: Obergrenze neu zu packender Daten, siehe [PruneTuning.Builder.maxRepackSize]. */
        public fun maxRepackSize(size: String): Builder = apply { tuning.maxRepackSize(size) }

        /** Für den Prune-Lauf: packt nur cachebare Packs neu, siehe [PruneTuning.Builder.repackCacheableOnly]. */
        @JvmOverloads
        public fun repackCacheableOnly(enabled: Boolean = true): Builder = apply { tuning.repackCacheableOnly(enabled) }

        /** Seit restic 0.19 veraltet, kleine Packs werden dort immer neu gepackt; siehe [repackSmallerThan]. */
        @JvmOverloads
        public fun repackSmall(enabled: Boolean = true): Builder = apply { tuning.repackSmall(enabled) }

        /** Für den Prune-Lauf: packt Packs unter dieser Größe neu, siehe [PruneTuning.Builder.repackSmallerThan]. */
        public fun repackSmallerThan(size: String): Builder = apply { tuning.repackSmallerThan(size) }

        /** Für den Prune-Lauf: komprimiert unkomprimierte Daten nach, siehe [PruneTuning.Builder.repackUncompressed]. */
        @JvmOverloads
        public fun repackUncompressed(enabled: Boolean = true): Builder = apply { tuning.repackUncompressed(enabled) }

        /** Token, über das sich der Aufruf von einem anderen Thread aus abbrechen lässt. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): ForgetOptions = ForgetOptions(this)
    }

    /** Einstieg in den [Builder]; ein `DEFAULT` gibt es nicht, weil restic ohne Regel nichts tut. */
    public companion object {
        private const val UNLIMITED: String = "unlimited"

        /** Ein neuer [Builder] ohne gesetzte Regeln. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Warum ein Snapshot behalten wird.
 *
 * @property snapshotId ID des behaltenen Snapshots.
 * @property matches Die greifenden Regeln, z.B. `daily snapshot` oder `last snapshot`.
 */
public data class ForgetReason(
    val snapshotId: String,
    val matches: List<String>,
)

/**
 * Die Entscheidungen für eine Gruppe von Snapshots.
 *
 * @property host Host der Gruppe, wenn nach Host gruppiert wurde.
 * @property paths Pfade der Gruppe, wenn nach Pfaden gruppiert wurde.
 * @property tags Tags der Gruppe, wenn nach Tags gruppiert wurde.
 * @property keep Behaltene Snapshots.
 * @property remove Entfernte Snapshots.
 * @property reasons Je behaltenem Snapshot die greifenden Regeln.
 */
public data class ForgetGroup(
    val host: String?,
    val paths: List<String>,
    val tags: List<String>,
    val keep: List<Snapshot>,
    val remove: List<Snapshot>,
    val reasons: List<ForgetReason>,
)

/**
 * Ergebnis von [Restic.forget].
 *
 * @property groups Die Entscheidungen je Gruppe.
 * @property pruneOutputLines Nur mit `prune`: die Textausgabe des anschließenden Prune-Laufs.
 */
public data class ForgetResult(
    val groups: List<ForgetGroup>,
    val pruneOutputLines: List<String>,
) {
    /** Alle entfernten Snapshots über alle Gruppen. */
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
            if (line.isBlank()) return@runChecked
            when {
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
