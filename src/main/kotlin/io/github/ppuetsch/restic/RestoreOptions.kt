package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.nio.file.Path

/** What [Restic.restore] does with files that already exist in the target (`--overwrite`). */
public enum class OverwriteBehavior(
    internal val cliValue: String,
) {
    /** Always overwrites; restic's default. */
    ALWAYS("always"),

    /** Overwrites only if the content differs. */
    IF_CHANGED("if-changed"),

    /** Overwrites only if the file in the snapshot is newer. */
    IF_NEWER("if-newer"),

    /** Never overwrites. */
    NEVER("never"),
}

/**
 * Options for [Restic.restore] (`restic restore`).
 *
 * @property filter Selects the snapshot when the ID is `latest`.
 * @property delete Deletes files in the target that are missing from the snapshot (`--delete`).
 * @property dryRun Only show what would be restored (`--dry-run`).
 * @property excludes Exclude patterns (`--exclude`).
 * @property caseInsensitiveExcludes Exclude patterns ignoring case (`--iexclude`).
 * @property excludeFiles Files with exclude patterns (`--exclude-file`).
 * @property caseInsensitiveExcludeFiles Files with exclude patterns ignoring case (`--iexclude-file`).
 * @property excludeXattrs Extended attributes not to restore (`--exclude-xattr`).
 * @property includes Include patterns (`--include`).
 * @property caseInsensitiveIncludes Include patterns ignoring case (`--iinclude`).
 * @property includeFiles Files with include patterns (`--include-file`).
 * @property caseInsensitiveIncludeFiles Files with include patterns ignoring case (`--iinclude-file`).
 * @property includeXattrs Extended attributes to restore (`--include-xattr`).
 * @property overwrite Handling of existing files (`--overwrite`).
 * @property sparse Restores files with holes in a space-saving way (`--sparse`).
 * @property verify Verifies the restored files (`--verify`).
 * @property verbosity Number of `--verbose`.
 * @property cancellationToken Token that cancels the call.
 */
public class RestoreOptions private constructor(
    builder: Builder,
) {
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

    /** Builds [RestoreOptions]; every method sets one flag and returns the builder. */
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

        /** Picks the newest snapshot of this host (`--host`); only has an effect when the snapshot ID is `latest`. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Picks the newest snapshot with this path (`--path`); only has an effect when the snapshot ID is `latest`. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Picks the newest snapshot with these tags (`--tag`); only has an effect when the snapshot ID is `latest`. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Deletes files in the target that do not exist in the snapshot (`--delete`). */
        @JvmOverloads
        public fun delete(enabled: Boolean = true): Builder = apply { delete = enabled }

        /** Only shows what would be restored (`--dry-run`); useful with [verbosity] 2. */
        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /** Does not restore matching paths (`--exclude`); may be repeated. */
        public fun exclude(pattern: String): Builder = apply { excludes.add(pattern) }

        /** Like [exclude], ignoring case (`--iexclude`). */
        public fun caseInsensitiveExclude(pattern: String): Builder = apply { caseInsensitiveExcludes.add(pattern) }

        /** Reads exclude patterns line by line from a file (`--exclude-file`). */
        public fun excludeFile(file: Path): Builder = apply { excludeFiles.add(file) }

        /** Like [excludeFile], ignoring case (`--iexclude-file`). */
        public fun caseInsensitiveExcludeFile(file: Path): Builder = apply { caseInsensitiveExcludeFiles.add(file) }

        /** Does not restore matching extended attributes, for example `user.*` (`--exclude-xattr`). */
        public fun excludeXattr(pattern: String): Builder = apply { excludeXattrs.add(pattern) }

        /** Restores only matching paths (`--include`); may be repeated. */
        public fun include(pattern: String): Builder = apply { includes.add(pattern) }

        /** Like [include], ignoring case (`--iinclude`). */
        public fun caseInsensitiveInclude(pattern: String): Builder = apply { caseInsensitiveIncludes.add(pattern) }

        /** Reads include patterns line by line from a file (`--include-file`). */
        public fun includeFile(file: Path): Builder = apply { includeFiles.add(file) }

        /** Like [includeFile], ignoring case (`--iinclude-file`). */
        public fun caseInsensitiveIncludeFile(file: Path): Builder = apply { caseInsensitiveIncludeFiles.add(file) }

        /** Restores only matching extended attributes (`--include-xattr`). */
        public fun includeXattr(pattern: String): Builder = apply { includeXattrs.add(pattern) }

        /** Handling of files that already exist in the target (`--overwrite`). */
        public fun overwrite(behavior: OverwriteBehavior): Builder = apply { overwrite = behavior }

        /** Creates ranges of zero bytes as holes instead of writing them (`--sparse`). */
        @JvmOverloads
        public fun sparse(enabled: Boolean = true): Builder = apply { sparse = enabled }

        /** Reads the restored files back and compares their content. */
        @JvmOverloads
        public fun verify(enabled: Boolean = true): Builder = apply { verify = enabled }

        /** 2 reports a [RestoreEvent.FileAction] per file. */
        public fun verbosity(level: Int): Builder = apply { verbosity = level }

        /** Token that cancels the call from another thread. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): RestoreOptions = RestoreOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: restores the whole snapshot. */
        @JvmField
        public val DEFAULT: RestoreOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
