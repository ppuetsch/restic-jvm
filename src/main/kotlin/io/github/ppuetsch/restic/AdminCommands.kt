package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime

private const val FEATURE_COLUMNS: Int = 4
private val FEATURE_STABILITIES: Set<String> = setOf("stable", "beta", "alpha", "deprecated")
private val COLUMN_SEPARATOR = Regex("""\s+""")

/**
 * Textausgabe eines Befehls ohne JSON-Unterstützung.
 *
 * @property lines Die nicht leeren Zeilen der Standardausgabe in Ausgabereihenfolge.
 */
public data class CommandOutput(
    val lines: List<String>,
)

/**
 * Ein Schlüssel (Passwort) des Repositorys, wie [Restic.listKeys] ihn liefert.
 *
 * @property id Kurz-ID des Schlüssels.
 * @property userName Benutzer, der den Schlüssel angelegt hat.
 * @property hostName Rechner, auf dem der Schlüssel angelegt wurde.
 * @property created Anlagezeitpunkt in lokaler Zeit des anlegenden Rechners.
 * @property current `true`, wenn mit diesem Schlüssel gerade auf das Repository zugegriffen wird.
 */
public data class KeyInfo(
    val id: String,
    val userName: String,
    val hostName: String,
    val created: LocalDateTime?,
    val current: Boolean,
)

/**
 * Optionen für [Restic.addKey] (`restic key add`) und [Restic.changePassword] (`restic key passwd`).
 *
 * @property host Hostname, der im neuen Schlüssel vermerkt wird (`--host`).
 * @property user Benutzername, der im neuen Schlüssel vermerkt wird (`--user`).
 * @property newPasswordFile Datei mit dem neuen Passwort (`--new-password-file`).
 * @property newPassword Neues Passwort im Klartext; wird über eine kurzlebige Temp-Datei übergeben.
 * @property newInsecureNoPassword Neuer Schlüssel ohne Passwort (`--new-insecure-no-password`).
 */
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

    /** Stellt [NewKeyOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal var host: String? = null
        internal var user: String? = null
        internal var newPasswordFile: Path? = null
        internal var newPassword: String? = null
        internal var newInsecureNoPassword: Boolean = false

        /** Hostname, der im neuen Schlüssel vermerkt wird (`--host`); Standard ist der Rechnername. */
        public fun host(hostname: String): Builder = apply { host = hostname }

        /** Benutzername, der im neuen Schlüssel vermerkt wird (`--user`); Standard ist der aktuelle Benutzer. */
        public fun user(username: String): Builder = apply { user = username }

        /** Datei, deren erste Zeile das neue Passwort ist (`--new-password-file`). */
        public fun newPasswordFile(file: Path): Builder = apply { newPasswordFile = file }

        /** restic liest das neue Passwort nur aus Datei oder Terminal; die Library legt dafür kurz eine geschützte Temp-Datei an. */
        public fun newPassword(password: String): Builder = apply { newPassword = password }

        /** Legt den neuen Schlüssel ohne Passwort an (`--new-insecure-no-password`); nur für Tests gedacht. */
        @JvmOverloads
        public fun newInsecureNoPassword(enabled: Boolean = true): Builder = apply { newInsecureNoPassword = enabled }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): NewKeyOptions = NewKeyOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: restic fragt das neue Passwort ab, was ohne Terminal scheitert. */
        @JvmField
        public val DEFAULT: NewKeyOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Ergebnis von [Restic.addKey].
 *
 * @property keyId Voll ausgeschriebene ID des neuen Schlüssels; `null`, wenn restic sie nicht ausgegeben hat.
 * @property outputLines Die Textausgabe von restic.
 */
public data class KeyAddResult(
    val keyId: String?,
    val outputLines: List<String>,
)

/**
 * Ein Feature-Flag der restic-Binary (`restic features`), schaltbar über die Umgebungsvariable `RESTIC_FEATURES`.
 *
 * @property name Name des Flags.
 * @property stage Reifegrad: `alpha`, `beta`, `stable` oder `deprecated`.
 * @property enabledByDefault Ob das Flag ohne `RESTIC_FEATURES` aktiv ist.
 * @property description Kurzbeschreibung von restic.
 */
public data class FeatureFlag(
    val name: String,
    val stage: String,
    val enabledByDefault: Boolean,
    val description: String,
)

/**
 * Optionen für [Restic.cache] (`restic cache`).
 *
 * @property cleanup Entfernt alte Cache-Verzeichnisse (`--cleanup`).
 * @property maxAgeDays Ab welchem Alter in Tagen ein Cache als alt gilt (`--max-age`).
 * @property noSize Verzichtet auf die Größenberechnung (`--no-size`).
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

    /** Stellt [CacheOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal var cleanup: Boolean = false
        internal var maxAgeDays: Int? = null
        internal var noSize: Boolean = false

        /** Entfernt alte Cache-Verzeichnisse (`--cleanup`). */
        @JvmOverloads
        public fun cleanup(enabled: Boolean = true): Builder = apply { cleanup = enabled }

        /** Ab welchem Alter in Tagen ein Cache als alt gilt (`--max-age`); restic-Standard ist 30. */
        public fun maxAgeDays(days: Int): Builder = apply { maxAgeDays = days }

        /** Verzichtet auf die Berechnung der Cache-Größen, was bei großen Caches Zeit spart (`--no-size`). */
        @JvmOverloads
        public fun noSize(enabled: Boolean = true): Builder = apply { noSize = enabled }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): CacheOptions = CacheOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: listet die Caches auf. */
        @JvmField
        public val DEFAULT: CacheOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Optionen für [Restic.repairSnapshots] (`restic repair snapshots`).
 *
 * @property filter Auswahl der Snapshots nach Host, Pfad und Tags.
 * @property snapshotIds Ausgewählte Snapshots; ohne Angabe alle.
 * @property dryRun Nur zeigen, was repariert würde (`--dry-run`).
 * @property forget Entfernt die defekten Originale nach der Reparatur (`--forget`).
 */
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

    /** Stellt [RepairSnapshotsOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal var dryRun: Boolean = false
        internal var forget: Boolean = false

        /** Wählt nur Snapshots dieses Hosts (`--host`); mehrfach möglich. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Wählt nur Snapshots, die genau diesen Pfad gesichert haben (`--path`); mehrfach möglich. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Wählt nur Snapshots, die alle diese Tags tragen (`--tag`); mehrere Aufrufe sind ODER-verknüpft. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Repariert nur diesen Snapshot (ID, Kurz-ID oder `latest`); mehrfach möglich. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Zeigt nur, was repariert würde (`--dry-run`). */
        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /** Entfernt die defekten Original-Snapshots nach der Reparatur (`--forget`). */
        @JvmOverloads
        public fun forget(enabled: Boolean = true): Builder = apply { forget = enabled }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): RepairSnapshotsOptions = RepairSnapshotsOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: repariert alle Snapshots. */
        @JvmField
        public val DEFAULT: RepairSnapshotsOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
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
