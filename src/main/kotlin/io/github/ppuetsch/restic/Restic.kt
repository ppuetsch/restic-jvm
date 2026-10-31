package io.github.ppuetsch.restic

import com.fasterxml.jackson.databind.JsonNode
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

/** Einstiegspunkt: eine Instanz pro Repository-Konfiguration; thread-safe, da zustandslos. */
public class Restic(public val configuration: ResticConfiguration) {
    private val runner = ProcessRunner(configuration)
    private val backupCommand = BackupCommand(runner)
    private val snapshotsCommand = SnapshotsCommand(runner)
    private val lsCommand = LsCommand(runner)
    private val findCommand = FindCommand(runner)
    private val restoreCommand = RestoreCommand(runner)
    private val dumpAndCatCommand = DumpAndCatCommand(runner)
    private val forgetCommand = ForgetCommand(runner)
    private val pruneCommand = PruneCommand(runner)
    private val checkAndStatsCommand = CheckAndStatsCommand(runner)
    private val diffCommand = DiffCommand(runner)
    private val tagCommand = TagCommand(runner)
    private val rewriteAndCopyCommand = RewriteAndCopyCommand(runner)
    private val listCommand = ListCommand(runner)
    private val adminCommands = AdminCommands(runner)

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

    /** Stellt einen Snapshot (`latest`, ID oder `id:unterordner`) im Zielverzeichnis wieder her. */
    @JvmOverloads
    public fun restore(
        snapshotId: String,
        target: Path,
        options: RestoreOptions = RestoreOptions.DEFAULT,
        listener: RestoreListener? = null,
    ): RestoreSummary = restoreCommand.restore(snapshotId, target, options, listener)

    @JvmSynthetic
    public fun restore(
        snapshotId: String,
        target: Path,
        listener: RestoreListener? = null,
        configure: RestoreOptions.Builder.() -> Unit,
    ): RestoreSummary = restore(snapshotId, target, RestoreOptions.builder().apply(configure).build(), listener)

    /** Schreibt eine Datei (oder ein Verzeichnis als Archiv) aus dem Snapshot in den Stream. */
    @JvmOverloads
    public fun dump(
        snapshotId: String,
        fileInSnapshot: String,
        output: OutputStream,
        options: DumpOptions = DumpOptions.DEFAULT,
    ): Unit = dumpAndCatCommand.dump(snapshotId, fileInSnapshot, options, output)

    /** Wie [dump], schreibt aber direkt in eine Zieldatei (`--target`). */
    @JvmOverloads
    public fun dumpToFile(
        snapshotId: String,
        fileInSnapshot: String,
        target: Path,
        options: DumpOptions = DumpOptions.DEFAULT,
    ): Unit = dumpAndCatCommand.dumpToFile(snapshotId, fileInSnapshot, target, options)

    /** Gibt ein Repository-Objekt roh in den Stream aus (`restic cat`). Für Typen ohne feste ID genügt `id = null`. */
    @JvmOverloads
    public fun cat(
        type: CatObjectType,
        id: String? = null,
        output: OutputStream,
    ): Unit = dumpAndCatCommand.cat(type, id, output)

    /** Wie [cat], liefert aber den Inhalt als Text (sinnvoll für JSON-Objekte wie config oder snapshot). */
    @JvmOverloads
    public fun catText(
        type: CatObjectType,
        id: String? = null,
    ): String = dumpAndCatCommand.catText(type, id)

    /** Entfernt Snapshots nach Aufbewahrungsregeln oder gezielt per ID. */
    @JvmOverloads
    public fun forget(
        options: ForgetOptions,
        outputListener: OutputLineListener? = null,
    ): ForgetResult = forgetCommand.forget(options, outputListener)

    @JvmSynthetic
    public fun forget(configure: ForgetOptions.Builder.() -> Unit): ForgetResult = forget(ForgetOptions.builder().apply(configure).build())

    /** Gibt ungenutzten Speicher im Repository frei. */
    @JvmOverloads
    public fun prune(
        options: PruneOptions = PruneOptions.DEFAULT,
        listener: OutputLineListener? = null,
    ): PruneResult = pruneCommand.prune(options, listener)

    @JvmSynthetic
    public fun prune(configure: PruneOptions.Builder.() -> Unit): PruneResult = prune(PruneOptions.builder().apply(configure).build())

    /** Prüft das Repository; Fehler im Repository führen zu [CheckResult.isHealthy] = false statt zu einer Exception. */
    @JvmOverloads
    public fun check(options: CheckOptions = CheckOptions.DEFAULT): CheckResult = checkAndStatsCommand.check(options)

    @JvmSynthetic
    public fun check(configure: CheckOptions.Builder.() -> Unit): CheckResult = check(CheckOptions.builder().apply(configure).build())

    @JvmOverloads
    public fun stats(options: StatsOptions = StatsOptions.DEFAULT): StatsResult = checkAndStatsCommand.stats(options)

