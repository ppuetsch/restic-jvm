package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val REWRITE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * Options for [ModifyCommands.rewrite] (`restic rewrite`). Without snapshot IDs and filters, all snapshots are rewritten.
 *
 * @property filter Selects snapshots by host, path and tags.
 * @property snapshotIds Snapshots to rewrite.
 * @property dryRun Only show what would be rewritten (`--dry-run`).
 * @property excludes Patterns to exclude afterwards (`--exclude`).
 * @property caseInsensitiveExcludes Exclude patterns ignoring case (`--iexclude`).
 * @property excludeFiles Files with exclude patterns (`--exclude-file`).
 * @property caseInsensitiveExcludeFiles Files with exclude patterns ignoring case (`--iexclude-file`).
 * @property forget Removes the originals after rewriting (`--forget`).
 * @property newHost New host name of the snapshots (`--new-host`).
 * @property newTime New time of the snapshots (`--new-time`).
 * @property snapshotSummary Adds missing summaries (`--snapshot-summary`).
 * @property cancellationToken Token that cancels the call.
 */
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

    /** Builds [RewriteOptions]; every method sets one flag and returns the builder. */
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

        /** Rewrites only snapshots of this host (`--host`); may be repeated. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Rewrites only snapshots that backed up exactly this path (`--path`); may be repeated. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Rewrites only snapshots carrying all of these tags (`--tag`); several calls are combined with OR. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Rewrites this snapshot (ID, short ID or `latest`); may be repeated. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Only shows what would be rewritten (`--dry-run`). */
        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /** Removes matching paths from the snapshots (`--exclude`); may be repeated. */
        public fun exclude(pattern: String): Builder = apply { excludes.add(pattern) }

        /** Like [exclude], ignoring case (`--iexclude`). */
        public fun caseInsensitiveExclude(pattern: String): Builder = apply { caseInsensitiveExcludes.add(pattern) }

        /** Reads exclude patterns line by line from a file (`--exclude-file`). */
        public fun excludeFile(file: Path): Builder = apply { excludeFiles.add(file) }

        /** Like [excludeFile], ignoring case (`--iexclude-file`). */
        public fun caseInsensitiveExcludeFile(file: Path): Builder = apply { caseInsensitiveExcludeFiles.add(file) }

        /** Removes the original snapshots after the new ones were created. */
        @JvmOverloads
        public fun forget(enabled: Boolean = true): Builder = apply { forget = enabled }

        /** Sets a new host name (`--new-host`). */
        public fun newHost(hostname: String): Builder = apply { newHost = hostname }

        /** Sets a new time, in local time (`--new-time`). */
        public fun newTime(time: LocalDateTime): Builder = apply { newTime = time }

        /** Adds a snapshot summary record if it is missing. */
        @JvmOverloads
        public fun snapshotSummary(enabled: Boolean = true): Builder = apply { snapshotSummary = enabled }

        /** Token that cancels the call from another thread. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): RewriteOptions = RewriteOptions(this)
    }

    /** Einstieg in den [Builder]. */
    public companion object {
        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
