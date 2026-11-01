package io.github.ppuetsch.restic

import java.nio.file.Path

/** Optionen für `restic init`. */
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

        /** Ein Format (`1`, `2`), `latest` oder `stable`. */
        public fun repositoryVersion(version: String): Builder = apply { repositoryVersion = version }

        @JvmOverloads
        public fun copyChunkerParams(enabled: Boolean = true): Builder = apply { copyChunkerParams = enabled }

        public fun fromRepository(repository: String): Builder = apply { fromRepository = repository }

        public fun fromRepositoryFile(file: Path): Builder = apply { fromRepositoryFile = file }

        public fun fromPasswordFile(file: Path): Builder = apply { fromPasswordFile = file }

        public fun fromPasswordCommand(command: String): Builder = apply { fromPasswordCommand = command }

        /** Wird über `RESTIC_FROM_PASSWORD` übergeben. */
        public fun fromPassword(password: String): Builder = apply { fromPassword = password }

        public fun fromKeyHint(keyId: String): Builder = apply { fromKeyHint = keyId }

        @JvmOverloads
        public fun fromInsecureNoPassword(enabled: Boolean = true): Builder = apply { fromInsecureNoPassword = enabled }

        public fun build(): InitOptions = InitOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: InitOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
