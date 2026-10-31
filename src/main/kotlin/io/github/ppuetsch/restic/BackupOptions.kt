package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val RESTIC_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** Optionen für `restic backup`. Alle Flags der CLI sind abgedeckt. */
public class BackupOptions private constructor(builder: Builder) {
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

        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        public fun exclude(pattern: String): Builder = apply { excludes.add(pattern) }

        public fun caseInsensitiveExclude(pattern: String): Builder = apply { caseInsensitiveExcludes.add(pattern) }

        public fun excludeFile(file: Path): Builder = apply { excludeFiles.add(file) }

        public fun caseInsensitiveExcludeFile(file: Path): Builder = apply { caseInsensitiveExcludeFiles.add(file) }

        public fun excludeCaches(enabled: Boolean = true): Builder = apply { excludeCaches = enabled }

        public fun excludeCloudFiles(enabled: Boolean = true): Builder = apply { excludeCloudFiles = enabled }

        /** Format `dateiname` oder `dateiname:header`. */
        public fun excludeIfPresent(filenameWithOptionalHeader: String): Builder =
            apply { excludeIfPresent.add(filenameWithOptionalHeader) }

        /** Größe mit Suffix k/K, m/M, g/G, t/T, z.B. `10M`. */
        public fun excludeLargerThan(size: String): Builder = apply { excludeLargerThan = size }

        public fun filesFrom(file: Path): Builder = apply { filesFrom.add(file) }

        public fun filesFromRaw(file: Path): Builder = apply { filesFromRaw.add(file) }

        public fun filesFromVerbatim(file: Path): Builder = apply { filesFromVerbatim.add(file) }

        public fun force(enabled: Boolean = true): Builder = apply { force = enabled }

        /** Kombination aus `host`, `paths`, `tags`, durch Komma getrennt; leer deaktiviert die Gruppierung. */
        public fun groupBy(group: String): Builder = apply { groupBy = group }

        public fun host(hostname: String): Builder = apply { host = hostname }

        public fun ignoreCtime(enabled: Boolean = true): Builder = apply { ignoreCtime = enabled }

        public fun ignoreInode(enabled: Boolean = true): Builder = apply { ignoreInode = enabled }

        public fun noScan(enabled: Boolean = true): Builder = apply { noScan = enabled }

        public fun oneFileSystem(enabled: Boolean = true): Builder = apply { oneFileSystem = enabled }

        public fun parent(snapshotId: String): Builder = apply { parent = snapshotId }

        public fun readConcurrency(files: Int): Builder = apply { readConcurrency = files }

        public fun skipIfUnchanged(enabled: Boolean = true): Builder = apply { skipIfUnchanged = enabled }

        /** Dateiname im Snapshot, wenn von stdin gesichert wird (Standard bei restic: `stdin`). */
        public fun stdinFilename(filename: String): Builder = apply { stdinFilename = filename }

        public fun tag(vararg tags: String): Builder = apply { this.tags.addAll(tags) }

        /** Zeitpunkt des Backups in lokaler Zeit; Standard ist jetzt. */
        public fun time(time: LocalDateTime): Builder = apply { this.time = time }

        public fun useFsSnapshot(enabled: Boolean = true): Builder = apply { useFsSnapshot = enabled }

        public fun withAtime(enabled: Boolean = true): Builder = apply { withAtime = enabled }

        /** Liefert zusätzlich [BackupEvent.FileAction]-Ereignisse pro Datei. */
        public fun verbose(enabled: Boolean = true): Builder = apply { verbose = enabled }

        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        public fun build(): BackupOptions = BackupOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: BackupOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
