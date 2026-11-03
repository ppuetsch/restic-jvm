package io.github.ppuetsch.restic

import java.io.InputStream
import java.nio.file.Path

/**
 * Entry point of the library: the everyday restic commands directly, the rest grouped by topic.
 *
 * | Group | Commands |
 * |---|---|
 * | this class | `init`, `backup`, `snapshots`, `list`, `find`, `restore`, `forget` |
 * | [files] | `ls`, `dump`, `diff`, `mount` |
 * | [modify] | `tag`, `rewrite`, `copy` |
 * | [maintenance] | `prune`, `check`, `stats`, `unlock`, `migrate`, `cache`, `recover`, `features` |
 * | [repair] | `repair index`, `repair packs`, `repair snapshots` |
 * | [keys] | `key list`, `key add`, `key passwd`, `key remove` |
 * | [cat] | `cat` |
 * | [tools] | `version`, `generate`, `completion`, `self-update`, `help`, `options` |
 *
 * An instance belongs to exactly one [ResticConfiguration] (repository, credentials, global flags). It keeps no state
 * between calls and may therefore be used by several threads at once; every call starts its own restic process and
 * returns only when that process has ended (exception: [FileCommands.mount]).
 *
 * restic's errors arrive as subclasses of [ResticException], for example [WrongPasswordException] or
 * [RepositoryLockedException]. Long-running calls can be cancelled through a [CancellationToken].
 *
 * ```kotlin
 * val restic = Restic(ResticConfiguration.builder().repository("/srv/backup").password("secret").build())
 * val result = restic.backup(listOf(Path.of("/home"))) { exclude("*.tmp") }
 * restic.maintenance().check()
 * ```
 *
 * @property configuration Repository, credentials and global flags for all calls of this instance.
 */
