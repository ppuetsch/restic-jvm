package io.github.ppuetsch.restic

import java.time.Instant

/**
 * An object stored in the repository and known by its ID, as [Restic.list] returns it.
 *
 * Listing only reads the IDs. Types with more content ([Lock], [Key], [Index]) read it on the first access to any
 * other property, with one `restic cat` call that fills all properties at once. If that call fails, for example
 * because a lock was released in the meantime, the access throws a [ResticException] and the next access tries again.
 *
 * Two objects are equal when they have the same type and ID.
 *
 * @property id Full ID of the object.
 */
public sealed class RepositoryObject(
    public val id: String,
) {
    /** Equal when [other] has the same type and [id]. */
    override fun equals(other: Any?): Boolean = other is RepositoryObject && other.javaClass == javaClass && other.id == id

    /** Derived from [id] alone. */
    override fun hashCode(): Int = id.hashCode()

    /** Type and ID, for example `Lock(5f2c…)`; never triggers a read. */
    override fun toString(): String = "${javaClass.simpleName}($id)"
}

/** A pack file holding the actual data; there is nothing to read beyond its [id]. */
public class Pack internal constructor(
    id: String,
) : RepositoryObject(id)

/**
 * A blob as listed by `restic list blobs`.
 *
 * @property type `data` or `tree`.
 */
public class Blob internal constructor(
    id: String,
    public val type: String,
) : RepositoryObject(id)

/** The content of a lock file; see [Lock]. */
internal data class LockContent(
    val time: Instant?,
    val exclusive: Boolean,
    val hostname: String,
    val username: String,
    val pid: Long,
    val uid: Long?,
    val gid: Long?,
)

/**
 * A lock held on the repository. Everything except [id] is read on first access (`restic cat lock`), once.
 *
 * @property time When the lock was created or last refreshed.
 * @property exclusive `true` for an exclusive lock, for example one held by `prune`.
 * @property hostname Host of the locking process.
 * @property username User of the locking process.
 * @property pid Process ID of the locking process.
 * @property uid User ID on Unix, otherwise `null`.
 * @property gid Group ID on Unix, otherwise `null`.
 */
public class Lock internal constructor(
    id: String,
    content: Lazy<LockContent>,
) : RepositoryObject(id) {
    private val content: LockContent by content

    public val time: Instant? get() = content.time
    public val exclusive: Boolean get() = content.exclusive
    public val hostname: String get() = content.hostname
    public val username: String get() = content.username
    public val pid: Long get() = content.pid
    public val uid: Long? get() = content.uid
    public val gid: Long? get() = content.gid
}

/** The content of a key file; see [Key]. */
internal data class KeyContent(
    val created: Instant?,
    val username: String,
    val hostname: String,
    val kdf: String,
    val n: Long,
    val r: Long,
    val p: Long,
    val salt: String,
    val data: String,
)

/**
 * A key file: the repository's master key, encrypted with one password. Everything except [id] is read on first
 * access (`restic cat key`), once. [KeyCommands.list] tells which key is the one in use.
 *
 * @property created When the key was added.
 * @property username User who added the key.
 * @property hostname Host on which the key was added.
 * @property kdf Key derivation function, always `scrypt` for restic.
 * @property n scrypt parameter N (cost factor).
 * @property r scrypt parameter r (block size).
 * @property p scrypt parameter p (parallelism).
 * @property salt Salt of the key derivation, Base64.
 * @property data The encrypted master key, Base64.
 */
public class Key internal constructor(
    id: String,
    content: Lazy<KeyContent>,
) : RepositoryObject(id) {
    private val content: KeyContent by content

    public val created: Instant? get() = content.created
    public val username: String get() = content.username
    public val hostname: String get() = content.hostname
    public val kdf: String get() = content.kdf
    public val n: Long get() = content.n
    public val r: Long get() = content.r
    public val p: Long get() = content.p
    public val salt: String get() = content.salt
    public val data: String get() = content.data
}

/**
 * A blob inside a pack, according to an index.
 *
 * @property id ID of the blob.
 * @property type `data` or `tree`.
 * @property offset Position in the pack, in bytes.
 * @property length Length in the pack in bytes, encrypted and possibly compressed.
 * @property uncompressedLength Length before compression; `null` if the blob is not compressed.
 */
public data class IndexBlob(
    val id: String,
    val type: String,
    val offset: Long,
    val length: Long,
    val uncompressedLength: Long?,
)

/**
 * A pack according to an index.
 *
 * @property id ID of the pack file.
 * @property blobs The blobs in this pack.
 */
public data class IndexPack(
    val id: String,
    val blobs: List<IndexBlob>,
)

/** The content of an index file; see [Index]. */
internal data class IndexContent(
    val supersedes: List<String>,
    val packs: List<IndexPack>,
)

/**
 * An index file. Everything except [id] is read on first access (`restic cat index`), once.
 *
 * @property supersedes IDs of older index files this one replaces.
 * @property packs The packs it covers, with their blobs.
 */
public class Index internal constructor(
    id: String,
    content: Lazy<IndexContent>,
) : RepositoryObject(id) {
    private val content: IndexContent by content

    public val supersedes: List<String> get() = content.supersedes
    public val packs: List<IndexPack> get() = content.packs
}

internal fun lockIn(
    runner: ProcessRunner,
    id: String,
): Lock = Lock(id, lazy { lockContentFrom(DumpAndCatCommand(runner).catJson(CatObjectType.LOCK, id)) })

internal fun keyIn(
    runner: ProcessRunner,
    id: String,
): Key = Key(id, lazy { keyContentFrom(DumpAndCatCommand(runner).catJson(CatObjectType.KEY, id)) })

internal fun indexIn(
    runner: ProcessRunner,
    id: String,
): Index = Index(id, lazy { indexContentFrom(DumpAndCatCommand(runner).catJson(CatObjectType.INDEX, id)) })
