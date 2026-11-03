package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * The repository configuration (`restic cat config`).
 *
 * @property version Repository format version: 1 without, 2 with compression.
 * @property id Unique ID of the repository.
 * @property chunkerPolynomial Polynomial restic uses to split files into blobs; repositories with the same polynomial
 *   deduplicate together, see [InitOptions.Builder.copyChunkerParams].
 */
public data class RepositoryConfig(
    val version: Int,
    val id: String,
    val chunkerPolynomial: String,
)

/**
 * The repository's master key material (`restic cat masterkey`); treat it as a secret.
 *
 * @property macKey Key for the Poly1305 authentication, Base64.
 * @property macRandom Random part of the Poly1305 authentication, Base64.
 * @property encryptionKey AES-256 key for the data, Base64.
 */
public data class MasterKey(
    val macKey: String,
    val macRandom: String,
    val encryptionKey: String,
)

/**
 * An entry of a tree object: a file, a directory or another file type.
 *
 * @property name Name without path.
 * @property type `file`, `dir`, `symlink` or another file type.
 * @property mode Unix file mode.
 * @property modificationTime Last modification.
 * @property size Size in bytes, files only.
 * @property uid User ID of the owner.
 * @property gid Group ID of the owner.
 * @property user Name of the owner.
 * @property group Name of the group.
 * @property content IDs of the content blobs, in order.
 * @property subtree Tree ID of the subdirectory, directories only.
 */
public data class TreeNode(
    val name: String,
    val type: String,
    val mode: Long?,
    val modificationTime: Instant?,
    val size: Long?,
    val uid: Long?,
    val gid: Long?,
    val user: String?,
    val group: String?,
    val content: List<String>,
    val subtree: String?,
)

/**
 * A directory in the repository (`restic cat tree`).
 *
 * @property nodes The entries of the directory.
 */
public data class TreeObject(
    val nodes: List<TreeNode>,
)

internal fun repositoryConfigFrom(node: JsonNode) =
    RepositoryConfig(node.long("version").toInt(), node.text("id"), node.text("chunker_polynomial"))

internal fun masterKeyFrom(node: JsonNode) =
    MasterKey(node.get("mac")?.text("k").orEmpty(), node.get("mac")?.text("r").orEmpty(), node.text("encrypt"))

internal fun keyContentFrom(node: JsonNode) =
    KeyContent(
        created = node.instantOrNull("created"),
        username = node.text("username"),
        hostname = node.text("hostname"),
        kdf = node.text("kdf"),
        n = node.long("N"),
        r = node.long("r"),
        p = node.long("p"),
        salt = node.text("salt"),
        data = node.text("data"),
    )

internal fun lockContentFrom(node: JsonNode) =
    LockContent(
        time = node.instantOrNull("time"),
        exclusive = node.booleanOrNull("exclusive") ?: false,
        hostname = node.text("hostname"),
        username = node.text("username"),
        pid = node.long("pid"),
        uid = node.longOrNull("uid"),
        gid = node.longOrNull("gid"),
    )

internal fun treeObjectFrom(node: JsonNode) = TreeObject(node.get("nodes")?.mapElements(::treeNodeFrom).orEmpty())

private fun treeNodeFrom(node: JsonNode) =
    TreeNode(
        name = node.text("name"),
        type = node.text("type"),
        mode = node.longOrNull("mode"),
        modificationTime = node.instantOrNull("mtime"),
        size = node.longOrNull("size"),
        uid = node.longOrNull("uid"),
        gid = node.longOrNull("gid"),
        user = node.textOrNull("user"),
        group = node.textOrNull("group"),
        content = node.textList("content"),
        subtree = node.textOrNull("subtree"),
    )

internal fun indexContentFrom(node: JsonNode) =
    IndexContent(node.textList("supersedes"), node.get("packs")?.mapElements(::indexPackFrom).orEmpty())

private fun indexPackFrom(node: JsonNode) = IndexPack(node.text("id"), node.get("blobs")?.mapElements(::indexBlobFrom).orEmpty())

private fun indexBlobFrom(node: JsonNode) =
    IndexBlob(node.text("id"), node.text("type"), node.long("offset"), node.long("length"), node.longOrNull("uncompressed_length"))
