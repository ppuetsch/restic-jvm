package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Options for [ModifyCommands.copy] (`restic copy`); the source is given through the `from*` settings, the target is the
 * repository of the [ResticConfiguration].
 *
 * @property filter Selects snapshots by host, path and tags.
 * @property snapshotIds Snapshots to copy; all if none are given.
 * @property fromRepository Source repository as a connection string (`--from-repo`).
 * @property fromRepositoryFile File holding the source's connection string (`--from-repository-file`).
 * @property fromPasswordFile Password file of the source (`--from-password-file`).
 * @property fromPasswordCommand Password command of the source (`--from-password-command`).
 * @property fromPassword Password of the source in plain text, passed as `RESTIC_FROM_PASSWORD`.
 * @property fromKeyHint Key ID tried first on the source (`--from-key-hint`).
 * @property fromInsecureNoPassword Source without a password (`--from-insecure-no-password`).
 * @property cancellationToken Token that cancels the call.
 */
public class CopyOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val snapshotIds: List<String> = builder.snapshotIds.toList()
    public val fromRepository: String? = builder.fromRepository
    public val fromRepositoryFile: Path? = builder.fromRepositoryFile
    public val fromPasswordFile: Path? = builder.fromPasswordFile
    public val fromPasswordCommand: String? = builder.fromPasswordCommand
    public val fromPassword: String? = builder.fromPassword
    public val fromKeyHint: String? = builder.fromKeyHint
    public val fromInsecureNoPassword: Boolean = builder.fromInsecureNoPassword
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalValue("--from-repo", fromRepository)
            optionalValue("--from-repository-file", fromRepositoryFile)
            optionalValue("--from-password-file", fromPasswordFile)
            optionalValue("--from-password-command", fromPasswordCommand)
            optionalValue("--from-key-hint", fromKeyHint)
            optionalFlag("--from-insecure-no-password", fromInsecureNoPassword)
            addAll(snapshotIds)
        }

    internal fun environment(): Map<String, String> = fromPassword?.let { mapOf("RESTIC_FROM_PASSWORD" to it) }.orEmpty()

    /** Builds [CopyOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal val snapshotIds: MutableList<String> = mutableListOf()
        internal var fromRepository: String? = null
        internal var fromRepositoryFile: Path? = null
        internal var fromPasswordFile: Path? = null
        internal var fromPasswordCommand: String? = null
        internal var fromPassword: String? = null
        internal var fromKeyHint: String? = null
        internal var fromInsecureNoPassword: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        /** Copies only snapshots of this host (`--host`); may be repeated. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Copies only snapshots that backed up exactly this path (`--path`); may be repeated. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Copies only snapshots carrying all of these tags (`--tag`); several calls are combined with OR. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Copies only this snapshot (may be repeated); if none is given, all are copied. */
        public fun snapshotId(id: String): Builder = apply { snapshotIds.add(id) }

        /** Source repository as a connection string. */
        public fun fromRepository(repository: String): Builder = apply { fromRepository = repository }

        /** File holding the connection string of the source repository (`--from-repository-file`). */
        public fun fromRepositoryFile(file: Path): Builder = apply { fromRepositoryFile = file }

        /** File holding the password of the source repository (`--from-password-file`). */
        public fun fromPasswordFile(file: Path): Builder = apply { fromPasswordFile = file }

        /** Command that prints the password of the source repository (`--from-password-command`). */
        public fun fromPasswordCommand(command: String): Builder = apply { fromPasswordCommand = command }

        /** Passed through `RESTIC_FROM_PASSWORD`. */
        public fun fromPassword(password: String): Builder = apply { fromPassword = password }

        /** Key ID tried first on the source repository (`--from-key-hint`). */
        public fun fromKeyHint(keyId: String): Builder = apply { fromKeyHint = keyId }

        /** Source repository without a password (`--from-insecure-no-password`). */
        @JvmOverloads
        public fun fromInsecureNoPassword(enabled: Boolean = true): Builder = apply { fromInsecureNoPassword = enabled }

        /** Token that cancels the call from another thread. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): CopyOptions = CopyOptions(this)
    }

    /** Einstieg in den [Builder]. */
    public companion object {
        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
