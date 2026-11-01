package io.github.ppuetsch.restic

import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Path

public enum class OverwriteBehavior(internal val cliValue: String) {
    ALWAYS("always"),
    IF_CHANGED("if-changed"),
    IF_NEWER("if-newer"),
    NEVER("never"),
}

/** Optionen für `restic restore`. */
public class RestoreOptions private constructor(builder: Builder) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val delete: Boolean = builder.delete
    public val dryRun: Boolean = builder.dryRun
    public val excludes: List<String> = builder.excludes.toList()
    public val caseInsensitiveExcludes: List<String> = builder.caseInsensitiveExcludes.toList()
    public val excludeFiles: List<Path> = builder.excludeFiles.toList()
    public val caseInsensitiveExcludeFiles: List<Path> = builder.caseInsensitiveExcludeFiles.toList()
    public val excludeXattrs: List<String> = builder.excludeXattrs.toList()
    public val includes: List<String> = builder.includes.toList()
    public val caseInsensitiveIncludes: List<String> = builder.caseInsensitiveIncludes.toList()
    public val includeFiles: List<Path> = builder.includeFiles.toList()
    public val caseInsensitiveIncludeFiles: List<Path> = builder.caseInsensitiveIncludeFiles.toList()
    public val includeXattrs: List<String> = builder.includeXattrs.toList()
    public val overwrite: OverwriteBehavior? = builder.overwrite
    public val sparse: Boolean = builder.sparse
    public val verify: Boolean = builder.verify
    public val verbosity: Int = builder.verbosity
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalFlag("--delete", delete)
            optionalFlag("--dry-run", dryRun)
            excludes.forEach { optionalValue("--exclude", it) }
            caseInsensitiveExcludes.forEach { optionalValue("--iexclude", it) }
            excludeFiles.forEach { optionalValue("--exclude-file", it) }
            caseInsensitiveExcludeFiles.forEach { optionalValue("--iexclude-file", it) }
            excludeXattrs.forEach { optionalValue("--exclude-xattr", it) }
            includes.forEach { optionalValue("--include", it) }
            caseInsensitiveIncludes.forEach { optionalValue("--iinclude", it) }
            includeFiles.forEach { optionalValue("--include-file", it) }
            caseInsensitiveIncludeFiles.forEach { optionalValue("--iinclude-file", it) }
            includeXattrs.forEach { optionalValue("--include-xattr", it) }
            optionalValue("--overwrite", overwrite?.cliValue)
            optionalFlag("--sparse", sparse)
            optionalFlag("--verify", verify)
            repeat(verbosity) { add("--verbose") }
        }

    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var delete: Boolean = false
        internal var dryRun: Boolean = false
        internal val excludes: MutableList<String> = mutableListOf()
        internal val caseInsensitiveExcludes: MutableList<String> = mutableListOf()
        internal val excludeFiles: MutableList<Path> = mutableListOf()
        internal val caseInsensitiveExcludeFiles: MutableList<Path> = mutableListOf()
        internal val excludeXattrs: MutableList<String> = mutableListOf()
        internal val includes: MutableList<String> = mutableListOf()
        internal val caseInsensitiveIncludes: MutableList<String> = mutableListOf()
        internal val includeFiles: MutableList<Path> = mutableListOf()
        internal val caseInsensitiveIncludeFiles: MutableList<Path> = mutableListOf()
        internal val includeXattrs: MutableList<String> = mutableListOf()
        internal var overwrite: OverwriteBehavior? = null
        internal var sparse: Boolean = false
        internal var verify: Boolean = false
        internal var verbosity: Int = 0
        internal var cancellationToken: CancellationToken? = null

        /** Wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Wirkt nur, wenn die Snapshot-ID `latest` ist. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Löscht Dateien im Ziel, die im Snapshot nicht existieren. */
        @JvmOverloads
        public fun delete(enabled: Boolean = true): Builder = apply { delete = enabled }

        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        public fun exclude(pattern: String): Builder = apply { excludes.add(pattern) }

        public fun caseInsensitiveExclude(pattern: String): Builder = apply { caseInsensitiveExcludes.add(pattern) }

        public fun excludeFile(file: Path): Builder = apply { excludeFiles.add(file) }

        public fun caseInsensitiveExcludeFile(file: Path): Builder = apply { caseInsensitiveExcludeFiles.add(file) }

        public fun excludeXattr(pattern: String): Builder = apply { excludeXattrs.add(pattern) }

        public fun include(pattern: String): Builder = apply { includes.add(pattern) }

        public fun caseInsensitiveInclude(pattern: String): Builder = apply { caseInsensitiveIncludes.add(pattern) }

        public fun includeFile(file: Path): Builder = apply { includeFiles.add(file) }

        public fun caseInsensitiveIncludeFile(file: Path): Builder = apply { caseInsensitiveIncludeFiles.add(file) }

        public fun includeXattr(pattern: String): Builder = apply { includeXattrs.add(pattern) }

        public fun overwrite(behavior: OverwriteBehavior): Builder = apply { overwrite = behavior }

        @JvmOverloads
        public fun sparse(enabled: Boolean = true): Builder = apply { sparse = enabled }

        /** Liest die wiederhergestellten Dateien zurück und vergleicht den Inhalt. */
        @JvmOverloads
        public fun verify(enabled: Boolean = true): Builder = apply { verify = enabled }

        /** 2 liefert [RestoreEvent.FileAction] pro Datei. */
        public fun verbosity(level: Int): Builder = apply { verbosity = level }

        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        public fun build(): RestoreOptions = RestoreOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: RestoreOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public sealed class RestoreEvent {
    public data class Status(
        val percentDone: Double,
        val totalFiles: Long,
        val filesRestored: Long,
        val filesSkipped: Long,
        val totalBytes: Long,
        val bytesRestored: Long,
        val bytesSkipped: Long,
    ) : RestoreEvent()

    public data class FileAction(
        val action: String,
        val item: String?,
        val size: Long?,
    ) : RestoreEvent()

    public data class Error(
        val item: String?,
        val during: String?,
        val message: String,
    ) : RestoreEvent()

    public data class Summary(val summary: RestoreSummary) : RestoreEvent()
}

