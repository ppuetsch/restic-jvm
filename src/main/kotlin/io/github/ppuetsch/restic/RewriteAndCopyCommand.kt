package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val REWRITE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val SAVED_SNAPSHOT_PATTERN = Regex("""snapshot (\w+) saved""")
private val SAVED_REWRITE_PATTERN = Regex("""saved new snapshot (\w+)""")

/** Optionen für `restic rewrite`. */
public class RewriteOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val snapshotIds: List<String> = builder.snapshotIds.toList()
    public val dryRun: Boolean = builder.dryRun
    public val excludes: List<String> = builder.excludes.toList()
    public val caseInsensitiveExcludes: List<String> = builder.caseInsensitiveExcludes.toList()
    public val excludeFiles: List<Path> = builder.excludeFiles.toList()
    public val caseInsensitiveExcludeFiles: List<Path> = builder.caseInsensitiveExcludeFiles.toList()
    public val forget: Boolean = builder.forget
    public val newHost: String? = builder.newHost
    public val newTime: LocalDateTime? = builder.newTime
    public val snapshotSummary: Boolean = builder.snapshotSummary
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalFlag("--dry-run", dryRun)
            excludes.forEach { optionalValue("--exclude", it) }
            caseInsensitiveExcludes.forEach { optionalValue("--iexclude", it) }
            excludeFiles.forEach { optionalValue("--exclude-file", it) }
            caseInsensitiveExcludeFiles.forEach { optionalValue("--iexclude-file", it) }
            optionalFlag("--forget", forget)
            optionalValue("--new-host", newHost)
            optionalValue("--new-time", newTime?.format(REWRITE_TIME_FORMAT))
            optionalFlag("--snapshot-summary", snapshotSummary)
            addAll(snapshotIds)
        }

    @Suppress("TooManyFunctions") // eine Methode je restic-Flag; das Builder-API bildet die CLI vollständig ab
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal var dryRun: Boolean = false
        internal val excludes: MutableList<String> = mutableListOf()
        internal val caseInsensitiveExcludes: MutableList<String> = mutableListOf()
        internal val excludeFiles: MutableList<Path> = mutableListOf()
        internal val caseInsensitiveExcludeFiles: MutableList<Path> = mutableListOf()
        internal var forget: Boolean = false
        internal var newHost: String? = null
        internal var newTime: LocalDateTime? = null
        internal var snapshotSummary: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        public fun exclude(pattern: String): Builder = apply { excludes.add(pattern) }

        public fun caseInsensitiveExclude(pattern: String): Builder = apply { caseInsensitiveExcludes.add(pattern) }

        public fun excludeFile(file: Path): Builder = apply { excludeFiles.add(file) }

        public fun caseInsensitiveExcludeFile(file: Path): Builder = apply { caseInsensitiveExcludeFiles.add(file) }

        /** Entfernt die Original-Snapshots, nachdem die neuen angelegt wurden. */
        @JvmOverloads
        public fun forget(enabled: Boolean = true): Builder = apply { forget = enabled }

        public fun newHost(hostname: String): Builder = apply { newHost = hostname }

        public fun newTime(time: LocalDateTime): Builder = apply { newTime = time }

        /** Legt einen Snapshot-Summary-Datensatz an, falls er fehlt. */
        @JvmOverloads
        public fun snapshotSummary(enabled: Boolean = true): Builder = apply { snapshotSummary = enabled }

        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        public fun build(): RewriteOptions = RewriteOptions(this)
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** `restic rewrite` kennt kein JSON; [newSnapshotIds] sind die aus der Textausgabe gelesenen Kurz-IDs. */
public data class RewriteResult(
    val outputLines: List<String>,
    val newSnapshotIds: List<String>,
)

