package io.github.ppuetsch.restic

import java.nio.file.Path

/** Shells restic can generate completion for. */
public enum class CompletionShell(
    internal val flag: String,
) {
    /** Bash. */
    BASH("--bash-completion"),

    /** Fish. */
    FISH("--fish-completion"),

    /** Zsh. */
    ZSH("--zsh-completion"),

    /** PowerShell. */
    POWERSHELL("--powershell-completion"),
}

/**
 * An extended backend option, set with [ResticConfiguration.Builder.extendedOption].
 *
 * @property name Name such as `s3.storage-class`.
 * @property description Description from restic.
 */
public data class ExtendedOption(
    val name: String,
    val description: String,
)

private const val STANDARD_OUTPUT_TARGET: String = "-"
private const val EXTENDED_OPTIONS_HEADER: String = "All Extended Options:"

internal class ToolCommand(
    private val runner: ProcessRunner,
) {
    fun generate(options: GenerateOptions): CommandOutput = CommandOutput(collect(listOf("generate") + options.arguments()))

    fun completion(shell: CompletionShell): String =
        collect(
            listOf("generate", shell.flag, STANDARD_OUTPUT_TARGET),
            keepBlankLines = true,
        ).joinToString("\n")

    fun selfUpdate(output: Path?): CommandOutput =
        CommandOutput(collect(listOf("self-update") + output?.let { listOf("--output", it.toString()) }.orEmpty()))

    fun help(commandPath: List<String>): String = collect(listOf("help") + commandPath, keepBlankLines = true).joinToString("\n")

    fun extendedOptions(): List<ExtendedOption> =
        collect(listOf("options"))
            .dropWhile { !it.contains(EXTENDED_OPTIONS_HEADER) }
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> ExtendedOption(line.substringBefore(' '), line.substringAfter(' ', "").trim()) }

    private fun collect(
        arguments: List<String>,
        keepBlankLines: Boolean = false,
    ): List<String> {
        val lines = mutableListOf<String>()
        runner.runChecked(arguments) { if (keepBlankLines || it.isNotBlank()) lines += it }
        return lines
    }
}
