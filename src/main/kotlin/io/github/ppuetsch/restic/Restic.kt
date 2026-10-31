package io.github.ppuetsch.restic

import com.fasterxml.jackson.databind.JsonNode
import java.io.InputStream
import java.nio.file.Path

/** Einstiegspunkt: eine Instanz pro Repository-Konfiguration; thread-safe, da zustandslos. */
public class Restic(public val configuration: ResticConfiguration) {
    private val runner = ProcessRunner(configuration)
    private val backupCommand = BackupCommand(runner)
    private val snapshotsCommand = SnapshotsCommand(runner)
    private val lsCommand = LsCommand(runner)
    private val findCommand = FindCommand(runner)

    public fun version(): ResticVersion {
        val node = lastJsonLine(listOf("version", "--json"))
        return ResticVersion(node.text("version"), node.text("go_version"), node.text("go_os"), node.text("go_arch"))
    }

    @JvmOverloads
    public fun init(options: InitOptions = InitOptions.DEFAULT): InitResult {
        val node = lastJsonLine(listOf("init", "--json") + options.arguments(), options.environment())
        return InitResult(node.text("id"), node.text("repository"))
    }

    @JvmSynthetic
    public fun init(configure: InitOptions.Builder.() -> Unit): InitResult = init(InitOptions.builder().apply(configure).build())

    /** Sichert Dateien und Verzeichnisse. Pfade dürfen leer sein, wenn die Optionen `filesFrom*` setzen. */
    @JvmOverloads
    public fun backup(
        paths: List<Path>,
        options: BackupOptions = BackupOptions.DEFAULT,
        listener: BackupListener? = null,
    ): BackupResult = backupCommand.backupPaths(paths.map(Path::toString), options, listener)

    @JvmSynthetic
    public fun backup(
        paths: List<Path>,
        listener: BackupListener? = null,
        configure: BackupOptions.Builder.() -> Unit,
    ): BackupResult = backup(paths, BackupOptions.builder().apply(configure).build(), listener)

    /** Sichert den Inhalt des Streams als eine Datei (`stdinFilename`, Standard `stdin`). */
    @JvmOverloads
    public fun backupStandardInput(
        input: InputStream,
        options: BackupOptions = BackupOptions.DEFAULT,
        listener: BackupListener? = null,
    ): BackupResult = backupCommand.backupStandardInput(input, options, listener)

    /** Führt das Kommando aus und sichert dessen Standardausgabe als eine Datei. */
    @JvmOverloads
    public fun backupCommandOutput(
        command: List<String>,
        options: BackupOptions = BackupOptions.DEFAULT,
        listener: BackupListener? = null,
    ): BackupResult = backupCommand.backupCommandOutput(command, options, listener)

    /** Alle passenden Snapshots als flache Liste, auch wenn `groupBy` gesetzt ist. */
    @JvmOverloads
    public fun snapshots(options: SnapshotsOptions = SnapshotsOptions.DEFAULT): List<Snapshot> = snapshotsCommand.snapshots(options)

    @JvmSynthetic
    public fun snapshots(configure: SnapshotsOptions.Builder.() -> Unit): List<Snapshot> =
        snapshots(SnapshotsOptions.builder().apply(configure).build())

    /** Snapshots gruppiert; ohne `groupBy` wird nach `host,paths` gruppiert. */
    @JvmOverloads
    public fun snapshotGroups(options: SnapshotsOptions = SnapshotsOptions.DEFAULT): List<SnapshotGroup> =
        snapshotsCommand.snapshotGroups(options)

    /** Listet den Inhalt eines Snapshots (`latest` oder eine ID) und meldet jeden Eintrag an den Listener. */
    @JvmOverloads
    public fun ls(
        snapshotId: String = LATEST,
        options: LsOptions = LsOptions.DEFAULT,
        listener: LsListener? = null,
    ): Snapshot = lsCommand.ls(snapshotId, options, listener)

    @JvmSynthetic
    public fun ls(
        snapshotId: String = LATEST,
        listener: LsListener? = null,
        configure: LsOptions.Builder.() -> Unit,
    ): Snapshot = ls(snapshotId, LsOptions.builder().apply(configure).build(), listener)

    /** Wie [ls], sammelt aber alle Einträge in einer Liste. */
    @JvmOverloads
    public fun listNodes(
        snapshotId: String = LATEST,
        options: LsOptions = LsOptions.DEFAULT,
    ): LsResult {
        val nodes = mutableListOf<LsNode>()
        val snapshot = ls(snapshotId, options) { nodes += it }
        return LsResult(snapshot, nodes)
    }

    /** `restic ls --ncdu`: Export im NCDU-Format als Text. */
    @JvmOverloads
    public fun lsNcdu(
        snapshotId: String = LATEST,
        options: LsOptions = LsOptions.DEFAULT,
    ): String = lsCommand.ncdu(snapshotId, options)

    /** Sucht Dateien oder Verzeichnisse nach Namensmuster in den Snapshots. */
    @JvmOverloads
    public fun find(
        patterns: List<String>,
        options: FindOptions = FindOptions.DEFAULT,
    ): List<FindSnapshotMatches> = findCommand.findNames(patterns, options)

    @JvmSynthetic
    public fun find(
        patterns: List<String>,
        configure: FindOptions.Builder.() -> Unit,
    ): List<FindSnapshotMatches> = find(patterns, FindOptions.builder().apply(configure).build())

    /** Sucht, in welchen Snapshots ein Blob, Tree oder Pack vorkommt. */
    @JvmOverloads
    public fun findObjects(
        kind: FindObjectKind,
        ids: List<String>,
        options: FindOptions = FindOptions.DEFAULT,
    ): List<FindObject> = findCommand.findObjects(kind, ids, options)

    private fun lastJsonLine(
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
    ): JsonNode {
        var lastLine = ""
        runner.runChecked(ProcessRequest(arguments, environment)) { line -> if (line.isNotBlank()) lastLine = line }
        return Json.parse(lastLine)
    }

    public companion object {
        public const val LATEST: String = "latest"
    }
}
