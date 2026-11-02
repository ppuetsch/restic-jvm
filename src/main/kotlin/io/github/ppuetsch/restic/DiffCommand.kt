package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/**
 * Optionen für [Restic.diff] und [Restic.diffAll] (`restic diff`).
 *
 * @property metadata Meldet auch reine Metadaten-Änderungen (`--metadata`).
 * @property cancellationToken Token, über das sich der Aufruf abbrechen lässt.
 */
public class DiffOptions private constructor(
    builder: Builder,
) {
    public val metadata: Boolean = builder.metadata
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> = buildList { optionalFlag("--metadata", metadata) }

    /** Stellt [DiffOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal var metadata: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        /** Meldet auch reine Metadaten-Änderungen wie Rechte oder Zeitstempel (`--metadata`). */
        @JvmOverloads
        public fun metadata(enabled: Boolean = true): Builder = apply { metadata = enabled }

        /** Token, über das sich der Aufruf von einem anderen Thread aus abbrechen lässt. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): DiffOptions = DiffOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: restic-Standardverhalten. */
        @JvmField
        public val DEFAULT: DiffOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Eine Änderung zwischen zwei Snapshots.
 *
 * @property path Pfad der geänderten Datei oder des Verzeichnisses.
 * @property modifier `+` hinzugefügt, `-` entfernt, `M` Inhalt geändert, `U` Metadaten geändert, `T` Typ geändert,
 *   `?` unbekannt.
 */
public data class DiffChange(
    val path: String,
    val modifier: String,
)

/**
 * Hinzugekommene oder entfernte Mengen zwischen zwei Snapshots.
 *
 * @property files Anzahl der Dateien.
 * @property directories Anzahl der Verzeichnisse.
 * @property others Anzahl anderer Einträge wie Symlinks oder Geräte.
 * @property dataBlobs Anzahl der Daten-Blobs.
 * @property treeBlobs Anzahl der Tree-Blobs.
 * @property bytes Größe der Blobs in Bytes.
 */
public data class DiffCounts(
    val files: Long,
    val directories: Long,
    val others: Long,
    val dataBlobs: Long,
    val treeBlobs: Long,
    val bytes: Long,
)

/**
 * Zusammenfassung eines Vergleichs zweier Snapshots.
 *
 * @property sourceSnapshotId Vollständige ID des ersten Snapshots.
 * @property targetSnapshotId Vollständige ID des zweiten Snapshots.
 * @property changedFiles Anzahl geänderter Dateien.
 * @property added Was im zweiten Snapshot hinzugekommen ist.
 * @property removed Was im zweiten Snapshot fehlt.
 */
public data class DiffStatistics(
    val sourceSnapshotId: String,
    val targetSnapshotId: String,
    val changedFiles: Long,
    val added: DiffCounts,
    val removed: DiffCounts,
)

/**
 * Ergebnis von [Restic.diffAll].
 *
 * @property changes Alle Änderungen in Ausgabereihenfolge.
 * @property statistics Die Zusammenfassung.
 */
public data class DiffResult(
    val changes: List<DiffChange>,
    val statistics: DiffStatistics,
)

/** Empfängt die Änderungen eines laufenden [Restic.diff]; wird im Thread aufgerufen, der die restic-Ausgabe liest. */
public fun interface DiffListener {
    /** Eine gefundene Änderung. */
    public fun onChange(change: DiffChange)
}

private fun diffCountsFrom(node: JsonNode?): DiffCounts =
    DiffCounts(
        files = node?.long("files") ?: 0,
        directories = node?.long("dirs") ?: 0,
        others = node?.long("others") ?: 0,
        dataBlobs = node?.long("data_blobs") ?: 0,
        treeBlobs = node?.long("tree_blobs") ?: 0,
        bytes = node?.long("bytes") ?: 0,
    )

internal class DiffCommand(
    private val runner: ProcessRunner,
) {
    fun diff(
        firstSnapshotId: String,
        secondSnapshotId: String,
        options: DiffOptions,
        listener: DiffListener?,
    ): DiffStatistics {
        var statistics: DiffStatistics? = null
        val arguments = listOf("diff", "--json") + options.arguments() + firstSnapshotId + secondSnapshotId
        runner.runChecked(ProcessRequest(arguments, cancellation = options.cancellationToken)) { line ->
            Json.parseOrNull(line)?.let { node ->
                when (node.messageType()) {
                    "change" -> {
                        listener?.onChange(DiffChange(node.text("path"), node.text("modifier")))
                    }

                    "statistics" -> {
                        statistics =
                            DiffStatistics(
                                sourceSnapshotId = node.text("source_snapshot"),
                                targetSnapshotId = node.text("target_snapshot"),
                                changedFiles = node.long("changed_files"),
                                added = diffCountsFrom(node.get("added")),
                                removed = diffCountsFrom(node.get("removed")),
                            )
                    }
                }
            }
        }
        return statistics ?: throw ResticOutputException("restic diff produced no statistics", null)
    }
}
