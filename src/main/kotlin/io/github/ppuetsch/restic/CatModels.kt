package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Instant

public data class RepositoryConfig(
    val version: Int,
    val id: String,
    val chunkerPolynomial: String,
)

/** Das Hauptschlüsselmaterial des Repositorys; geheim zu behandeln. */
public data class MasterKey(
    val macKey: String,
    val macRandom: String,
    val encryptionKey: String,
)

/** Eine Schlüsseldatei des Repositorys (`restic cat key`). */
public data class RepositoryKey(
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

public data class RepositoryLock(
    val time: Instant?,
    val exclusive: Boolean,
    val hostname: String,
    val username: String,
    val pid: Long,
    val uid: Long?,
    val gid: Long?,
)

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
    /** Blob-IDs der Dateiinhalte. */
    val content: List<String>,
    /** Tree-ID des Unterverzeichnisses. */
    val subtree: String?,
)

public data class TreeObject(
    val nodes: List<TreeNode>,
)

public data class IndexBlob(
    val id: String,
    val type: String,
    val offset: Long,
    val length: Long,
    val uncompressedLength: Long?,
)

public data class IndexPack(
    val id: String,
    val blobs: List<IndexBlob>,
)

public data class IndexObject(
    val supersedes: List<String>,
    val packs: List<IndexPack>,
)

internal fun repositoryConfigFrom(node: JsonNode) =
    RepositoryConfig(node.long("version").toInt(), node.text("id"), node.text("chunker_polynomial"))

internal fun masterKeyFrom(node: JsonNode) =
    MasterKey(node.get("mac")?.text("k").orEmpty(), node.get("mac")?.text("r").orEmpty(), node.text("encrypt"))

internal fun repositoryKeyFrom(node: JsonNode) =
    RepositoryKey(
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

internal fun repositoryLockFrom(node: JsonNode) =
    RepositoryLock(
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

internal fun indexObjectFrom(node: JsonNode) =
    IndexObject(node.textList("supersedes"), node.get("packs")?.mapElements(::indexPackFrom).orEmpty())

private fun indexPackFrom(node: JsonNode) = IndexPack(node.text("id"), node.get("blobs")?.mapElements(::indexBlobFrom).orEmpty())

private fun indexBlobFrom(node: JsonNode) =
    IndexBlob(node.text("id"), node.text("type"), node.long("offset"), node.long("length"), node.longOrNull("uncompressed_length"))