public class Restic(
    public val configuration: ResticConfiguration,
) {
    private val runner = ProcessRunner(configuration)
    private val backupCommand = BackupCommand(runner)
    private val restoreCommand = RestoreCommand(runner)
    private val forgetCommand = ForgetCommand(runner)
    private val fileCommands = FileCommands(runner)
    private val modifyCommands = ModifyCommands(runner)
    private val maintenanceCommands = MaintenanceCommands(runner)
    private val repairCommands = RepairCommands(runner)
    private val keyCommands = KeyCommands(runner)
    private val catCommands = CatCommands(runner)
    private val toolCommands = ToolCommands(runner)

    /**
     * Creates a new repository (`restic init`).
     *
     * @return ID and location of the new repository.
     */
    @JvmOverloads
    public fun init(
        options: InitOptions = InitOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): InitResult = InitCommand(runner.cancellableBy(cancellationToken)).init(options)

    /** Kotlin variant of [init] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun init(configure: InitOptions.Builder.() -> Unit): InitResult = init(InitOptions.builder().apply(configure).build())

    /**
     * Backs up files and directories as a new snapshot (`restic backup`).
     *
     * Unreadable source files do not cause an exception: the snapshot is created anyway, and
     * [BackupResult.hasUnreadableSources] is set.
     *
     * @param paths Paths to back up; may be empty if the options set `filesFrom*`.
     * @param listener Receives progress, errors and the summary while restic runs.
     */
    @JvmOverloads
    public fun backup(
        paths: List<Path>,
        options: BackupOptions = BackupOptions.DEFAULT,
        listener: BackupListener? = null,
    ): BackupResult = backupCommand.backupPaths(paths.map(Path::toString), options, listener)

    /** Kotlin variant of [backup] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun backup(
        paths: List<Path>,
        listener: BackupListener? = null,
        configure: BackupOptions.Builder.() -> Unit,
    ): BackupResult = backup(paths, BackupOptions.builder().apply(configure).build(), listener)

    /**
     * Backs up the content of the stream as a single file in the snapshot (`restic backup --stdin`).
     * Its name comes from [BackupOptions.Builder.stdinFilename]; otherwise it is called `stdin`.
     */
    @JvmOverloads
    public fun backupStandardInput(
        input: InputStream,
        options: BackupOptions = BackupOptions.DEFAULT,
        listener: BackupListener? = null,
    ): BackupResult = backupCommand.backupStandardInput(input, options, listener)

    /**
     * Lets restic start the command and backs up its standard output as one file
     * (`restic backup --stdin-from-command`). If the command fails, no snapshot is created.
     *
     * @param command Program and arguments, for example `listOf("pg_dump", "mydb")`.
     */
    @JvmOverloads
    public fun backupCommandOutput(
        command: List<String>,
        options: BackupOptions = BackupOptions.DEFAULT,
        listener: BackupListener? = null,
    ): BackupResult = backupCommand.backupCommandOutput(command, options, listener)

    /** All matching snapshots as a flat list, oldest first (`restic snapshots`), even if `groupBy` is set. */
    @JvmOverloads
    public fun snapshots(
        options: SnapshotsOptions = SnapshotsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): List<Snapshot> = SnapshotsCommand(runner.cancellableBy(cancellationToken ?: options.cancellationToken)).snapshots(options)

    /** Kotlin variant of [snapshots] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun snapshots(configure: SnapshotsOptions.Builder.() -> Unit): List<Snapshot> =
        snapshots(SnapshotsOptions.builder().apply(configure).build())

    /** The matching snapshots in groups; without [SnapshotsOptions.Builder.groupBy] restic groups by `host,paths`. */
    @JvmOverloads
    public fun snapshotGroups(
        options: SnapshotsOptions = SnapshotsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): List<SnapshotGroup> = SnapshotsCommand(runner.cancellableBy(cancellationToken ?: options.cancellationToken)).snapshotGroups(options)

    /**
     * The objects of one kind in the repository (`restic list`), for example all locks:
     *
     * ```kotlin
     * restic.list(ListObjectType.LOCKS).forEach { println("${it.id}: pid ${it.pid} on ${it.hostname}") }
     * ```
     *
     * Listing reads only the IDs; locks, keys and index files read their content on first access, see
     * [RepositoryObject]. The token also cancels those later reads.
     */
    @JvmOverloads
    public fun <T : Any> list(
        type: ListObjectType<T>,
        cancellationToken: CancellationToken? = null,
    ): List<T> = type.listWith(runner.cancellableBy(cancellationToken))

    /**
     * Searches the snapshots for files or directories by name pattern (`restic find`).
     *
     * @param patterns Glob patterns such as `*.pdf`; an entry matches if any of the patterns matches.
     * @return For each snapshot with matches, the entries found.
     */
    @JvmOverloads
    public fun find(
        patterns: List<String>,
        options: FindOptions = FindOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): List<FindSnapshotMatches> =
        FindCommand(runner.cancellableBy(cancellationToken ?: options.cancellationToken)).findNames(patterns, options)

    /** Kotlin variant of [find] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun find(
        patterns: List<String>,
        configure: FindOptions.Builder.() -> Unit,
    ): List<FindSnapshotMatches> = find(patterns, FindOptions.builder().apply(configure).build())

    /**
     * Searches which snapshots contain a blob, tree or pack (`restic find --blob`, `--tree` or `--pack`).
     *
     * @param ids Full IDs or unique prefixes.
     */
    @JvmOverloads
    public fun findObjects(
        kind: FindObjectKind,
        ids: List<String>,
        options: FindOptions = FindOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): List<FindObject> = FindCommand(runner.cancellableBy(cancellationToken ?: options.cancellationToken)).findObjects(kind, ids, options)

    /**
     * Restores a snapshot into the target directory (`restic restore`).
     *
     * @param snapshotId Snapshot ID, [LATEST] or `id:subfolder` to restore only a subtree.
     * @param target Target directory; restic creates it if needed.
     * @param listener Receives progress, errors and the summary while restic runs.
     */
    @JvmOverloads
    public fun restore(
        snapshotId: String,
        target: Path,
        options: RestoreOptions = RestoreOptions.DEFAULT,
        listener: RestoreListener? = null,
    ): RestoreSummary = restoreCommand.restore(snapshotId, target, options, listener)

    /** Kotlin variant of [restore] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun restore(
        snapshotId: String,
        target: Path,
        listener: RestoreListener? = null,
        configure: RestoreOptions.Builder.() -> Unit,
    ): RestoreSummary = restore(snapshotId, target, RestoreOptions.builder().apply(configure).build(), listener)

    /**
     * Removes snapshots by retention rules or explicitly by ID (`restic forget`).
     * Space is freed only by [MaintenanceCommands.prune], or in the same call with [ForgetOptions.Builder.prune].
     *
     * @param outputListener Receives the text output of a subsequent prune run and the error output.
     */
    @JvmOverloads
    public fun forget(
        options: ForgetOptions,
        outputListener: OutputLineListener? = null,
    ): ForgetResult = forgetCommand.forget(options, outputListener)

    /** Kotlin variant of [forget] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun forget(configure: ForgetOptions.Builder.() -> Unit): ForgetResult = forget(ForgetOptions.builder().apply(configure).build())

    /** Looking into snapshots: list, read, compare and mount their files. */
    public fun files(): FileCommands = fileCommands

    /** Changing snapshots: tags, rewriting, copying from another repository. */
    public fun modify(): ModifyCommands = modifyCommands

    /** Keeping the repository healthy and small: prune, check, stats, locks, caches. */
    public fun maintenance(): MaintenanceCommands = maintenanceCommands

    /** Repairing a damaged repository. */
    public fun repair(): RepairCommands = repairCommands

    /** The keys (passwords) of the repository. */
    public fun keys(): KeyCommands = keyCommands

    /** Reading single repository objects by ID (`restic cat`). */
    public fun cat(): CatCommands = catCommands

    /** Commands about the restic binary itself rather than the repository. */
    public fun tools(): ToolCommands = toolCommands

    /** Constants for calls of [Restic]. */
    public companion object {
        /** Stands in for a snapshot ID and means the newest matching snapshot. */
        public const val LATEST: String = "latest"
    }
}
