package io.github.ppuetsch.restic

import java.nio.file.Path

/** Commands about the restic binary itself rather than the repository. Obtained from [Restic.tools]. */
public class ToolCommands internal constructor(
    private val runner: ProcessRunner,
) {
    /** The version of the restic binary in use (`restic version`); needs no repository. */
    @JvmOverloads
    public fun version(cancellationToken: CancellationToken? = null): ResticVersion =
        InitCommand(runner.cancellableBy(cancellationToken)).version()

    /** Generates shell completions and man pages in the given targets (`restic generate`). */
    @JvmOverloads
    public fun generate(
        options: GenerateOptions,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = tool(cancellationToken).generate(options)

    /** Kotlin variant of [generate] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun generate(configure: GenerateOptions.Builder.() -> Unit): CommandOutput =
        generate(GenerateOptions.builder().apply(configure).build())

    /** The completion script for a shell, as text. */
    @JvmOverloads
    public fun completion(
        shell: CompletionShell,
        cancellationToken: CancellationToken? = null,
    ): String = tool(cancellationToken).completion(shell)

    /**
     * Downloads the newest restic version (`restic self-update`).
     *
     * @param output Target file; if omitted, restic replaces the running binary.
     */
    @JvmOverloads
    public fun selfUpdate(
        output: Path? = null,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = tool(cancellationToken).selfUpdate(output)

    /** Help text of a command, for example `help("key", "add")`; without arguments the general help. */
    public fun help(vararg commandPath: String): String = help(commandPath.toList(), null)

    /** Like [help] with a command path as a list, cancellable through [cancellationToken]. */
    public fun help(
        commandPath: List<String>,
        cancellationToken: CancellationToken?,
    ): String = tool(cancellationToken).help(commandPath)

    /** The extended options (`--option key=value`) of the backends (`restic options`). */
    @JvmOverloads
    public fun extendedOptions(cancellationToken: CancellationToken? = null): List<ExtendedOption> =
        tool(cancellationToken).extendedOptions()

    private fun tool(cancellationToken: CancellationToken?): ToolCommand = ToolCommand(runner.cancellableBy(cancellationToken))
}
