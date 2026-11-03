package io.github.ppuetsch.restic

/** The keys (passwords) of the repository (`restic key`). Obtained from [Restic.keys]. */
public class KeyCommands internal constructor(
    private val runner: ProcessRunner,
) {
    /** All keys of the repository, with the one in use marked (`restic key list`). */
    @JvmOverloads
    public fun list(cancellationToken: CancellationToken? = null): List<KeyInfo> = key(cancellationToken).listKeys()

    /** Adds another key (password) to the repository (`restic key add`). */
    @JvmOverloads
    public fun add(
        options: NewKeyOptions = NewKeyOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): KeyAddResult = key(cancellationToken).addKey(options)

    /** Kotlin variant of [add] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun add(configure: NewKeyOptions.Builder.() -> Unit): KeyAddResult = add(NewKeyOptions.builder().apply(configure).build())

    /**
     * Replaces the key currently in use with one for a new password (`restic key passwd`).
     * Afterwards a new [ResticConfiguration] with the new password is needed.
     */
    @JvmOverloads
    public fun changePassword(
        options: NewKeyOptions = NewKeyOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = key(cancellationToken).changePassword(options)

    /** Kotlin variant of [changePassword] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun changePassword(configure: NewKeyOptions.Builder.() -> Unit): CommandOutput =
        changePassword(NewKeyOptions.builder().apply(configure).build())

    /** Removes a key (`restic key remove`); restic refuses to remove the one in use. */
    @JvmOverloads
    public fun remove(
        keyId: String,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = key(cancellationToken).removeKey(keyId)

    private fun key(cancellationToken: CancellationToken?): KeyCommand = KeyCommand(runner.cancellableBy(cancellationToken))
}
