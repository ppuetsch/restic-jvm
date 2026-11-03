package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val RESTIC_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * Options for [Restic.backup], [Restic.backupStandardInput] and [Restic.backupCommandOutput] (`restic backup`).
 * Every flag of the CLI is covered.
 *
 * @property dryRun Only show what would be backed up (`--dry-run`).
 * @property excludes Exclude patterns (`--exclude`).
 * @property caseInsensitiveExcludes Exclude patterns ignoring case (`--iexclude`).
 * @property excludeFiles Files with exclude patterns (`--exclude-file`).
 * @property caseInsensitiveExcludeFiles Files with exclude patterns ignoring case (`--iexclude-file`).
 * @property excludeCaches Skips directories marked as cache (`--exclude-caches`).
 * @property excludeCloudFiles Skips files that exist only in the cloud (`--exclude-cloud-files`).
 * @property excludeIfPresent Skips directories containing one of these files (`--exclude-if-present`).
 * @property excludeLargerThan Skips files above this size (`--exclude-larger-than`).
 * @property filesFrom Files with patterns to back up (`--files-from`).
 * @property filesFromRaw Files with NUL-separated paths (`--files-from-raw`).
 * @property filesFromVerbatim Files with one path per line, without pattern matching (`--files-from-verbatim`).
 * @property force Rereads all files instead of skipping unchanged ones (`--force`).
 * @property groupBy Grouping used to find the parent snapshot (`--group-by`).
 * @property host Host name in the snapshot (`--host`).
 * @property ignoreCtime Ignores ctime changes when detecting changed files (`--ignore-ctime`).
 * @property ignoreInode Ignores inode changes when detecting changed files (`--ignore-inode`).
 * @property noScan Skips the initial size scan (`--no-scan`).
 * @property oneFileSystem Stays on the file system of the start paths (`--one-file-system`).
 * @property parent Explicit parent snapshot (`--parent`).
 * @property readConcurrency Number of files read in parallel (`--read-concurrency`).
 * @property skipIfUnchanged Creates no snapshot if nothing changed (`--skip-if-unchanged`).
 * @property stdinFilename File name in the snapshot when backing up stdin (`--stdin-filename`).
 * @property tags Tags of the new snapshot (`--tag`).
 * @property time Time of the snapshot (`--time`).
 * @property useFsSnapshot Backs up from a VSS snapshot on Windows (`--use-fs-snapshot`).
 * @property withAtime Also stores the access time (`--with-atime`).
 * @property verbose Reports every file as [BackupEvent.FileAction] (`--verbose`).
 * @property cancellationToken Token that cancels the call.
 */
