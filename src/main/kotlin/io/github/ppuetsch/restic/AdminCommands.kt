package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime

private const val FEATURE_COLUMNS: Int = 4
private val FEATURE_STABILITIES: Set<String> = setOf("stable", "beta", "alpha", "deprecated")
private val COLUMN_SEPARATOR = Regex("""\s+""")

/**
 * Text output of a command without JSON support.
 *
 * @property lines The non-blank lines of standard output, in output order.
 */
public data class CommandOutput(
    val lines: List<String>,
)

/**
 * A key (password) of the repository, as [KeyCommands.list] returns it.
 *
 * @property id Short ID of the key.
 * @property userName User who added the key.
 * @property hostName Host on which the key was added.
 * @property created When the key was added, in the local time of that host.
 * @property current `true` if this key is the one currently used to access the repository.
 */
public data class KeyInfo(
    val id: String,
    val userName: String,
    val hostName: String,
    val created: LocalDateTime?,
    val current: Boolean,
)

/**
 * Result of [KeyCommands.add].
 *
 * @property keyId Full ID of the new key; `null` if restic did not print it.
 * @property outputLines restic's text output.
 */
public data class KeyAddResult(
    val keyId: String?,
    val outputLines: List<String>,
)

/**
 * A feature flag of the restic binary (`restic features`), switched through the environment variable `RESTIC_FEATURES`.
 *
 * @property name Name of the flag.
 * @property stage Maturity: `alpha`, `beta`, `stable` or `deprecated`.
 * @property enabledByDefault Whether the flag is active without `RESTIC_FEATURES`.
 * @property description Short description from restic.
 */
public data class FeatureFlag(
    val name: String,
    val stage: String,
    val enabledByDefault: Boolean,
    val description: String,
)

internal class AdminCommands(
    private val runner: ProcessRunner,
) {
    fun unlock(removeAll: Boolean): CommandOutput = output(listOf("unlock") + flagIf(removeAll, "--remove-all"))

    fun migrate(
        names: List<String>,
        force: Boolean,
    ): CommandOutput = output(listOf("migrate") + flagIf(force, "--force") + names)

    fun cache(options: CacheOptions): CommandOutput = output(listOf("cache") + options.arguments())

    fun features(): List<FeatureFlag> =
        runner
            .collectNonBlankLines(listOf("features"))
            .map { it.trim().split(COLUMN_SEPARATOR, limit = FEATURE_COLUMNS) }
            .filter { columns -> columns.size == FEATURE_COLUMNS && columns[1] in FEATURE_STABILITIES }
            .map(::featureFlagFrom)

    fun recover(): CommandOutput = output(listOf("recover"))

    fun repairIndex(readAllPacks: Boolean): CommandOutput = output(listOf("repair", "index") + flagIf(readAllPacks, "--read-all-packs"))

    fun repairPacks(packIds: List<String>): CommandOutput = output(listOf("repair", "packs") + packIds)

    fun repairSnapshots(options: RepairSnapshotsOptions): CommandOutput = output(listOf("repair", "snapshots") + options.arguments())

    private fun output(arguments: List<String>): CommandOutput = CommandOutput(runner.collectNonBlankLines(arguments))

    private fun flagIf(
        enabled: Boolean,
        flag: String,
    ): List<String> = if (enabled) listOf(flag) else emptyList()
}

private fun featureFlagFrom(columns: List<String>): FeatureFlag {
    val (name, stability, enabled) = columns
    return FeatureFlag(name, stability, enabled.toBoolean(), description = columns.last())
}

internal fun ProcessRunner.collectNonBlankLines(arguments: List<String>): List<String> {
    val lines = mutableListOf<String>()
    runChecked(arguments) { if (it.isNotBlank()) lines += it }
    return lines
}
