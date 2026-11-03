package io.github.ppuetsch.restic

import java.nio.file.Path

/**
 * Options for [Restic.init] (`restic init`). The `from*` values name an existing repository whose chunker
 * parameters should be copied.
 *
 * @property repositoryVersion Format of the new repository (`--repository-version`).
 * @property copyChunkerParams Copies the chunker parameters of the source repository (`--copy-chunker-params`).
 * @property fromRepository Source repository as a connection string (`--from-repo`).
 * @property fromRepositoryFile File holding the source's connection string (`--from-repository-file`).
 * @property fromPasswordFile Password file of the source (`--from-password-file`).
 * @property fromPasswordCommand Password command of the source (`--from-password-command`).
 * @property fromPassword Password of the source in plain text, passed as `RESTIC_FROM_PASSWORD`.
 * @property fromKeyHint Key ID tried first on the source (`--from-key-hint`).
 * @property fromInsecureNoPassword Source without a password (`--from-insecure-no-password`).
 */
public class InitOptions private constructor(
    builder: Builder,
) {
    public val repositoryVersion: String? = builder.repositoryVersion
    public val copyChunkerParams: Boolean = builder.copyChunkerParams
    public val fromRepository: String? = builder.fromRepository
    public val fromRepositoryFile: Path? = builder.fromRepositoryFile
    public val fromPasswordFile: Path? = builder.fromPasswordFile
    public val fromPasswordCommand: String? = builder.fromPasswordCommand
    public val fromPassword: String? = builder.fromPassword
    public val fromKeyHint: String? = builder.fromKeyHint
    public val fromInsecureNoPassword: Boolean = builder.fromInsecureNoPassword

    internal fun arguments(): List<String> =
        buildList {
            optionalValue("--repository-version", repositoryVersion)
            optionalFlag("--copy-chunker-params", copyChunkerParams)
            optionalValue("--from-repo", fromRepository)
            optionalValue("--from-repository-file", fromRepositoryFile)
            optionalValue("--from-password-file", fromPasswordFile)
            optionalValue("--from-password-command", fromPasswordCommand)
            optionalValue("--from-key-hint", fromKeyHint)
            optionalFlag("--from-insecure-no-password", fromInsecureNoPassword)
        }

    internal fun environment(): Map<String, String> = fromPassword?.let { mapOf("RESTIC_FROM_PASSWORD" to it) }.orEmpty()

    /** Builds [InitOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal var repositoryVersion: String? = null
        internal var copyChunkerParams: Boolean = false
        internal var fromRepository: String? = null
        internal var fromRepositoryFile: Path? = null
        internal var fromPasswordFile: Path? = null
        internal var fromPasswordCommand: String? = null
        internal var fromPassword: String? = null
        internal var fromKeyHint: String? = null
        internal var fromInsecureNoPassword: Boolean = false

        /** Format of the new repository (`--repository-version`): `1`, `2`, `latest` or `stable`. */
        public fun repositoryVersion(version: String): Builder = apply { repositoryVersion = version }

        /**
         * Copies the chunker parameters of the source repository (`--copy-chunker-params`), so that [ModifyCommands.copy]
         * deduplicates between both repositories.
         */
        @JvmOverloads
        public fun copyChunkerParams(enabled: Boolean = true): Builder = apply { copyChunkerParams = enabled }

        /** Source repository as a connection string (`--from-repo`). */
        public fun fromRepository(repository: String): Builder = apply { fromRepository = repository }

        /** File holding the connection string of the source repository (`--from-repository-file`). */
        public fun fromRepositoryFile(file: Path): Builder = apply { fromRepositoryFile = file }

        /** File holding the password of the source repository (`--from-password-file`). */
        public fun fromPasswordFile(file: Path): Builder = apply { fromPasswordFile = file }

        /** Command that prints the password of the source repository (`--from-password-command`). */
        public fun fromPasswordCommand(command: String): Builder = apply { fromPasswordCommand = command }

        /** Password of the source repository; passed through `RESTIC_FROM_PASSWORD`, never as an argument. */
        public fun fromPassword(password: String): Builder = apply { fromPassword = password }

        /** Key ID tried first on the source repository (`--from-key-hint`). */
        public fun fromKeyHint(keyId: String): Builder = apply { fromKeyHint = keyId }

        /** Source repository without a password (`--from-insecure-no-password`). */
        @JvmOverloads
        public fun fromInsecureNoPassword(enabled: Boolean = true): Builder = apply { fromInsecureNoPassword = enabled }

        /** Creates the immutable options. */
        public fun build(): InitOptions = InitOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: a new repository in the current default format. */
        @JvmField
        public val DEFAULT: InitOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
