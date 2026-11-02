package io.github.ppuetsch.restic

/** Objektarten, die `restic list` aufzählen kann (ohne `blobs`, siehe [Restic.listBlobs]). */
public enum class ListObjectType(
    internal val cliValue: String,
) {
    /** Pack-Dateien mit den eigentlichen Daten. */
    PACKS("packs"),

    /** Indexdateien. */
    INDEX("index"),

    /** Snapshots. */
    SNAPSHOTS("snapshots"),

    /** Schlüsseldateien. */
    KEYS("keys"),

    /** Sperrdateien. */
    LOCKS("locks"),
}

/**
 * Ein Blob des Repositorys, wie [Restic.listBlobs] ihn liefert.
 *
 * @property type `data` oder `tree`.
 * @property id ID des Blobs.
 */
public data class BlobEntry(
    val type: String,
    val id: String,
)

internal class ListCommand(
    private val runner: ProcessRunner,
) {
    fun list(type: ListObjectType): List<String> = readLines(type.cliValue).map(String::trim)

    fun listBlobs(): List<BlobEntry> =
        readLines("blobs").map { line ->
            val parts = line.trim().split(' ')
            BlobEntry(parts[0], parts.getOrElse(1) { "" })
        }

    private fun readLines(cliType: String): List<String> {
        val lines = mutableListOf<String>()
        runner.runChecked(listOf("list", cliType)) { if (it.isNotBlank()) lines += it }
        return lines
    }
}
