package io.github.ppuetsch.restic

import com.fasterxml.jackson.databind.JsonNode
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

/** Einstiegspunkt: eine Instanz pro Repository-Konfiguration; thread-safe, da zustandslos. */
public class Restic(public val configuration: ResticConfiguration) {
    private val runner = ProcessRunner(configuration)
    private val backupCommand = BackupCommand(runner)
    private val restoreCommand = RestoreCommand(runner)
    private val forgetCommand = ForgetCommand(runner)
    private val pruneCommand = PruneCommand(runner)
    private val checkAndStatsCommand = CheckAndStatsCommand(runner)
    private val diffCommand = DiffCommand(runner)
    private val rewriteAndCopyCommand = RewriteAndCopyCommand(runner)
    private val mountCommand = MountCommand(runner)

    @JvmOverloads
    public fun version(cancellationToken: CancellationToken? = null): ResticVersion {
        val node = lastJsonLine(listOf("version", "--json"), cancellationToken = cancellationToken)
        return ResticVersion(node.text("version"), node.text("go_version"), node.text("go_os"), node.text("go_arch"))
    }

    @JvmOverloads
    public fun init(
        options: InitOptions = InitOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): InitResult {
        val node = lastJsonLine(listOf("init", "--json") + options.arguments(), options.environment(), cancellationToken)
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
    public fun snapshots(
        options: SnapshotsOptions = SnapshotsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): List<Snapshot> = SnapshotsCommand(runner.cancellableBy(cancellationToken)).snapshots(options)

    @JvmSynthetic
    public fun snapshots(configure: SnapshotsOptions.Builder.() -> Unit): List<Snapshot> =
        snapshots(SnapshotsOptions.builder().apply(configure).build())

    /** Snapshots gruppiert; ohne `groupBy` wird nach `host,paths` gruppiert. */
    @JvmOverloads
    public fun snapshotGroups(
        options: SnapshotsOptions = SnapshotsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): List<SnapshotGroup> = SnapshotsCommand(runner.cancellableBy(cancellationToken)).snapshotGroups(options)

    /** Listet den Inhalt eines Snapshots (`latest` oder eine ID) und meldet jeden Eintrag an den Listener. */
    @JvmOverloads
    public fun ls(
        snapshotId: String = LATEST,
        options: LsOptions = LsOptions.DEFAULT,
        listener: LsListener? = null,
    ): Snapshot = ls(snapshotId, options, null, listener)

    /**
     * Wie [ls], abbrechbar über [cancellationToken]. Der Listener steht zuletzt, damit ein Lambda am Ende des Aufrufs
     * weiterhin der Listener bleibt; deshalb sind hier alle Parameter Pflicht.
     */
    public fun ls(
        snapshotId: String,
        options: LsOptions,
        cancellationToken: CancellationToken?,
        listener: LsListener?,
    ): Snapshot = LsCommand(runner.cancellableBy(cancellationToken)).ls(snapshotId, options, listener)

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
        cancellationToken: CancellationToken? = null,
    ): LsResult {
        val nodes = mutableListOf<LsNode>()
        val snapshot = ls(snapshotId, options, cancellationToken) { nodes += it }
        return LsResult(snapshot, nodes)
    }

