package io.github.ppuetsch.restic

import java.nio.file.Path

/**
 * Optionen für [Restic.init] (`restic init`). Die `from*`-Werte nennen ein bestehendes Repository, dessen
 * Chunker-Parameter übernommen werden sollen.
 *
 * @property repositoryVersion Format des neuen Repositorys (`--repository-version`).
 * @property copyChunkerParams Übernimmt die Chunker-Parameter des Quell-Repositorys (`--copy-chunker-params`).
 * @property fromRepository Quell-Repository als Connection-String (`--from-repo`).
 * @property fromRepositoryFile Datei mit dem Connection-String der Quelle (`--from-repository-file`).
 * @property fromPasswordFile Passwortdatei der Quelle (`--from-password-file`).
 * @property fromPasswordCommand Passwort-Kommando der Quelle (`--from-password-command`).
 * @property fromPassword Passwort der Quelle im Klartext, übergeben als `RESTIC_FROM_PASSWORD`.
 * @property fromKeyHint Schlüssel-ID, die bei der Quelle zuerst probiert wird (`--from-key-hint`).
 * @property fromInsecureNoPassword Quelle ohne Passwort (`--from-insecure-no-password`).
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

    /** Stellt [InitOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
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

        /** Format des neuen Repositorys (`--repository-version`): `1`, `2`, `latest` oder `stable`. */
        public fun repositoryVersion(version: String): Builder = apply { repositoryVersion = version }

        /**
         * Übernimmt die Chunker-Parameter des Quell-Repositorys (`--copy-chunker-params`), damit [Restic.copy] zwischen
         * beiden Repositorys dedupliziert.
         */
        @JvmOverloads
        public fun copyChunkerParams(enabled: Boolean = true): Builder = apply { copyChunkerParams = enabled }

        /** Quell-Repository als Connection-String (`--from-repo`). */
        public fun fromRepository(repository: String): Builder = apply { fromRepository = repository }

        /** Datei mit dem Connection-String des Quell-Repositorys (`--from-repository-file`). */
        public fun fromRepositoryFile(file: Path): Builder = apply { fromRepositoryFile = file }

        /** Datei mit dem Passwort des Quell-Repositorys (`--from-password-file`). */
        public fun fromPasswordFile(file: Path): Builder = apply { fromPasswordFile = file }

        /** Kommando, das das Passwort des Quell-Repositorys ausgibt (`--from-password-command`). */
        public fun fromPasswordCommand(command: String): Builder = apply { fromPasswordCommand = command }

        /** Passwort des Quell-Repositorys; wird über `RESTIC_FROM_PASSWORD` übergeben, nie als Argument. */
        public fun fromPassword(password: String): Builder = apply { fromPassword = password }

        /** Schlüssel-ID, die beim Quell-Repository zuerst probiert wird (`--from-key-hint`). */
        public fun fromKeyHint(keyId: String): Builder = apply { fromKeyHint = keyId }

        /** Quell-Repository ohne Passwort (`--from-insecure-no-password`). */
        @JvmOverloads
        public fun fromInsecureNoPassword(enabled: Boolean = true): Builder = apply { fromInsecureNoPassword = enabled }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): InitOptions = InitOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: neues Repository im aktuellen Standardformat. */
        @JvmField
        public val DEFAULT: InitOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
