package io.github.ppuetsch.restic

import java.io.InputStream

/** Alles danach ist ein Pfad bzw. Kommando, auch wenn es mit `-` beginnt. */
private const val END_OF_OPTIONS: String = "--"

internal class BackupCommand(
    private val runner: ProcessRunner,
) {
    fun backupPaths(
        paths: List<String>,
        options: BackupOptions,
        listener: BackupListener?,
    ): BackupResult = execute(listOf("backup", "--json") + options.arguments() + END_OF_OPTIONS + paths, options, null, listener)

    fun backupStandardInput(
        input: InputStream,
        options: BackupOptions,
        listener: BackupListener?,
    ): BackupResult = execute(listOf("backup", "--json", "--stdin") + options.arguments(), options, input, listener)

    fun backupCommandOutput(
        command: List<String>,
        options: BackupOptions,
        listener: BackupListener?,
    ): BackupResult =
        execute(
            listOf("backup", "--json", "--stdin-from-command") + options.arguments() + END_OF_OPTIONS + command,
            options,
            null,
            listener,
        )

    private fun execute(
        arguments: List<String>,
        options: BackupOptions,
        input: InputStream?,
        listener: BackupListener?,
    ): BackupResult {
        val errors = mutableListOf<BackupEvent.Error>()
        var summary: BackupSummary? = null
        val handleLine = { line: String ->
            Json.parseOrNull(line)?.let(::backupEventFrom)?.let { event ->
                if (event is BackupEvent.Error) errors += event
                if (event is BackupEvent.Summary) summary = event.summary
                listener?.onEvent(event)
            }
        }
        val request = ProcessRequest(arguments, standardInput = input, cancellation = options.cancellationToken)
        val outcome = runner.run(request) { line -> handleLine(line) }
        return resultFrom(outcome, summary, errors)
    }

    private fun resultFrom(
        outcome: ProcessOutcome,
        summary: BackupSummary?,
        errors: List<BackupEvent.Error>,
    ): BackupResult {
        val unreadable = outcome.exitCode == ExitCodes.SOURCE_UNREADABLE
        if (outcome.exitCode != 0 && !unreadable) throw commandExceptionFor(outcome.exitCode, outcome.standardError)
        val finalSummary = summary ?: throw ResticOutputException("restic backup produced no summary", null)
        return BackupResult(finalSummary, errors, unreadable)
    }
}
