package io.github.ppuetsch.restic

import java.nio.file.Path

/**
 * Options for [ToolCommands.generate] (`restic generate`); every target that is set is generated.
 *
 * @property bashCompletion Target file of the Bash completion (`--bash-completion`).
 * @property fishCompletion Target file of the Fish completion (`--fish-completion`).
 * @property zshCompletion Target file of the Zsh completion (`--zsh-completion`).
 * @property powershellCompletion Target file of the PowerShell completion (`--powershell-completion`).
 * @property manDirectory Target directory of the man pages (`--man`).
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

    /** Builds [GenerateOptions]; every method sets one target and returns the builder. */
    public class Builder internal constructor() {
        internal var bashCompletion: Path? = null
        internal var fishCompletion: Path? = null
        internal var zshCompletion: Path? = null
        internal var powershellCompletion: Path? = null
        internal var manDirectory: Path? = null

        /** Writes the Bash completion to this file (`--bash-completion`). */
        public fun bashCompletion(file: Path): Builder = apply { bashCompletion = file }

        /** Writes the Fish completion to this file (`--fish-completion`). */
        public fun fishCompletion(file: Path): Builder = apply { fishCompletion = file }

        /** Writes the Zsh completion to this file (`--zsh-completion`). */
        public fun zshCompletion(file: Path): Builder = apply { zshCompletion = file }

        /** Writes the PowerShell completion to this file (`--powershell-completion`). */
        public fun powershellCompletion(file: Path): Builder = apply { powershellCompletion = file }

        /** Writes the man pages to this directory (`--man`). */
        public fun manDirectory(directory: Path): Builder = apply { manDirectory = directory }

        /** Creates the immutable options. */
        public fun build(): GenerateOptions = GenerateOptions(this)
    }

    /** Einstieg in den [Builder]. */
    public companion object {
        /** A new [Builder] without targets. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
