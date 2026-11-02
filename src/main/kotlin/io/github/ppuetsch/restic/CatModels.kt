package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * Die Konfiguration des Repositorys (`restic cat config`).
 *
 * @property version Format-Version des Repositorys: 1 ohne, 2 mit Kompression.
 * @property id Eindeutige ID des Repositorys.
 * @property chunkerPolynomial Polynom, mit dem restic Dateien in Blobs zerlegt; Repositorys mit gleichem Polynom
 *   deduplizieren gemeinsam, siehe [InitOptions.Builder.copyChunkerParams].
 */
public data class RepositoryConfig(
    val version: Int,
    val id: String,
    val chunkerPolynomial: String,
)

/**
 * Das Hauptschlüsselmaterial des Repositorys (`restic cat masterkey`); geheim zu behandeln.
 *
 * @property macKey Schlüssel für die Poly1305-Authentifizierung, Base64.
 * @property macRandom Zufallsanteil der Poly1305-Authentifizierung, Base64.
 * @property encryptionKey AES-256-Schlüssel für die Daten, Base64.
 */
public data class MasterKey(
    val macKey: String,
    val macRandom: String,
    val encryptionKey: String,
)

/**
 * Eine Schlüsseldatei des Repositorys (`restic cat key`): der mit dem Passwort verschlüsselte Hauptschlüssel.
 *
 * @property created Anlagezeitpunkt.
 * @property username Benutzer, der den Schlüssel angelegt hat.
 * @property hostname Rechner, auf dem der Schlüssel angelegt wurde.
 * @property kdf Schlüsselableitungsfunktion, bei restic immer `scrypt`.
 * @property n scrypt-Parameter N (Kostenfaktor).
 * @property r scrypt-Parameter r (Blockgröße).
 * @property p scrypt-Parameter p (Parallelität).
 * @property salt Salt der Schlüsselableitung, Base64.
 * @property data Der verschlüsselte Hauptschlüssel, Base64.
 */
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

/**
 * Eine Sperrdatei des Repositorys (`restic cat lock`).
 *
 * @property time Zeitpunkt, zu dem die Sperre angelegt oder zuletzt erneuert wurde.
 * @property exclusive `true` bei einer exklusiven Sperre, etwa von `prune`.
 * @property hostname Rechner des sperrenden Prozesses.
 * @property username Benutzer des sperrenden Prozesses.
 * @property pid Prozess-ID des sperrenden Prozesses.
 * @property uid Benutzer-ID unter Unix, sonst `null`.
 * @property gid Gruppen-ID unter Unix, sonst `null`.
 */
public data class RepositoryLock(
    val time: Instant?,
    val exclusive: Boolean,
    val hostname: String,
    val username: String,
    val pid: Long,
    val uid: Long?,
    val gid: Long?,
)

/**
 * Ein Eintrag eines Tree-Objekts: eine Datei, ein Verzeichnis oder ein anderer Dateityp.
 *
 * @property name Name ohne Pfad.
 * @property type `file`, `dir`, `symlink` oder ein anderer Dateityp.
 * @property mode Unix-Dateimodus.
 * @property modificationTime Letzte Änderung.
 * @property size Größe in Bytes, nur bei Dateien.
 * @property uid Benutzer-ID des Eigentümers.
 * @property gid Gruppen-ID des Eigentümers.
 * @property user Name des Eigentümers.
 * @property group Name der Gruppe.
 * @property content Blob-IDs der Dateiinhalte in Reihenfolge.
 * @property subtree Tree-ID des Unterverzeichnisses, nur bei Verzeichnissen.
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
 * Ein Verzeichnis im Repository (`restic cat tree`).
 *
 * @property nodes Die Einträge des Verzeichnisses.
 */
public data class TreeObject(
    val nodes: List<TreeNode>,
)

/**
 * Ein Blob innerhalb eines Packs laut Index.
 *
 * @property id ID des Blobs.
 * @property type `data` oder `tree`.
 * @property offset Position im Pack in Bytes.
 * @property length Länge im Pack in Bytes, verschlüsselt und gegebenenfalls komprimiert.
 * @property uncompressedLength Länge vor der Kompression; `null`, wenn der Blob nicht komprimiert ist.
 */
public data class IndexBlob(
    val id: String,
    val type: String,
    val offset: Long,
    val length: Long,
    val uncompressedLength: Long?,
)

/**
 * Ein Pack laut Index.
 *
 * @property id ID der Pack-Datei.
 * @property blobs Die Blobs in diesem Pack.
 */
public data class IndexPack(
    val id: String,
    val blobs: List<IndexBlob>,
)

/**
 * Eine Indexdatei des Repositorys (`restic cat index`).
 *
 * @property supersedes IDs älterer Indexdateien, die diese ersetzt.
 * @property packs Die erfassten Packs mit ihren Blobs.
 */
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
