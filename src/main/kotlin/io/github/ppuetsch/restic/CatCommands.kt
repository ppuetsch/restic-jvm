package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.io.OutputStream

/**
 * Reading single repository objects by ID (`restic cat`). Obtained from [Restic.cat].
 *
 * Every method reads right away; [Restic.list] instead returns objects that read their content only when needed.
 */
public class CatCommands internal constructor(
    private val runner: ProcessRunner,
) {
    /**
     * Writes a repository object raw to the stream. The stream stays open.
     *
     * @param id Required for all types except [CatObjectType.CONFIG] and [CatObjectType.MASTERKEY].
     * @throws IllegalArgumentException if the type needs an ID and none is given.
     */
    @JvmOverloads
    public fun raw(
        type: CatObjectType,
        id: String? = null,
        output: OutputStream,
        cancellationToken: CancellationToken? = null,
    ): Unit = command(cancellationToken).cat(type, id, output)

    /** Like [raw], but returns the content as text; for JSON objects prefer the typed methods of this class. */
    @JvmOverloads
    public fun text(
        type: CatObjectType,
        id: String? = null,
        cancellationToken: CancellationToken? = null,
    ): String = command(cancellationToken).catText(type, id)

    /** The configuration of the repository (`restic cat config`). */
    @JvmOverloads
    public fun config(cancellationToken: CancellationToken? = null): RepositoryConfig =
        repositoryConfigFrom(json(CatObjectType.CONFIG, null, cancellationToken))

    /** The decrypted master key of the repository (`restic cat masterkey`); secret, do not log it. */
    @JvmOverloads
    public fun masterKey(cancellationToken: CancellationToken? = null): MasterKey =
        masterKeyFrom(json(CatObjectType.MASTERKEY, null, cancellationToken))

    /** A single snapshot with all fields (`restic cat snapshot`). */
    @JvmOverloads
    public fun snapshot(
        snapshotId: String,
        cancellationToken: CancellationToken? = null,
    ): Snapshot = command(cancellationToken).catSnapshot(snapshotId)

    /** A key file (`restic cat key`). */
    @JvmOverloads
    public fun key(
        keyId: String,
        cancellationToken: CancellationToken? = null,
    ): Key = Key(keyId, lazyOf(keyContentFrom(json(CatObjectType.KEY, keyId, cancellationToken))))

    /** A lock (`restic cat lock`). */
    @JvmOverloads
    public fun lock(
        lockId: String,
        cancellationToken: CancellationToken? = null,
    ): Lock = Lock(lockId, lazyOf(lockContentFrom(json(CatObjectType.LOCK, lockId, cancellationToken))))

    /** [snapshotAndSubfolder] has the form `snapshotId` or `snapshotId:subfolder` and names the tree of that directory. */
    @JvmOverloads
    public fun tree(
        snapshotAndSubfolder: String,
        cancellationToken: CancellationToken? = null,
    ): TreeObject = treeObjectFrom(json(CatObjectType.TREE, snapshotAndSubfolder, cancellationToken))

    /** An index file (`restic cat index`). */
    @JvmOverloads
    public fun index(
        indexId: String,
        cancellationToken: CancellationToken? = null,
    ): Index = Index(indexId, lazyOf(indexContentFrom(json(CatObjectType.INDEX, indexId, cancellationToken))))

    private fun command(cancellationToken: CancellationToken?): DumpAndCatCommand =
        DumpAndCatCommand(runner.cancellableBy(cancellationToken))

    private fun json(
        type: CatObjectType,
        id: String?,
        cancellationToken: CancellationToken?,
    ): JsonNode = command(cancellationToken).catJson(type, id)
}