public class BackupOptions private constructor(
    builder: Builder,
) {
    public val dryRun: Boolean = builder.dryRun
    public val excludes: List<String> = builder.excludes.toList()
    public val caseInsensitiveExcludes: List<String> = builder.caseInsensitiveExcludes.toList()
    public val excludeFiles: List<Path> = builder.excludeFiles.toList()
    public val caseInsensitiveExcludeFiles: List<Path> = builder.caseInsensitiveExcludeFiles.toList()
    public val excludeCaches: Boolean = builder.excludeCaches
    public val excludeCloudFiles: Boolean = builder.excludeCloudFiles
    public val excludeIfPresent: List<String> = builder.excludeIfPresent.toList()
    public val excludeLargerThan: String? = builder.excludeLargerThan
    public val filesFrom: List<Path> = builder.filesFrom.toList()
    public val filesFromRaw: List<Path> = builder.filesFromRaw.toList()
    public val filesFromVerbatim: List<Path> = builder.filesFromVerbatim.toList()
    public val force: Boolean = builder.force
    public val groupBy: String? = builder.groupBy
    public val host: String? = builder.host
    public val ignoreCtime: Boolean = builder.ignoreCtime
    public val ignoreInode: Boolean = builder.ignoreInode
    public val noScan: Boolean = builder.noScan
    public val oneFileSystem: Boolean = builder.oneFileSystem
    public val parent: String? = builder.parent
    public val readConcurrency: Int? = builder.readConcurrency
    public val skipIfUnchanged: Boolean = builder.skipIfUnchanged
    public val stdinFilename: String? = builder.stdinFilename
    public val tags: List<String> = builder.tags.toList()
    public val time: LocalDateTime? = builder.time
    public val useFsSnapshot: Boolean = builder.useFsSnapshot
    public val withAtime: Boolean = builder.withAtime
    public val verbose: Boolean = builder.verbose
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            optionalFlag("--dry-run", dryRun)
            excludes.forEach { optionalValue("--exclude", it) }
            caseInsensitiveExcludes.forEach { optionalValue("--iexclude", it) }
            excludeFiles.forEach { optionalValue("--exclude-file", it) }
            caseInsensitiveExcludeFiles.forEach { optionalValue("--iexclude-file", it) }
            optionalFlag("--exclude-caches", excludeCaches)
            optionalFlag("--exclude-cloud-files", excludeCloudFiles)
            excludeIfPresent.forEach { optionalValue("--exclude-if-present", it) }
            optionalValue("--exclude-larger-than", excludeLargerThan)
            filesFrom.forEach { optionalValue("--files-from", it) }
            filesFromRaw.forEach { optionalValue("--files-from-raw", it) }
            filesFromVerbatim.forEach { optionalValue("--files-from-verbatim", it) }
            optionalFlag("--force", force)
            optionalValue("--group-by", groupBy)
            optionalValue("--host", host)
            optionalFlag("--ignore-ctime", ignoreCtime)
            optionalFlag("--ignore-inode", ignoreInode)
            optionalFlag("--no-scan", noScan)
            optionalFlag("--one-file-system", oneFileSystem)
            optionalValue("--parent", parent)
            optionalValue("--read-concurrency", readConcurrency)
            optionalFlag("--skip-if-unchanged", skipIfUnchanged)
            optionalValue("--stdin-filename", stdinFilename)
            tags.forEach { optionalValue("--tag", it) }
            optionalValue("--time", time?.format(RESTIC_TIME_FORMAT))
            optionalFlag("--use-fs-snapshot", useFsSnapshot)
            optionalFlag("--with-atime", withAtime)
            optionalFlag("--verbose", verbose)
        }

    /** Builds [BackupOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal var dryRun: Boolean = false
        internal val excludes: MutableList<String> = mutableListOf()
        internal val caseInsensitiveExcludes: MutableList<String> = mutableListOf()
        internal val excludeFiles: MutableList<Path> = mutableListOf()
        internal val caseInsensitiveExcludeFiles: MutableList<Path> = mutableListOf()
        internal var excludeCaches: Boolean = false
        internal var excludeCloudFiles: Boolean = false
        internal val excludeIfPresent: MutableList<String> = mutableListOf()
        internal var excludeLargerThan: String? = null
        internal val filesFrom: MutableList<Path> = mutableListOf()
        internal val filesFromRaw: MutableList<Path> = mutableListOf()
        internal val filesFromVerbatim: MutableList<Path> = mutableListOf()
        internal var force: Boolean = false
        internal var groupBy: String? = null
        internal var host: String? = null
        internal var ignoreCtime: Boolean = false
        internal var ignoreInode: Boolean = false
        internal var noScan: Boolean = false
        internal var oneFileSystem: Boolean = false
        internal var parent: String? = null
        internal var readConcurrency: Int? = null
        internal var skipIfUnchanged: Boolean = false
        internal var stdinFilename: String? = null
        internal val tags: MutableList<String> = mutableListOf()
        internal var time: LocalDateTime? = null
        internal var useFsSnapshot: Boolean = false
        internal var withAtime: Boolean = false
        internal var verbose: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        /** Only shows what would be backed up, without writing data (`--dry-run`). */
        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /** Excludes matching paths (`--exclude`), for example `*.tmp` or `/home/user/.cache`; may be repeated. */
        public fun exclude(pattern: String): Builder = apply { excludes.add(pattern) }

        /** Like [exclude], ignoring case (`--iexclude`). */
        public fun caseInsensitiveExclude(pattern: String): Builder = apply { caseInsensitiveExcludes.add(pattern) }

        /** Reads exclude patterns line by line from a file (`--exclude-file`). */
        public fun excludeFile(file: Path): Builder = apply { excludeFiles.add(file) }

        /** Like [excludeFile], ignoring case (`--iexclude-file`). */
        public fun caseInsensitiveExcludeFile(file: Path): Builder = apply { caseInsensitiveExcludeFiles.add(file) }

        /** Skips directories containing a `CACHEDIR.TAG` file (`--exclude-caches`). */
        @JvmOverloads
        public fun excludeCaches(enabled: Boolean = true): Builder = apply { excludeCaches = enabled }

        /** Skips placeholders of cloud files that are not available locally, for example with OneDrive (`--exclude-cloud-files`). */
        @JvmOverloads
        public fun excludeCloudFiles(enabled: Boolean = true): Builder = apply { excludeCloudFiles = enabled }

        /** Skips directories containing this file (`--exclude-if-present`); format `filename` or `filename:header`. */
        public fun excludeIfPresent(filenameWithOptionalHeader: String): Builder =
            apply { excludeIfPresent.add(filenameWithOptionalHeader) }

        /** Skips files above this size (`--exclude-larger-than`); suffix k/K, m/M, g/G, t/T, for example `10M`. */
        public fun excludeLargerThan(size: String): Builder = apply { excludeLargerThan = size }

        /** Reads paths and patterns to back up line by line from a file (`--files-from`). */
        public fun filesFrom(file: Path): Builder = apply { filesFrom.add(file) }

        /** Reads NUL-separated paths to back up from a file, for example from `find -print0` (`--files-from-raw`). */
        public fun filesFromRaw(file: Path): Builder = apply { filesFromRaw.add(file) }

        /** Reads paths to back up line by line, without evaluating patterns (`--files-from-verbatim`). */
        public fun filesFromVerbatim(file: Path): Builder = apply { filesFromVerbatim.add(file) }

        /** Rereads all files instead of skipping unchanged ones based on the parent snapshot (`--force`). */
        @JvmOverloads
        public fun force(enabled: Boolean = true): Builder = apply { force = enabled }

        /** How the parent snapshot is found (`--group-by`): a comma-separated combination of `host`, `paths`, `tags`. */
        public fun groupBy(group: String): Builder = apply { groupBy = group }

        /** Host name in the snapshot instead of the machine name (`--host`). */
        public fun host(hostname: String): Builder = apply { host = hostname }

        /** Detects changed files without looking at the ctime (`--ignore-ctime`). */
        @JvmOverloads
        public fun ignoreCtime(enabled: Boolean = true): Builder = apply { ignoreCtime = enabled }

        /** Detects changed files without looking at the inode number, for example on FUSE or network drives (`--ignore-inode`). */
        @JvmOverloads
        public fun ignoreInode(enabled: Boolean = true): Builder = apply { ignoreInode = enabled }

        /** Skips the initial size scan; progress then knows no total size (`--no-scan`). */
        @JvmOverloads
        public fun noScan(enabled: Boolean = true): Builder = apply { noScan = enabled }

        /** Stays on the file system of the start paths and does not follow mount points (`--one-file-system`). */
        @JvmOverloads
        public fun oneFileSystem(enabled: Boolean = true): Builder = apply { oneFileSystem = enabled }

        /** Compares with this snapshot instead of the automatically chosen one (`--parent`). */
        public fun parent(snapshotId: String): Builder = apply { parent = snapshotId }

        /** Number of files read in parallel (`--read-concurrency`); restic's default is 2. */
        public fun readConcurrency(files: Int): Builder = apply { readConcurrency = files }

        /** Creates no snapshot if nothing changed compared with the parent snapshot (`--skip-if-unchanged`). */
        @JvmOverloads
        public fun skipIfUnchanged(enabled: Boolean = true): Builder = apply { skipIfUnchanged = enabled }

        /** File name in the snapshot when backing up stdin (`--stdin-filename`, restic's default: `stdin`). */
        public fun stdinFilename(filename: String): Builder = apply { stdinFilename = filename }

        /** Adds tags to the snapshot (`--tag`); may be repeated. */
        public fun tag(vararg tags: String): Builder = apply { this.tags.addAll(tags) }

        /** Time of the snapshot in local time (`--time`); defaults to now. */
        public fun time(time: LocalDateTime): Builder = apply { this.time = time }

        /** On Windows, backs up from a Volume Shadow Copy snapshot so open files are consistent (`--use-fs-snapshot`). */
        @JvmOverloads
        public fun useFsSnapshot(enabled: Boolean = true): Builder = apply { useFsSnapshot = enabled }

        /** Also stores the access time of the files (`--with-atime`). */
        @JvmOverloads
        public fun withAtime(enabled: Boolean = true): Builder = apply { withAtime = enabled }

        /** Additionally reports every file as [BackupEvent.FileAction] (`--verbose`). */
        @JvmOverloads
        public fun verbose(enabled: Boolean = true): Builder = apply { verbose = enabled }

        /** Token that cancels the call from another thread. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): BackupOptions = BackupOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: restic's default behaviour. */
        @JvmField
        public val DEFAULT: BackupOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