/** Optionen für `restic copy`; die Quelle wird über die `from*`-Einstellungen angegeben. */
public class CopyOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val snapshotIds: List<String> = builder.snapshotIds.toList()
    public val fromRepository: String? = builder.fromRepository
    public val fromRepositoryFile: Path? = builder.fromRepositoryFile
    public val fromPasswordFile: Path? = builder.fromPasswordFile
    public val fromPasswordCommand: String? = builder.fromPasswordCommand
    public val fromPassword: String? = builder.fromPassword
    public val fromKeyHint: String? = builder.fromKeyHint
    public val fromInsecureNoPassword: Boolean = builder.fromInsecureNoPassword
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalValue("--from-repo", fromRepository)
            optionalValue("--from-repository-file", fromRepositoryFile)
            optionalValue("--from-password-file", fromPasswordFile)
            optionalValue("--from-password-command", fromPasswordCommand)
            optionalValue("--from-key-hint", fromKeyHint)
            optionalFlag("--from-insecure-no-password", fromInsecureNoPassword)
            addAll(snapshotIds)
        }

    internal fun environment(): Map<String, String> = fromPassword?.let { mapOf("RESTIC_FROM_PASSWORD" to it) }.orEmpty()

    @Suppress("TooManyFunctions") // eine Methode je restic-Flag; das Builder-API bildet die CLI vollständig ab
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal var fromRepository: String? = null
        internal var fromRepositoryFile: Path? = null
        internal var fromPasswordFile: Path? = null
        internal var fromPasswordCommand: String? = null
        internal var fromPassword: String? = null
        internal var fromKeyHint: String? = null
        internal var fromInsecureNoPassword: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Kopiert nur diesen Snapshot (mehrfach möglich); ohne Angabe werden alle kopiert. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Quell-Repository als Connection-String. */
        public fun fromRepository(repository: String): Builder = apply { fromRepository = repository }

        public fun fromRepositoryFile(file: Path): Builder = apply { fromRepositoryFile = file }

        public fun fromPasswordFile(file: Path): Builder = apply { fromPasswordFile = file }

        public fun fromPasswordCommand(command: String): Builder = apply { fromPasswordCommand = command }

        /** Wird über `RESTIC_FROM_PASSWORD` übergeben. */
        public fun fromPassword(password: String): Builder = apply { fromPassword = password }

        public fun fromKeyHint(keyId: String): Builder = apply { fromKeyHint = keyId }

        @JvmOverloads
        public fun fromInsecureNoPassword(enabled: Boolean = true): Builder = apply { fromInsecureNoPassword = enabled }

        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        public fun build(): CopyOptions = CopyOptions(this)
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** `restic copy` kennt kein JSON; [newSnapshotIds] sind die aus der Textausgabe gelesenen Kurz-IDs im Ziel. */
public data class CopyResult(
    val outputLines: List<String>,
    val newSnapshotIds: List<String>,
)

internal class RewriteAndCopyCommand(
    private val runner: ProcessRunner,
) {
    fun rewrite(
        options: RewriteOptions,
        listener: OutputLineListener?,
    ): RewriteResult {
        val request =
            ProcessRequest(
                listOf("rewrite") + options.arguments(),
                cancellation = options.cancellationToken,
                onStandardErrorLine = readableErrorLines(listener),
            )
        val lines = collectLines(request, listener)
        return RewriteResult(lines, idsMatching(SAVED_REWRITE_PATTERN, lines))
    }

    fun copy(
        options: CopyOptions,
        listener: OutputLineListener?,
    ): CopyResult {
        val request =
            ProcessRequest(
                listOf("copy") + options.arguments(),
                options.environment(),
                cancellation = options.cancellationToken,
                onStandardErrorLine = readableErrorLines(listener),
            )
        val lines = collectLines(request, listener)
        return CopyResult(lines, idsMatching(SAVED_SNAPSHOT_PATTERN, lines))
    }

    private fun collectLines(
        request: ProcessRequest,
        listener: OutputLineListener?,
    ): List<String> {
        val lines = mutableListOf<String>()
        runner.runChecked(request) { line ->
            lines += line
            listener?.onLine(line)
        }
        return lines
    }

    private fun idsMatching(
        pattern: Regex,
        lines: List<String>,
    ): List<String> = lines.mapNotNull { pattern.find(it)?.groupValues?.get(1) }
}
