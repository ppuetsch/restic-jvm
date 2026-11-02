package io.github.ppuetsch.restic

import java.nio.file.Path

/**
 * Optionen für [Restic.generate] (`restic generate`); jedes gesetzte Ziel wird erzeugt.
 *
 * @property bashCompletion Zieldatei der Bash-Vervollständigung (`--bash-completion`).
 * @property fishCompletion Zieldatei der Fish-Vervollständigung (`--fish-completion`).
 * @property zshCompletion Zieldatei der Zsh-Vervollständigung (`--zsh-completion`).
 * @property powershellCompletion Zieldatei der PowerShell-Vervollständigung (`--powershell-completion`).
 * @property manDirectory Zielverzeichnis der Man-Pages (`--man`).
 */
public class GenerateOptions private constructor(
    builder: Builder,
) {
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

    /** Stellt [GenerateOptions] zusammen; jede Methode setzt ein Ziel und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal var bashCompletion: Path? = null
        internal var fishCompletion: Path? = null
        internal var zshCompletion: Path? = null
        internal var powershellCompletion: Path? = null
        internal var manDirectory: Path? = null

        /** Schreibt die Bash-Vervollständigung in diese Datei (`--bash-completion`). */
        public fun bashCompletion(file: Path): Builder = apply { bashCompletion = file }

        /** Schreibt die Fish-Vervollständigung in diese Datei (`--fish-completion`). */
        public fun fishCompletion(file: Path): Builder = apply { fishCompletion = file }

        /** Schreibt die Zsh-Vervollständigung in diese Datei (`--zsh-completion`). */
        public fun zshCompletion(file: Path): Builder = apply { zshCompletion = file }

        /** Schreibt die PowerShell-Vervollständigung in diese Datei (`--powershell-completion`). */
        public fun powershellCompletion(file: Path): Builder = apply { powershellCompletion = file }

        /** Schreibt die Man-Pages in dieses Verzeichnis (`--man`). */
        public fun manDirectory(directory: Path): Builder = apply { manDirectory = directory }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): GenerateOptions = GenerateOptions(this)
    }

    /** Einstieg in den [Builder]. */
    public companion object {
        /** Ein neuer [Builder] ohne Ziele. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Shells, für die restic Auto-Vervollständigung erzeugen kann. */
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
 * Eine erweiterte Backend-Option, setzbar mit [ResticConfiguration.Builder.extendedOption].
 *
 * @property name Name wie `s3.storage-class`.
 * @property description Beschreibung von restic.
 */
public data class ExtendedOption(
    val name: String,
    val description: String,
)

private const val STANDARD_OUTPUT_TARGET: String = "-"
private const val EXTENDED_OPTIONS_HEADER: String = "All Extended Options:"

internal class ToolCommands(
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
