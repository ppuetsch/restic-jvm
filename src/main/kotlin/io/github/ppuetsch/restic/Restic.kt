package io.github.ppuetsch.restic

import com.fasterxml.jackson.databind.JsonNode
import java.io.InputStream
import java.nio.file.Path

/** Einstiegspunkt: eine Instanz pro Repository-Konfiguration; thread-safe, da zustandslos. */
public class Restic(public val configuration: ResticConfiguration) {
    private val runner = ProcessRunner(configuration)
    private val backupCommand = BackupCommand(runner)

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

    private fun lastJsonLine(
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
    ): JsonNode {
        var lastLine = ""
        runner.runChecked(ProcessRequest(arguments, environment)) { line -> if (line.isNotBlank()) lastLine = line }
        return Json.parse(lastLine)
    }
}
