package io.github.ppuetsch.restic

/** Objektarten, die `restic list` aufzählen kann (ohne `blobs`, siehe [Restic.listBlobs]). */
public enum class ListObjectType(
    internal val cliValue: String,
) {
    PACKS("packs"),
    INDEX("index"),
    SNAPSHOTS("snapshots"),
    KEYS("keys"),
    LOCKS("locks"),
}

public data class BlobEntry(
    /** `data` oder `tree`. */
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