    /** Sucht Dateien oder Verzeichnisse nach Namensmuster in den Snapshots. */
    @JvmOverloads
    public fun find(
        patterns: List<String>,
        options: FindOptions = FindOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): List<FindSnapshotMatches> = FindCommand(runner.cancellableBy(cancellationToken)).findNames(patterns, options)

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
        cancellationToken: CancellationToken? = null,
    ): List<FindObject> = FindCommand(runner.cancellableBy(cancellationToken)).findObjects(kind, ids, options)

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
        cancellationToken: CancellationToken? = null,
    ): Unit = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).dump(snapshotId, fileInSnapshot, options, output)

    /** Wie [dump], schreibt aber direkt in eine Zieldatei (`--target`). */
    @JvmOverloads
    public fun dumpToFile(
        snapshotId: String,
        fileInSnapshot: String,
        target: Path,
        options: DumpOptions = DumpOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): Unit = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).dumpToFile(snapshotId, fileInSnapshot, target, options)

    /** Gibt ein Repository-Objekt roh in den Stream aus (`restic cat`). Für Typen ohne feste ID genügt `id = null`. */
    @JvmOverloads
    public fun cat(
        type: CatObjectType,
        id: String? = null,
        output: OutputStream,
        cancellationToken: CancellationToken? = null,
    ): Unit = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).cat(type, id, output)

    @JvmOverloads
    public fun catConfig(cancellationToken: CancellationToken? = null): RepositoryConfig =
        repositoryConfigFrom(catJson(CatObjectType.CONFIG, null, cancellationToken))

    /** Liefert geheimes Schlüsselmaterial; nicht protokollieren. */
    @JvmOverloads
    public fun catMasterKey(cancellationToken: CancellationToken? = null): MasterKey =
        masterKeyFrom(catJson(CatObjectType.MASTERKEY, null, cancellationToken))

    @JvmOverloads
    public fun catSnapshot(
        snapshotId: String,
        cancellationToken: CancellationToken? = null,
    ): Snapshot = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).catSnapshot(snapshotId)

    @JvmOverloads
    public fun catKey(
        keyId: String,
        cancellationToken: CancellationToken? = null,
    ): RepositoryKey = repositoryKeyFrom(catJson(CatObjectType.KEY, keyId, cancellationToken))

    @JvmOverloads
    public fun catLock(
        lockId: String,
        cancellationToken: CancellationToken? = null,
    ): RepositoryLock = repositoryLockFrom(catJson(CatObjectType.LOCK, lockId, cancellationToken))

    /** [snapshotAndSubfolder] hat die Form `snapshotId` oder `snapshotId:unterordner` und nennt den Tree dieses Verzeichnisses. */
    @JvmOverloads
    public fun catTree(
        snapshotAndSubfolder: String,
        cancellationToken: CancellationToken? = null,
    ): TreeObject = treeObjectFrom(catJson(CatObjectType.TREE, snapshotAndSubfolder, cancellationToken))

    @JvmOverloads
    public fun catIndex(
        indexId: String,
        cancellationToken: CancellationToken? = null,
    ): IndexObject = indexObjectFrom(catJson(CatObjectType.INDEX, indexId, cancellationToken))

    /**
     * Wie [cat], liefert aber den Inhalt als Text. Für JSON-Objekte stattdessen die getypten Varianten ([catConfig],
     * [catSnapshot], [catKey], [catLock], [catTree], [catIndex], [catMasterKey]) verwenden; dies hier ist für den Rest gedacht.
     */
    @JvmOverloads
    public fun catText(
        type: CatObjectType,
        id: String? = null,
        cancellationToken: CancellationToken? = null,
    ): String = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).catText(type, id)

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
    public fun check(
        options: CheckOptions = CheckOptions.DEFAULT,
        listener: OutputLineListener? = null,
    ): CheckResult = checkAndStatsCommand.check(options, listener)

    @JvmSynthetic
    public fun check(
        listener: OutputLineListener? = null,
        configure: CheckOptions.Builder.() -> Unit,
    ): CheckResult = check(CheckOptions.builder().apply(configure).build(), listener)

    @JvmOverloads
    public fun stats(
        options: StatsOptions = StatsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): StatsResult = CheckAndStatsCommand(runner.cancellableBy(cancellationToken)).stats(options)

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
    @JvmOverloads
    public fun tag(
        options: TagOptions,
        cancellationToken: CancellationToken? = null,
    ): TagResult = TagCommand(runner.cancellableBy(cancellationToken)).tag(options)

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
    @JvmOverloads
    public fun list(
        type: ListObjectType,
        cancellationToken: CancellationToken? = null,
    ): List<String> = ListCommand(runner.cancellableBy(cancellationToken)).list(type)

    @JvmOverloads
    public fun listBlobs(cancellationToken: CancellationToken? = null): List<BlobEntry> =
        ListCommand(runner.cancellableBy(cancellationToken)).listBlobs()

    @JvmOverloads
    public fun listKeys(cancellationToken: CancellationToken? = null): List<KeyInfo> = admin(cancellationToken).listKeys()

    /** Fügt dem Repository einen weiteren Schlüssel (Passwort) hinzu. */
    @JvmOverloads
    public fun addKey(
        options: NewKeyOptions = NewKeyOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): KeyAddResult = admin(cancellationToken).addKey(options)

    @JvmSynthetic
    public fun addKey(configure: NewKeyOptions.Builder.() -> Unit): KeyAddResult = addKey(NewKeyOptions.builder().apply(configure).build())

    /** Ersetzt den aktuell verwendeten Schlüssel durch einen mit neuem Passwort. */
    @JvmOverloads
    public fun changePassword(
        options: NewKeyOptions = NewKeyOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).changePassword(options)

    @JvmSynthetic
    public fun changePassword(configure: NewKeyOptions.Builder.() -> Unit): CommandOutput =
        changePassword(NewKeyOptions.builder().apply(configure).build())

    @JvmOverloads
    public fun removeKey(
        keyId: String,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).removeKey(keyId)

    /** Entfernt veraltete Sperren; mit `removeAll` auch aktive. */
    @JvmOverloads
    public fun unlock(
        removeAll: Boolean = false,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).unlock(removeAll)

    /** Ohne Namen werden die verfügbaren Migrationen aufgelistet. */
    @JvmOverloads
    public fun migrate(
        names: List<String> = emptyList(),
        force: Boolean = false,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).migrate(names, force)

    @JvmOverloads
    public fun cache(
        options: CacheOptions = CacheOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).cache(options)

    @JvmSynthetic
    public fun cache(configure: CacheOptions.Builder.() -> Unit): CommandOutput = cache(CacheOptions.builder().apply(configure).build())

    @JvmOverloads
    public fun features(cancellationToken: CancellationToken? = null): List<FeatureFlag> = admin(cancellationToken).features()

    /** Baut Snapshots aus verwaisten Bäumen wieder auf (`restic recover`). */
    @JvmOverloads
    public fun recover(cancellationToken: CancellationToken? = null): CommandOutput = admin(cancellationToken).recover()

    @JvmOverloads
    public fun repairIndex(
        readAllPacks: Boolean = false,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).repairIndex(readAllPacks)

    @JvmOverloads
    public fun repairPacks(
        packIds: List<String>,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).repairPacks(packIds)

    @JvmOverloads
    public fun repairSnapshots(
        options: RepairSnapshotsOptions = RepairSnapshotsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).repairSnapshots(options)

    @JvmSynthetic
    public fun repairSnapshots(configure: RepairSnapshotsOptions.Builder.() -> Unit): CommandOutput =
        repairSnapshots(RepairSnapshotsOptions.builder().apply(configure).build())

    /** Erzeugt Shell-Vervollständigungen und Man-Pages in den angegebenen Zielen. */
    @JvmOverloads
    public fun generate(
        options: GenerateOptions,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = tools(cancellationToken).generate(options)

    @JvmSynthetic
    public fun generate(configure: GenerateOptions.Builder.() -> Unit): CommandOutput =
        generate(GenerateOptions.builder().apply(configure).build())

    /** Das Vervollständigungsskript für eine Shell als Text. */
    @JvmOverloads
    public fun completion(
        shell: CompletionShell,
        cancellationToken: CancellationToken? = null,
    ): String = tools(cancellationToken).completion(shell)

    /** Aktualisiert die restic-Binary; mit `output` wird sie stattdessen in diese Datei geschrieben. */
    @JvmOverloads
    public fun selfUpdate(
        output: Path? = null,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = tools(cancellationToken).selfUpdate(output)

    /** Hilfetext eines Befehls, z.B. `help("key", "add")`; ohne Argumente die allgemeine Hilfe. */
    public fun help(vararg commandPath: String): String = help(commandPath.toList(), null)

    /** Wie [help] mit einem Befehlspfad als Liste, abbrechbar über [cancellationToken]. */
    public fun help(
        commandPath: List<String>,
        cancellationToken: CancellationToken?,
    ): String = tools(cancellationToken).help(commandPath)

    /** Die erweiterten Optionen (`--option key=value`) der Backends. */
    @JvmOverloads
    public fun extendedOptions(cancellationToken: CancellationToken? = null): List<ExtendedOption> =
        tools(cancellationToken).extendedOptions()

    /**
     * Stellt das Repository per FUSE als Verzeichnisbaum bereit (nicht unter Windows).
     * Der Aufruf kehrt sofort zurück; [MountHandle.awaitMounted] wartet auf die Bereitschaft, [MountHandle.close] hängt wieder aus.
     */
    @JvmOverloads
    public fun mount(
        mountpoint: Path,
        options: MountOptions = MountOptions.DEFAULT,
    ): MountHandle = mountCommand.mount(mountpoint, options)

    private fun admin(cancellationToken: CancellationToken?): AdminCommands = AdminCommands(runner.cancellableBy(cancellationToken))

    private fun tools(cancellationToken: CancellationToken?): ToolCommands = ToolCommands(runner.cancellableBy(cancellationToken))

    private fun catJson(
        type: CatObjectType,
        id: String?,
        cancellationToken: CancellationToken?,
    ): JsonNode = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).catJson(type, id)

    private fun lastJsonLine(
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
        cancellationToken: CancellationToken? = null,
    ): JsonNode {
        var lastLine = ""
        runner.cancellableBy(
            cancellationToken,
        ).runChecked(ProcessRequest(arguments, environment)) { line -> if (line.isNotBlank()) lastLine = line }
        return Json.parse(lastLine)
    }

    public companion object {
        public const val LATEST: String = "latest"
    }
}
