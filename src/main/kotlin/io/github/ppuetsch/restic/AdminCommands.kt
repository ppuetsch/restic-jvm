package io.github.ppuetsch.restic

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val KEY_CREATED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val NEW_KEY_ID_PATTERN = Regex("""saved new key with ID (\w+)""")
private const val OWNER_ONLY_FILE_PERMISSIONS: String = "rw-------"

/** Textausgabe eines Befehls ohne JSON-Unterstützung. */
public data class CommandOutput(val lines: List<String>)

public data class KeyInfo(
    val id: String,
    val userName: String,
    val hostName: String,
    val created: LocalDateTime?,
    /** `true`, wenn mit diesem Schlüssel gerade auf das Repository zugegriffen wird. */
    val current: Boolean,
)

/** Optionen für `restic key add` und `restic key passwd`. */
public class NewKeyOptions private constructor(builder: Builder) {
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
public class CacheOptions private constructor(builder: Builder) {
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
public class RepairSnapshotsOptions private constructor(builder: Builder) {
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

internal class AdminCommands(private val runner: ProcessRunner) {
    fun listKeys(): List<KeyInfo> {
        val lines = mutableListOf<String>()
        runner.runChecked(listOf("key", "list", "--json")) { lines += it }
        return Json.parse(lines.joinToString("\n")).map { node ->
            KeyInfo(
                id = node.text("id"),
                userName = node.text("userName"),
                hostName = node.text("hostName"),
                created = node.textOrNull("created")?.let { LocalDateTime.parse(it, KEY_CREATED_FORMAT) },
                current = node.booleanOrNull("current") ?: false,
            )
        }
    }

    fun addKey(options: NewKeyOptions): KeyAddResult {
        val lines = withPasswordFile(options) { passwordFile -> collect(listOf("key", "add") + options.arguments(passwordFile)) }
        return KeyAddResult(lines.firstNotNullOfOrNull { NEW_KEY_ID_PATTERN.find(it)?.groupValues?.get(1) }, lines)
    }

    fun changePassword(options: NewKeyOptions): CommandOutput =
        CommandOutput(withPasswordFile(options) { passwordFile -> collect(listOf("key", "passwd") + options.arguments(passwordFile)) })

    fun removeKey(keyId: String): CommandOutput = CommandOutput(collect(listOf("key", "remove", keyId)))

    fun unlock(removeAll: Boolean): CommandOutput =
        CommandOutput(
            collect(listOf("unlock") + if (removeAll) listOf("--remove-all") else emptyList()),
        )

    fun migrate(
        names: List<String>,
        force: Boolean,
    ): CommandOutput = CommandOutput(collect(listOf("migrate") + (if (force) listOf("--force") else emptyList()) + names))

    fun cache(options: CacheOptions): CommandOutput = CommandOutput(collect(listOf("cache") + options.arguments()))

    fun features(): List<FeatureFlag> =
        collect(listOf("features"))
            .map { it.trim().split(Regex("""\s+"""), limit = FEATURE_COLUMNS) }
            .filter { it.size == FEATURE_COLUMNS && (it[1] == "stable" || it[1] == "beta" || it[1] == "alpha" || it[1] == "deprecated") }
            .map { FeatureFlag(it[0], it[1], it[2].toBoolean(), it[3]) }

    fun recover(): CommandOutput = CommandOutput(collect(listOf("recover")))

    fun repairIndex(readAllPacks: Boolean): CommandOutput =
        CommandOutput(collect(listOf("repair", "index") + if (readAllPacks) listOf("--read-all-packs") else emptyList()))

    fun repairPacks(packIds: List<String>): CommandOutput = CommandOutput(collect(listOf("repair", "packs") + packIds))

    fun repairSnapshots(options: RepairSnapshotsOptions): CommandOutput =
        CommandOutput(collect(listOf("repair", "snapshots") + options.arguments()))

    private fun collect(arguments: List<String>): List<String> {
        val lines = mutableListOf<String>()
        runner.runChecked(arguments) { if (it.isNotBlank()) lines += it }
        return lines
    }

    private fun <T> withPasswordFile(
        options: NewKeyOptions,
        action: (Path?) -> T,
    ): T {
        val password = options.newPassword ?: return action(null)
        val file = createProtectedTemporaryFile(password)
        try {
            return action(file)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private fun createProtectedTemporaryFile(content: String): Path {
        val isPosix = Files.getFileStore(Path.of(System.getProperty("java.io.tmpdir"))).supportsFileAttributeView("posix")
        val ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_ONLY_FILE_PERMISSIONS))
        val file =
            if (isPosix) {
                Files.createTempFile("restic-new-password", ".txt", ownerOnly)
            } else {
                Files.createTempFile("restic-new-password", ".txt")
            }
        Files.writeString(file, content)
        return file
    }

    private companion object {
        const val FEATURE_COLUMNS: Int = 4
    }
}
