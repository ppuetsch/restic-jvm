package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

/**
 * Einstiegspunkt der Library: eine Methode je restic-Befehl.
 *
 * Eine Instanz gehört zu genau einer [ResticConfiguration] (Repository, Zugangsdaten, globale Flags). Sie hält keinen
 * Zustand zwischen Aufrufen und darf daher von mehreren Threads gleichzeitig benutzt werden; jeder Aufruf startet einen
 * eigenen restic-Prozess und kehrt erst zurück, wenn dieser beendet ist (Ausnahme: [mount]).
 *
 * Fehler von restic kommen als Unterklassen von [ResticException], etwa [WrongPasswordException] oder
 * [RepositoryLockedException]. Lang laufende Aufrufe lassen sich über ein [CancellationToken] abbrechen.
 *
 * ```kotlin
 * val restic = Restic(ResticConfiguration.builder().repository("/srv/backup").password("geheim").build())
 * val result = restic.backup(listOf(Path.of("/home"))) { exclude("*.tmp") }
 * println(result.summary.snapshotId)
 * ```
 *
 * @property configuration Repository, Zugangsdaten und globale Flags für alle Aufrufe dieser Instanz.
 */
@Suppress("TooManyFunctions") // Fassade mit einer Methode je restic-Befehl; die Logik liegt in den *Command-Klassen
public class Restic(
    public val configuration: ResticConfiguration,
) {
    private val runner = ProcessRunner(configuration)
    private val backupCommand = BackupCommand(runner)
    private val restoreCommand = RestoreCommand(runner)
    private val forgetCommand = ForgetCommand(runner)
    private val pruneCommand = PruneCommand(runner)
    private val checkAndStatsCommand = CheckAndStatsCommand(runner)
    private val diffCommand = DiffCommand(runner)
    private val rewriteAndCopyCommand = RewriteAndCopyCommand(runner)
    private val mountCommand = MountCommand(runner)

    /** Die Version der verwendeten restic-Binary (`restic version`); braucht kein Repository. */
    @JvmOverloads
    public fun version(cancellationToken: CancellationToken? = null): ResticVersion {
        val node = lastJsonLine(listOf("version", "--json"), cancellationToken = cancellationToken)
        return ResticVersion(node.text("version"), node.text("go_version"), node.text("go_os"), node.text("go_arch"))
    }

    /**
     * Legt ein neues Repository an (`restic init`).
     *
     * @return ID und Ort des neuen Repositorys.
     */
    @JvmOverloads
    public fun init(
        options: InitOptions = InitOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): InitResult {
        val node = lastJsonLine(listOf("init", "--json") + options.arguments(), options.environment(), cancellationToken)
        return InitResult(node.text("id"), node.text("repository"))
    }

    /** Kotlin-Variante von [init], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun init(configure: InitOptions.Builder.() -> Unit): InitResult = init(InitOptions.builder().apply(configure).build())

    /**
     * Sichert Dateien und Verzeichnisse als neuen Snapshot (`restic backup`).
     *
     * Nicht lesbare Quelldateien führen nicht zu einer Exception: Der Snapshot entsteht trotzdem, und
     * [BackupResult.hasUnreadableSources] ist gesetzt.
     *
     * @param paths Zu sichernde Pfade; darf leer sein, wenn die Optionen `filesFrom*` setzen.
     * @param listener Empfängt Fortschritt, Fehler und die Zusammenfassung, während restic läuft.
     */
    @JvmOverloads
    public fun backup(
        paths: List<Path>,
        options: BackupOptions = BackupOptions.DEFAULT,
        listener: BackupListener? = null,
    ): BackupResult = backupCommand.backupPaths(paths.map(Path::toString), options, listener)

    /** Kotlin-Variante von [backup], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun backup(
        paths: List<Path>,
        listener: BackupListener? = null,
        configure: BackupOptions.Builder.() -> Unit,
    ): BackupResult = backup(paths, BackupOptions.builder().apply(configure).build(), listener)

    /**
     * Sichert den Inhalt des Streams als eine einzige Datei im Snapshot (`restic backup --stdin`).
     * Ihr Name kommt aus [BackupOptions.Builder.stdinFilename], sonst heißt sie `stdin`.
     */
    @JvmOverloads
    public fun backupStandardInput(
        input: InputStream,
        options: BackupOptions = BackupOptions.DEFAULT,
        listener: BackupListener? = null,
    ): BackupResult = backupCommand.backupStandardInput(input, options, listener)

    /**
     * Lässt restic das Kommando starten und sichert dessen Standardausgabe als eine Datei
     * (`restic backup --stdin-from-command`). Endet das Kommando mit einem Fehler, entsteht kein Snapshot.
     *
     * @param command Programm und Argumente, z.B. `listOf("pg_dump", "mydb")`.
     */
    @JvmOverloads
    public fun backupCommandOutput(
        command: List<String>,
        options: BackupOptions = BackupOptions.DEFAULT,
        listener: BackupListener? = null,
    ): BackupResult = backupCommand.backupCommandOutput(command, options, listener)

    /** Alle passenden Snapshots als flache Liste (`restic snapshots`), auch wenn `groupBy` gesetzt ist. */
    @JvmOverloads
    public fun snapshots(
        options: SnapshotsOptions = SnapshotsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): List<Snapshot> = SnapshotsCommand(runnerFor(cancellationToken, options.cancellationToken)).snapshots(options)

    /** Kotlin-Variante von [snapshots], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun snapshots(configure: SnapshotsOptions.Builder.() -> Unit): List<Snapshot> =
        snapshots(SnapshotsOptions.builder().apply(configure).build())

    /** Die passenden Snapshots in Gruppen; ohne [SnapshotsOptions.Builder.groupBy] gruppiert restic nach `host,paths`. */
    @JvmOverloads
    public fun snapshotGroups(
        options: SnapshotsOptions = SnapshotsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): List<SnapshotGroup> = SnapshotsCommand(runnerFor(cancellationToken, options.cancellationToken)).snapshotGroups(options)

    /**
     * Listet den Inhalt eines Snapshots (`restic ls`) und meldet jeden Eintrag einzeln an den Listener, ohne alle
     * Einträge im Speicher zu halten.
     *
     * @param snapshotId Snapshot-ID, Kurz-ID oder [LATEST].
     * @return Der aufgelistete Snapshot.
     */
    @JvmOverloads
    public fun ls(
        snapshotId: String = LATEST,
        options: LsOptions = LsOptions.DEFAULT,
        listener: LsListener? = null,
    ): Snapshot = ls(snapshotId, options, null, listener)

    /**
     * Wie [ls], abbrechbar über [cancellationToken]; ist es `null`, gilt das Token aus [options]. Der Listener steht
     * zuletzt, damit ein Lambda am Ende des Aufrufs weiterhin der Listener bleibt; deshalb sind hier alle Parameter Pflicht.
     */
    public fun ls(
        snapshotId: String,
        options: LsOptions,
        cancellationToken: CancellationToken?,
        listener: LsListener?,
    ): Snapshot = LsCommand(runnerFor(cancellationToken, options.cancellationToken)).ls(snapshotId, options, listener)

    /** Kotlin-Variante von [ls], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun ls(
        snapshotId: String = LATEST,
        listener: LsListener? = null,
        configure: LsOptions.Builder.() -> Unit,
    ): Snapshot = ls(snapshotId, LsOptions.builder().apply(configure).build(), listener)

    /** Wie [ls], sammelt aber alle Einträge in einer Liste; für sehr große Snapshots besser [ls] mit Listener nutzen. */
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

    /**
     * Sucht Dateien oder Verzeichnisse nach Namensmuster in den Snapshots (`restic find`).
     *
     * @param patterns Glob-Muster wie `*.pdf`; ein Eintrag passt, wenn eines der Muster passt.
     * @return Je Snapshot mit Treffern die gefundenen Einträge.
     */
    @JvmOverloads
    public fun find(
        patterns: List<String>,
        options: FindOptions = FindOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): List<FindSnapshotMatches> = FindCommand(runnerFor(cancellationToken, options.cancellationToken)).findNames(patterns, options)

    /** Kotlin-Variante von [find], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun find(
        patterns: List<String>,
        configure: FindOptions.Builder.() -> Unit,
    ): List<FindSnapshotMatches> = find(patterns, FindOptions.builder().apply(configure).build())

    /**
     * Sucht, in welchen Snapshots ein Blob, Tree oder Pack vorkommt (`restic find --blob`, `--tree` oder `--pack`).
     *
     * @param ids Vollständige IDs oder eindeutige Präfixe.
     */
    @JvmOverloads
    public fun findObjects(
        kind: FindObjectKind,
        ids: List<String>,
        options: FindOptions = FindOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): List<FindObject> = FindCommand(runnerFor(cancellationToken, options.cancellationToken)).findObjects(kind, ids, options)

    /**
     * Stellt einen Snapshot im Zielverzeichnis wieder her (`restic restore`).
     *
     * @param snapshotId Snapshot-ID, [LATEST] oder `id:unterordner`, um nur einen Teilbaum wiederherzustellen.
     * @param target Zielverzeichnis; restic legt es bei Bedarf an.
     * @param listener Empfängt Fortschritt, Fehler und die Zusammenfassung, während restic läuft.
     */
    @JvmOverloads
    public fun restore(
        snapshotId: String,
        target: Path,
        options: RestoreOptions = RestoreOptions.DEFAULT,
        listener: RestoreListener? = null,
    ): RestoreSummary = restoreCommand.restore(snapshotId, target, options, listener)

    /** Kotlin-Variante von [restore], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun restore(
        snapshotId: String,
        target: Path,
        listener: RestoreListener? = null,
        configure: RestoreOptions.Builder.() -> Unit,
    ): RestoreSummary = restore(snapshotId, target, RestoreOptions.builder().apply(configure).build(), listener)

    /**
     * Schreibt eine Datei aus dem Snapshot in den Stream, ein Verzeichnis als Archiv (`restic dump`).
     * Der Stream bleibt offen.
     *
     * @param fileInSnapshot Absoluter Pfad innerhalb des Snapshots, z.B. `/home/user/notes.txt`.
     */
    @JvmOverloads
    public fun dump(
        snapshotId: String,
        fileInSnapshot: String,
        output: OutputStream,
        options: DumpOptions = DumpOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): Unit = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).dump(snapshotId, fileInSnapshot, options, output)

    /** Wie [dump], schreibt aber direkt in eine Zieldatei (`restic dump --target`). */
    @JvmOverloads
    public fun dumpToFile(
        snapshotId: String,
        fileInSnapshot: String,
        target: Path,
        options: DumpOptions = DumpOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): Unit = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).dumpToFile(snapshotId, fileInSnapshot, target, options)

    /**
     * Gibt ein Repository-Objekt roh in den Stream aus (`restic cat`). Der Stream bleibt offen.
     *
     * @param id Pflicht für alle Typen außer [CatObjectType.CONFIG] und [CatObjectType.MASTERKEY].
     * @throws IllegalArgumentException wenn der Typ eine ID braucht und keine angegeben ist.
     */
    @JvmOverloads
    public fun cat(
        type: CatObjectType,
        id: String? = null,
        output: OutputStream,
        cancellationToken: CancellationToken? = null,
    ): Unit = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).cat(type, id, output)

    /** Die Konfiguration des Repositorys (`restic cat config`). */
    @JvmOverloads
    public fun catConfig(cancellationToken: CancellationToken? = null): RepositoryConfig =
        repositoryConfigFrom(catJson(CatObjectType.CONFIG, null, cancellationToken))

    /** Der entschlüsselte Hauptschlüssel des Repositorys (`restic cat masterkey`); geheim, nicht protokollieren. */
    @JvmOverloads
    public fun catMasterKey(cancellationToken: CancellationToken? = null): MasterKey =
        masterKeyFrom(catJson(CatObjectType.MASTERKEY, null, cancellationToken))

    /** Ein einzelner Snapshot mit allen Feldern (`restic cat snapshot`). */
    @JvmOverloads
    public fun catSnapshot(
        snapshotId: String,
        cancellationToken: CancellationToken? = null,
    ): Snapshot = DumpAndCatCommand(runner.cancellableBy(cancellationToken)).catSnapshot(snapshotId)

    /** Eine Schlüsseldatei des Repositorys (`restic cat key`); die IDs liefert [listKeys]. */
    @JvmOverloads
    public fun catKey(
        keyId: String,
        cancellationToken: CancellationToken? = null,
    ): RepositoryKey = repositoryKeyFrom(catJson(CatObjectType.KEY, keyId, cancellationToken))

    /** Eine Sperrdatei des Repositorys (`restic cat lock`); die IDs liefert [list] mit [ListObjectType.LOCKS]. */
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

    /** Eine Indexdatei des Repositorys (`restic cat index`); die IDs liefert [list] mit [ListObjectType.INDEX]. */
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

    /**
     * Entfernt Snapshots nach Aufbewahrungsregeln oder gezielt per ID (`restic forget`).
     * Speicher wird erst durch [prune] frei, oder im selben Aufruf mit [ForgetOptions.Builder.prune].
     *
     * @param outputListener Empfängt die Textausgabe eines anschließenden Prune-Laufs und die Fehlerausgabe.
     */
    @JvmOverloads
    public fun forget(
        options: ForgetOptions,
        outputListener: OutputLineListener? = null,
    ): ForgetResult = forgetCommand.forget(options, outputListener)

    /** Kotlin-Variante von [forget], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun forget(configure: ForgetOptions.Builder.() -> Unit): ForgetResult = forget(ForgetOptions.builder().apply(configure).build())

    /**
     * Gibt Speicher frei, den keine Snapshots mehr referenzieren (`restic prune`).
     *
     * @param listener Empfängt die Textausgabe, während restic läuft.
     */
    @JvmOverloads
    public fun prune(
        options: PruneOptions = PruneOptions.DEFAULT,
        listener: OutputLineListener? = null,
    ): PruneResult = pruneCommand.prune(options, listener)

    /** Kotlin-Variante von [prune], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun prune(configure: PruneOptions.Builder.() -> Unit): PruneResult = prune(PruneOptions.builder().apply(configure).build())

    /**
     * Prüft das Repository auf Fehler (`restic check`).
     *
     * Ein beschädigtes Repository führt nicht zu einer Exception, sondern zu [CheckResult.isHealthy] = `false`;
     * Exceptions gibt es nur, wenn die Prüfung selbst nicht laufen konnte.
     *
     * @param listener Empfängt die Meldungen in lesbarer Form, während restic läuft.
     */
    @JvmOverloads
    public fun check(
        options: CheckOptions = CheckOptions.DEFAULT,
        listener: OutputLineListener? = null,
    ): CheckResult = checkAndStatsCommand.check(options, listener)

    /** Kotlin-Variante von [check], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun check(
        listener: OutputLineListener? = null,
        configure: CheckOptions.Builder.() -> Unit,
    ): CheckResult = check(CheckOptions.builder().apply(configure).build(), listener)

    /** Größen und Anzahlen des Repositorys oder ausgewählter Snapshots (`restic stats`). */
    @JvmOverloads
    public fun stats(
        options: StatsOptions = StatsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): StatsResult = CheckAndStatsCommand(runnerFor(cancellationToken, options.cancellationToken)).stats(options)

    /** Kotlin-Variante von [stats], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun stats(configure: StatsOptions.Builder.() -> Unit): StatsResult = stats(StatsOptions.builder().apply(configure).build())

    /**
     * Vergleicht zwei Snapshots (`restic diff`) und meldet jede Änderung einzeln an den Listener.
     *
     * @return Zusammengefasste Zahlen zu hinzugefügten und entfernten Daten.
     */
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

    /** Ändert Tags von Snapshots (`restic tag`); restic legt dafür neue Snapshots an und entfernt die alten. */
    @JvmOverloads
    public fun tag(
        options: TagOptions,
        cancellationToken: CancellationToken? = null,
    ): TagResult = TagCommand(runner.cancellableBy(cancellationToken)).tag(options)

    /** Kotlin-Variante von [tag], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun tag(configure: TagOptions.Builder.() -> Unit): TagResult = tag(TagOptions.builder().apply(configure).build())

    /**
     * Schreibt Snapshots um (`restic rewrite`), etwa um nachträglich Dateien auszuschließen oder Host und Zeit zu ändern.
     *
     * @param listener Empfängt die Textausgabe, während restic läuft.
     */
    @JvmOverloads
    public fun rewrite(
        options: RewriteOptions,
        listener: OutputLineListener? = null,
    ): RewriteResult = rewriteAndCopyCommand.rewrite(options, listener)

    /** Kotlin-Variante von [rewrite], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun rewrite(configure: RewriteOptions.Builder.() -> Unit): RewriteResult =
        rewrite(RewriteOptions.builder().apply(configure).build())

    /**
     * Kopiert Snapshots aus dem in den Optionen genannten Quell-Repository in dieses Repository (`restic copy`).
     *
     * @param listener Empfängt die Textausgabe, während restic läuft.
     */
    @JvmOverloads
    public fun copy(
        options: CopyOptions,
        listener: OutputLineListener? = null,
    ): CopyResult = rewriteAndCopyCommand.copy(options, listener)

    /** Kotlin-Variante von [copy], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun copy(configure: CopyOptions.Builder.() -> Unit): CopyResult = copy(CopyOptions.builder().apply(configure).build())

    /** Zählt Objekt-IDs des Repositorys auf (`restic list`). */
    @JvmOverloads
    public fun list(
        type: ListObjectType,
        cancellationToken: CancellationToken? = null,
    ): List<String> = ListCommand(runner.cancellableBy(cancellationToken)).list(type)

    /** Alle Blobs des Repositorys mit ihrem Typ (`restic list blobs`). */
    @JvmOverloads
    public fun listBlobs(cancellationToken: CancellationToken? = null): List<BlobEntry> =
        ListCommand(runner.cancellableBy(cancellationToken)).listBlobs()

    /** Alle Schlüssel (Passwörter) des Repositorys (`restic key list`). */
    @JvmOverloads
    public fun listKeys(cancellationToken: CancellationToken? = null): List<KeyInfo> = keys(cancellationToken).listKeys()

    /** Fügt dem Repository einen weiteren Schlüssel (Passwort) hinzu (`restic key add`). */
    @JvmOverloads
    public fun addKey(
        options: NewKeyOptions = NewKeyOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): KeyAddResult = keys(cancellationToken).addKey(options)

    /** Kotlin-Variante von [addKey], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun addKey(configure: NewKeyOptions.Builder.() -> Unit): KeyAddResult = addKey(NewKeyOptions.builder().apply(configure).build())

    /**
     * Ersetzt den aktuell verwendeten Schlüssel durch einen mit neuem Passwort (`restic key passwd`).
     * Danach braucht es eine neue [ResticConfiguration] mit dem neuen Passwort.
     */
    @JvmOverloads
    public fun changePassword(
        options: NewKeyOptions = NewKeyOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = keys(cancellationToken).changePassword(options)

    /** Kotlin-Variante von [changePassword], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun changePassword(configure: NewKeyOptions.Builder.() -> Unit): CommandOutput =
        changePassword(NewKeyOptions.builder().apply(configure).build())

    /** Entfernt einen Schlüssel (`restic key remove`); den gerade verwendeten lehnt restic ab. */
    @JvmOverloads
    public fun removeKey(
        keyId: String,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = keys(cancellationToken).removeKey(keyId)

    /**
     * Entfernt veraltete Sperren (`restic unlock`).
     *
     * @param removeAll Entfernt auch Sperren laufender Prozesse; nur nutzen, wenn sicher keiner mehr läuft.
     */
    @JvmOverloads
    public fun unlock(
        removeAll: Boolean = false,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).unlock(removeAll)

    /**
     * Führt Migrationen des Repository-Formats aus (`restic migrate`).
     *
     * @param names Auszuführende Migrationen, z.B. `upgrade_repo_v2`; ohne Namen listet restic die verfügbaren auf.
     * @param force Führt die Migration auch aus, wenn restic davon abrät.
     */
    @JvmOverloads
    public fun migrate(
        names: List<String> = emptyList(),
        force: Boolean = false,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).migrate(names, force)

    /** Zeigt oder bereinigt die lokalen Cache-Verzeichnisse (`restic cache`). */
    @JvmOverloads
    public fun cache(
        options: CacheOptions = CacheOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).cache(options)

    /** Kotlin-Variante von [cache], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun cache(configure: CacheOptions.Builder.() -> Unit): CommandOutput = cache(CacheOptions.builder().apply(configure).build())

    /** Die Feature-Flags der restic-Binary mit Stabilität und Standardwert (`restic features`). */
    @JvmOverloads
    public fun features(cancellationToken: CancellationToken? = null): List<FeatureFlag> = admin(cancellationToken).features()

    /** Baut Snapshots aus verwaisten Bäumen wieder auf (`restic recover`). */
    @JvmOverloads
    public fun recover(cancellationToken: CancellationToken? = null): CommandOutput = admin(cancellationToken).recover()

    /**
     * Baut den Index neu auf (`restic repair index`).
     *
     * @param readAllPacks Liest alle Packs statt nur der unbekannten; langsam, aber gründlich.
     */
    @JvmOverloads
    public fun repairIndex(
        readAllPacks: Boolean = false,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).repairIndex(readAllPacks)

    /** Rettet die lesbaren Blobs beschädigter Packs (`restic repair packs`). */
    @JvmOverloads
    public fun repairPacks(
        packIds: List<String>,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).repairPacks(packIds)

    /** Repariert Snapshots, die auf fehlende Daten verweisen (`restic repair snapshots`). */
    @JvmOverloads
    public fun repairSnapshots(
        options: RepairSnapshotsOptions = RepairSnapshotsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).repairSnapshots(options)

    /** Kotlin-Variante von [repairSnapshots], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun repairSnapshots(configure: RepairSnapshotsOptions.Builder.() -> Unit): CommandOutput =
        repairSnapshots(RepairSnapshotsOptions.builder().apply(configure).build())

    /** Erzeugt Shell-Vervollständigungen und Man-Pages in den angegebenen Zielen (`restic generate`). */
    @JvmOverloads
    public fun generate(
        options: GenerateOptions,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = tools(cancellationToken).generate(options)

    /** Kotlin-Variante von [generate], die die Optionen per Builder-Lambda zusammenstellt. */
    @JvmSynthetic
    public fun generate(configure: GenerateOptions.Builder.() -> Unit): CommandOutput =
        generate(GenerateOptions.builder().apply(configure).build())

    /** Das Vervollständigungsskript für eine Shell als Text. */
    @JvmOverloads
    public fun completion(
        shell: CompletionShell,
        cancellationToken: CancellationToken? = null,
    ): String = tools(cancellationToken).completion(shell)

    /**
     * Lädt die neueste restic-Version herunter (`restic self-update`).
     *
     * @param output Zieldatei; ohne Angabe ersetzt restic die laufende Binary.
     */
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

    /** Die erweiterten Optionen (`--option key=value`) der Backends (`restic options`). */
    @JvmOverloads
    public fun extendedOptions(cancellationToken: CancellationToken? = null): List<ExtendedOption> =
        tools(cancellationToken).extendedOptions()

    /**
     * Stellt das Repository per FUSE als Verzeichnisbaum bereit (`restic mount`, nicht unter Windows).
     * Der Aufruf kehrt sofort zurück; [MountHandle.awaitMounted] wartet auf die Bereitschaft, [MountHandle.close] hängt wieder aus.
     */
    @JvmOverloads
    public fun mount(
        mountpoint: Path,
        options: MountOptions = MountOptions.DEFAULT,
    ): MountHandle = mountCommand.mount(mountpoint, options)

    /** Ein direkt übergebenes Token hat Vorrang vor dem aus den Optionen. */
    private fun runnerFor(
        explicitToken: CancellationToken?,
        optionsToken: CancellationToken?,
    ): ProcessRunner = runner.cancellableBy(explicitToken ?: optionsToken)

    private fun admin(cancellationToken: CancellationToken?): AdminCommands = AdminCommands(runner.cancellableBy(cancellationToken))

    private fun keys(cancellationToken: CancellationToken?): KeyCommands = KeyCommands(runner.cancellableBy(cancellationToken))

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
        runner
            .cancellableBy(
                cancellationToken,
            ).runChecked(ProcessRequest(arguments, environment)) { line -> if (line.isNotBlank()) lastLine = line }
        return Json.parse(lastLine)
    }

    /** Konstanten für Aufrufe von [Restic]. */
    public companion object {
        /** Steht anstelle einer Snapshot-ID für den neuesten passenden Snapshot. */
        public const val LATEST: String = "latest"
    }
}
