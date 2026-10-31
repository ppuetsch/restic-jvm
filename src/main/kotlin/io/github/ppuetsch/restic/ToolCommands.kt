package io.github.ppuetsch.restic

import java.nio.file.Path

/** Optionen für `restic generate`; jedes gesetzte Ziel wird erzeugt. */
public class GenerateOptions private constructor(builder: Builder) {
    public val bashCompletion: Path? = builder.bashCompletion
    public val fishCompletion: Path? = builder.fishCompletion
    public val zshCompletion: Path? = builder.zshCompletion
    public val powershellCompletion: Path? = builder.powershellCompletion
    public val manDirectory: Path? = builder.manDirectory

    internal fun arguments(): List<String> =
        buildList {
            optionalValue("--bash-completion", bashCompletion)
            optionalValue("--fish-completion", fishCompletion)
            optionalValue("--zsh-completion", zshCompletion)
            optionalValue("--powershell-completion", powershellCompletion)
            optionalValue("--man", manDirectory)
        }

    public class Builder internal constructor() {
        internal var bashCompletion: Path? = null
        internal var fishCompletion: Path? = null
        internal var zshCompletion: Path? = null
        internal var powershellCompletion: Path? = null
        internal var manDirectory: Path? = null

        public fun bashCompletion(file: Path): Builder = apply { bashCompletion = file }

        public fun fishCompletion(file: Path): Builder = apply { fishCompletion = file }

        public fun zshCompletion(file: Path): Builder = apply { zshCompletion = file }

        public fun powershellCompletion(file: Path): Builder = apply { powershellCompletion = file }

        public fun manDirectory(directory: Path): Builder = apply { manDirectory = directory }

        public fun build(): GenerateOptions = GenerateOptions(this)
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Shells, für die restic Auto-Vervollständigung erzeugen kann. */
public enum class CompletionShell(internal val flag: String) {
    BASH("--bash-completion"),
    FISH("--fish-completion"),
    ZSH("--zsh-completion"),
    POWERSHELL("--powershell-completion"),
}

public data class ExtendedOption(
    val name: String,
    val description: String,
)

private const val STANDARD_OUTPUT_TARGET: String = "-"
private const val EXTENDED_OPTIONS_HEADER: String = "All Extended Options:"

internal class ToolCommands(private val runner: ProcessRunner) {
    fun generate(options: GenerateOptions): CommandOutput = CommandOutput(collect(listOf("generate") + options.arguments()))

    fun completion(shell: CompletionShell): String =
        collect(
            listOf("generate", shell.flag, STANDARD_OUTPUT_TARGET),
            keepBlankLines = true,
        ).joinToString("\n")

    fun selfUpdate(output: Path?): CommandOutput =
        CommandOutput(collect(listOf("self-update") + (output?.let { listOf("--output", it.toString()) } ?: emptyList())))

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
