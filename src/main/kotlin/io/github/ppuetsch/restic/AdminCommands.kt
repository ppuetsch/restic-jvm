package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime

private const val FEATURE_COLUMNS: Int = 4
private val FEATURE_STABILITIES: Set<String> = setOf("stable", "beta", "alpha", "deprecated")
private val COLUMN_SEPARATOR = Regex("""\s+""")

/** Textausgabe eines Befehls ohne JSON-Unterstützung. */
public data class CommandOutput(
    val lines: List<String>,
)

public data class KeyInfo(
    val id: String,
    val userName: String,
    val hostName: String,
    val created: LocalDateTime?,
    /** `true`, wenn mit diesem Schlüssel gerade auf das Repository zugegriffen wird. */
    val current: Boolean,
)

/** Optionen für `restic key add` und `restic key passwd`. */
public class NewKeyOptions private constructor(
    builder: Builder,
) {
    public val host: String? = builder.host
    public val user: String? = builder.user
    public val newPasswordFile: Path? = builder.newPasswordFile
    public val newPassword: String? = builder.newPassword
    public val newInsecureNoPassword: Boolean = builder.newInsecureNoPassword

    internal fun arguments(passwordFile: Path?): List<String> =
        buildList {
            optionalValue("--host", host)
            optionalValue("--user", user)
            optionalValue("--new-password-file", passwordFile ?: newPasswordFile)
            optionalFlag("--new-insecure-no-password", newInsecureNoPassword)
        }

    public class Builder internal constructor() {
        internal var host: String? = null
        internal var user: String? = null
        internal var newPasswordFile: Path? = null
        internal var newPassword: String? = null
        internal var newInsecureNoPassword: Boolean = false

        public fun host(hostname: String): Builder = apply { host = hostname }

        public fun user(username: String): Builder = apply { user = username }

        public fun newPasswordFile(file: Path): Builder = apply { newPasswordFile = file }

        /** restic liest das neue Passwort nur aus Datei oder Terminal; die Library legt dafür kurz eine geschützte Temp-Datei an. */
        public fun newPassword(password: String): Builder = apply { newPassword = password }

        @JvmOverloads
        public fun newInsecureNoPassword(enabled: Boolean = true): Builder = apply { newInsecureNoPassword = enabled }

        public fun build(): NewKeyOptions = NewKeyOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: NewKeyOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public data class KeyAddResult(
    /** Voll ausgeschriebene ID des neuen Schlüssels. */
    val keyId: String?,
    val outputLines: List<String>,
)

public data class FeatureFlag(
    val name: String,
    val stage: String,
    val enabledByDefault: Boolean,
    val description: String,
)

/** Optionen für `restic cache`. */
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

    public class Builder internal constructor() {
        internal var cleanup: Boolean = false
        internal var maxAgeDays: Int? = null
        internal var noSize: Boolean = false

        /** Entfernt alte Cache-Verzeichnisse. */
        @JvmOverloads
        public fun cleanup(enabled: Boolean = true): Builder = apply { cleanup = enabled }

        public fun maxAgeDays(days: Int): Builder = apply { maxAgeDays = days }

        @JvmOverloads
        public fun noSize(enabled: Boolean = true): Builder = apply { noSize = enabled }

        public fun build(): CacheOptions = CacheOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: CacheOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Optionen für `restic repair snapshots`. */
public class RepairSnapshotsOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val snapshotIds: List<String> = builder.snapshotIds.toList()
    public val dryRun: Boolean = builder.dryRun
    public val forget: Boolean = builder.forget

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalFlag("--dry-run", dryRun)
            optionalFlag("--forget", forget)
            addAll(snapshotIds)
        }

    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal var dryRun: Boolean = false
        internal var forget: Boolean = false

        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /** Entfernt die defekten Original-Snapshots nach der Reparatur. */
        @JvmOverloads
        public fun forget(enabled: Boolean = true): Builder = apply { forget = enabled }

        public fun build(): RepairSnapshotsOptions = RepairSnapshotsOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: RepairSnapshotsOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

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