    @JvmSynthetic
    public fun stats(configure: StatsOptions.Builder.() -> Unit): StatsResult = stats(StatsOptions.builder().apply(configure).build())

    /** Vergleicht zwei Snapshots und meldet jede Änderung an den Listener. */
    @JvmOverloads
    public fun diff(
        firstSnapshotId: String,
        secondSnapshotId: String,
        options: DiffOptions = DiffOptions.DEFAULT,
        listener: DiffListener? = null,
    ): DiffStatistics = diffCommand.diff(firstSnapshotId, secondSnapshotId, options, listener)

    /** Wie [diff], sammelt aber alle Änderungen in einer Liste. */
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

    /** Ändert Tags von Snapshots; restic legt dafür neue Snapshots an. */
    public fun tag(options: TagOptions): TagResult = tagCommand.tag(options)

    @JvmSynthetic
    public fun tag(configure: TagOptions.Builder.() -> Unit): TagResult = tag(TagOptions.builder().apply(configure).build())

    /** Schreibt Snapshots um (Ausschlüsse, Host, Zeit). */
    @JvmOverloads
    public fun rewrite(
        options: RewriteOptions,
        listener: OutputLineListener? = null,
    ): RewriteResult = rewriteAndCopyCommand.rewrite(options, listener)

    @JvmSynthetic
    public fun rewrite(configure: RewriteOptions.Builder.() -> Unit): RewriteResult =
        rewrite(RewriteOptions.builder().apply(configure).build())

    /** Kopiert Snapshots aus dem in den Optionen genannten Quell-Repository in dieses Repository. */
    @JvmOverloads
    public fun copy(
        options: CopyOptions,
        listener: OutputLineListener? = null,
    ): CopyResult = rewriteAndCopyCommand.copy(options, listener)

    @JvmSynthetic
    public fun copy(configure: CopyOptions.Builder.() -> Unit): CopyResult = copy(CopyOptions.builder().apply(configure).build())

    /** Zählt Objekt-IDs des Repositorys auf (`restic list`). */
    public fun list(type: ListObjectType): List<String> = listCommand.list(type)

    public fun listBlobs(): List<BlobEntry> = listCommand.listBlobs()

    public fun listKeys(): List<KeyInfo> = adminCommands.listKeys()

    /** Fügt dem Repository einen weiteren Schlüssel (Passwort) hinzu. */
    @JvmOverloads
    public fun addKey(options: NewKeyOptions = NewKeyOptions.DEFAULT): KeyAddResult = adminCommands.addKey(options)

    @JvmSynthetic
    public fun addKey(configure: NewKeyOptions.Builder.() -> Unit): KeyAddResult = addKey(NewKeyOptions.builder().apply(configure).build())

    /** Ersetzt den aktuell verwendeten Schlüssel durch einen mit neuem Passwort. */
    @JvmOverloads
    public fun changePassword(options: NewKeyOptions = NewKeyOptions.DEFAULT): CommandOutput = adminCommands.changePassword(options)

    @JvmSynthetic
    public fun changePassword(configure: NewKeyOptions.Builder.() -> Unit): CommandOutput =
        changePassword(NewKeyOptions.builder().apply(configure).build())

    public fun removeKey(keyId: String): CommandOutput = adminCommands.removeKey(keyId)

    /** Entfernt veraltete Sperren; mit `removeAll` auch aktive. */
    @JvmOverloads
    public fun unlock(removeAll: Boolean = false): CommandOutput = adminCommands.unlock(removeAll)

    /** Ohne Namen werden die verfügbaren Migrationen aufgelistet. */
    @JvmOverloads
    public fun migrate(
        names: List<String> = emptyList(),
        force: Boolean = false,
    ): CommandOutput = adminCommands.migrate(names, force)

    @JvmOverloads
    public fun cache(options: CacheOptions = CacheOptions.DEFAULT): CommandOutput = adminCommands.cache(options)

    @JvmSynthetic
    public fun cache(configure: CacheOptions.Builder.() -> Unit): CommandOutput = cache(CacheOptions.builder().apply(configure).build())

    public fun features(): List<FeatureFlag> = adminCommands.features()

    /** Baut Snapshots aus verwaisten Bäumen wieder auf (`restic recover`). */
    public fun recover(): CommandOutput = adminCommands.recover()

    @JvmOverloads
    public fun repairIndex(readAllPacks: Boolean = false): CommandOutput = adminCommands.repairIndex(readAllPacks)

    public fun repairPacks(packIds: List<String>): CommandOutput = adminCommands.repairPacks(packIds)

    @JvmOverloads
    public fun repairSnapshots(options: RepairSnapshotsOptions = RepairSnapshotsOptions.DEFAULT): CommandOutput =
        adminCommands.repairSnapshots(options)

    @JvmSynthetic
    public fun repairSnapshots(configure: RepairSnapshotsOptions.Builder.() -> Unit): CommandOutput =
        repairSnapshots(RepairSnapshotsOptions.builder().apply(configure).build())

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
