package io.github.ppuetsch.restic

import java.io.OutputStream
import java.nio.file.Path

/** Looking into snapshots: list, read, compare and mount their files. Obtained from [Restic.files]. */
public class FileCommands internal constructor(
    private val runner: ProcessRunner,
) {
    /**
     * Lists the content of a snapshot (`restic ls`) and reports each entry to the listener individually, without keeping
     * all entries in memory.
     *
     * @param snapshotId Snapshot ID, short ID or [Restic.LATEST].
     * @return The listed snapshot.
     */
    @JvmOverloads
    public fun ls(
        snapshotId: String = Restic.LATEST,
        options: LsOptions = LsOptions.DEFAULT,
        listener: LsListener? = null,
    ): Snapshot = ls(snapshotId, options, null, listener)

    /**
     * Like [ls], cancellable through [cancellationToken]; if it is `null`, the token from [options] applies. The listener
     * comes last so that a trailing lambda remains the listener; that is why all parameters are required here.
     */
    public fun ls(
        snapshotId: String,
        options: LsOptions,
        cancellationToken: CancellationToken?,
        listener: LsListener?,
    ): Snapshot = LsCommand(runner.cancellableBy(cancellationToken ?: options.cancellationToken)).ls(snapshotId, options, listener)

    /** Kotlin variant of [ls] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun ls(
        snapshotId: String = Restic.LATEST,
        listener: LsListener? = null,
        configure: LsOptions.Builder.() -> Unit,
    ): Snapshot = ls(snapshotId, LsOptions.builder().apply(configure).build(), listener)

    /** Like [ls], but collects all entries in a list; for very large snapshots prefer [ls] with a listener. */
    @JvmOverloads
    public fun listNodes(
        snapshotId: String = Restic.LATEST,
        options: LsOptions = LsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): LsResult {
        val nodes = mutableListOf<LsNode>()
        val snapshot = ls(snapshotId, options, cancellationToken) { nodes += it }
        return LsResult(snapshot, nodes)
    }

    /**
     * Writes a file from the snapshot to the stream, a directory as an archive (`restic dump`).
     * The stream stays open.
     *
     * @param fileInSnapshot Absolute path inside the snapshot, for example `/home/user/notes.txt`.
     */
    @JvmOverloads
    public fun dump(
        snapshotId: String,
        fileInSnapshot: String,
        output: OutputStream,
        options: DumpOptions = DumpOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): Unit = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).dump(snapshotId, fileInSnapshot, options, output)

    /** Like [dump], but writes directly to a target file (`restic dump --target`). */
    @JvmOverloads
    public fun dumpToFile(
        snapshotId: String,
        fileInSnapshot: String,
        target: Path,
        options: DumpOptions = DumpOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): Unit = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).dumpToFile(snapshotId, fileInSnapshot, target, options)

    /**
     * Compares two snapshots (`restic diff`) and reports each change to the listener individually.
     *
     * @return Summarized figures of added and removed data.
     */
    @JvmOverloads
    public fun diff(
        firstSnapshotId: String,
        secondSnapshotId: String,
        options: DiffOptions = DiffOptions.DEFAULT,
        listener: DiffListener? = null,
    ): DiffStatistics = DiffCommand(runner).diff(firstSnapshotId, secondSnapshotId, options, listener)

    /** Like [diff], but collects all changes in a list. */
    @JvmOverloads
    public fun diffAll(
        firstSnapshotId: String,
        secondSnapshotId: String,
        options: DiffOptions = DiffOptions.DEFAULT,
    ): DiffResult {
        val changes = mutableListOf<DiffChange>()
        val statistics = diff(firstSnapshotId, secondSnapshotId, options) { changes += it }
        return DiffResult(changes, statistics)
    }

    /**
     * Mounts the repository as a directory tree through FUSE (`restic mount`, not on Windows).
     * The call returns immediately; [MountHandle.awaitMounted] waits until it is ready, [MountHandle.close] unmounts again.
     */
    @JvmOverloads
    public fun mount(
        mountpoint: Path,
        options: MountOptions = MountOptions.DEFAULT,
    ): MountHandle = MountCommand(runner).mount(mountpoint, options)
}
