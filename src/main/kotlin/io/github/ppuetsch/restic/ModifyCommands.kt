package io.github.ppuetsch.restic

/** Changing snapshots: tags, rewriting, copying from another repository. Obtained from [Restic.modify]. */
public class ModifyCommands internal constructor(
    private val runner: ProcessRunner,
) {
    private val rewriteAndCopyCommand = RewriteAndCopyCommand(runner)

    /** Changes tags of snapshots (`restic tag`); restic creates new snapshots for this and removes the old ones. */
    @JvmOverloads
    public fun tag(
        options: TagOptions,
        cancellationToken: CancellationToken? = null,
    ): TagResult = TagCommand(runner.cancellableBy(cancellationToken)).tag(options)

    /** Kotlin variant of [tag] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun tag(configure: TagOptions.Builder.() -> Unit): TagResult = tag(TagOptions.builder().apply(configure).build())

    /**
     * Rewrites snapshots (`restic rewrite`), for example to exclude files afterwards or to change host and time.
     *
     * @param listener Receives the text output while restic runs.
     */
    @JvmOverloads
    public fun rewrite(
        options: RewriteOptions,
        listener: OutputLineListener? = null,
    ): RewriteResult = rewriteAndCopyCommand.rewrite(options, listener)

    /** Kotlin variant of [rewrite] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun rewrite(configure: RewriteOptions.Builder.() -> Unit): RewriteResult =
        rewrite(RewriteOptions.builder().apply(configure).build())

    /**
     * Copies snapshots from the source repository named in the options into this repository (`restic copy`).
     *
     * @param listener Receives the text output while restic runs.
     */
    @JvmOverloads
    public fun copy(
        options: CopyOptions,
        listener: OutputLineListener? = null,
    ): CopyResult = rewriteAndCopyCommand.copy(options, listener)

    /** Kotlin variant of [copy] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun copy(configure: CopyOptions.Builder.() -> Unit): CopyResult = copy(CopyOptions.builder().apply(configure).build())
}