public data class RestoreSummary(
    val totalFiles: Long,
    val filesRestored: Long,
    val filesSkipped: Long,
    val filesDeleted: Long,
    val totalBytes: Long,
    val bytesRestored: Long,
    val bytesSkipped: Long,
)

public fun interface RestoreListener {
    public fun onEvent(event: RestoreEvent)
}

private fun restoreEventFrom(node: JsonNode): RestoreEvent? =
    when (node.messageType()) {
        "status" ->
            RestoreEvent.Status(
                percentDone = node.doubleOrNull("percent_done") ?: 0.0,
                totalFiles = node.long("total_files"),
                filesRestored = node.long("files_restored"),
                filesSkipped = node.long("files_skipped"),
                totalBytes = node.long("total_bytes"),
                bytesRestored = node.long("bytes_restored"),
                bytesSkipped = node.long("bytes_skipped"),
            )
        "verbose_status" -> RestoreEvent.FileAction(node.text("action"), node.textOrNull("item"), node.longOrNull("size"))
        "error" ->
            RestoreEvent.Error(
                node.get("error")?.get("item")?.asText() ?: node.textOrNull("item"),
                node.textOrNull("during"),
                node.get("error")?.get("message")?.asText() ?: node.text("message"),
            )
        "summary" ->
            RestoreEvent.Summary(
                RestoreSummary(
                    totalFiles = node.long("total_files"),
                    filesRestored = node.long("files_restored"),
                    filesSkipped = node.long("files_skipped"),
                    filesDeleted = node.long("files_deleted"),
                    totalBytes = node.long("total_bytes"),
                    bytesRestored = node.long("bytes_restored"),
                    bytesSkipped = node.long("bytes_skipped"),
                ),
            )
        else -> null
    }

internal class RestoreCommand(private val runner: ProcessRunner) {
    fun restore(
        snapshotId: String,
        target: Path,
        options: RestoreOptions,
        listener: RestoreListener?,
    ): RestoreSummary {
        var summary: RestoreSummary? = null
        val arguments = listOf("restore", "--json", "--target", target.toString()) + options.arguments() + snapshotId
        runner.runChecked(ProcessRequest(arguments, cancellation = options.cancellationToken)) { line ->
            Json.parseOrNull(line)?.let(::restoreEventFrom)?.let { event ->
                if (event is RestoreEvent.Summary) summary = event.summary
                listener?.onEvent(event)
            }
        }
        return summary ?: throw ResticOutputException("restic restore produced no summary", null)
    }
}
