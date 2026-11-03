package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.Duration

/** Name of the restic binary when [ResticConfiguration.Builder.binary] is not set; it is looked up in the `PATH`. */
public const val DEFAULT_RESTIC_BINARY: String = "restic"

/** Compression level for newly written data (`--compression`); only has an effect on repositories of format 2 or later. */
public enum class Compression(
    internal val cliValue: String,
) {
    /** Fast compromise between speed and size; restic's default. */
    AUTO("auto"),

    /** No compression. */
    OFF("off"),

    /** Strongest compression at the cost of speed. */
    MAX("max"),
}

/**
 * Immutable connection and process settings including all of restic's global flags; the basis of a [Restic] instance.
 *
 * A plain-text password is never passed as an argument but through the environment variable `RESTIC_PASSWORD`,
 * so it does not show up in the process list.
 *
 * ```java
 * ResticConfiguration configuration = ResticConfiguration.builder()
 *     .repository("sftp:backup@nas:/srv/restic")
 *     .passwordFile(Path.of("/etc/restic/password"))
 *     .compression(Compression.MAX)
 *     .build();
 * ```
 *
 * @property binary Path or name of the restic binary.
 * @property repository Repository as a connection string (`--repo`).
 * @property repositoryFile File holding the connection string (`--repository-file`).
 * @property passwordFile File holding the repository password (`--password-file`).
 * @property passwordCommand Command that prints the password (`--password-command`).
 * @property password Password in plain text, passed as `RESTIC_PASSWORD`.
 * @property insecureNoPassword Repository without a password (`--insecure-no-password`).
 * @property keyHint ID of the key to try first (`--key-hint`).
 * @property caCertificate Additional CA certificate for TLS (`--cacert`).
 * @property tlsClientCertificate Client certificate with key for TLS (`--tls-client-cert`).
 * @property insecureTls Turns off verification of TLS certificates (`--insecure-tls`).
 * @property cacheDirectory Custom cache directory (`--cache-dir`).
 * @property noCache Works without a local cache (`--no-cache`).
 * @property cleanupCache Removes old cache directories (`--cleanup-cache`).
 * @property compression Compression level (`--compression`).
 * @property packSizeMebibytes Target size of new pack files in MiB (`--pack-size`).
 * @property noExtraVerify Skips the extra check before uploading (`--no-extra-verify`).
 * @property noLock Works without a lock, for read-only commands only (`--no-lock`).
 * @property retryLock How long to wait for another process's lock (`--retry-lock`).
 * @property stuckRequestTimeout When a stuck backend request is restarted (`--stuck-request-timeout`).
 * @property limitDownloadKibPerSecond Download limit in KiB/s (`--limit-download`).
 * @property limitUploadKibPerSecond Upload limit in KiB/s (`--limit-upload`).
 * @property httpUserAgent User agent for HTTP backends (`--http-user-agent`).
 * @property extendedOptions Backend options (`--option key=value`).
 * @property verbosity Number of `--verbose`.
 * @property environment Additional environment variables for the restic process, for example credentials of a backend.
 * @property workingDirectory Working directory of the restic process; relative paths are resolved against it.
 * @property timeout Maximum duration of a single call.
 */
public class ResticConfiguration private constructor(
    builder: Builder,
) {
    public val binary: String = builder.binary
    public val repository: String? = builder.repository
    public val repositoryFile: Path? = builder.repositoryFile
    public val passwordFile: Path? = builder.passwordFile
    public val passwordCommand: String? = builder.passwordCommand
    public val password: String? = builder.password
    public val insecureNoPassword: Boolean = builder.insecureNoPassword
    public val keyHint: String? = builder.keyHint
    public val caCertificate: Path? = builder.caCertificate
    public val tlsClientCertificate: Path? = builder.tlsClientCertificate
    public val insecureTls: Boolean = builder.insecureTls
    public val cacheDirectory: Path? = builder.cacheDirectory
    public val noCache: Boolean = builder.noCache
    public val cleanupCache: Boolean = builder.cleanupCache
    public val compression: Compression? = builder.compression
    public val packSizeMebibytes: Int? = builder.packSizeMebibytes
    public val noExtraVerify: Boolean = builder.noExtraVerify
    public val noLock: Boolean = builder.noLock
    public val retryLock: Duration? = builder.retryLock
    public val stuckRequestTimeout: Duration? = builder.stuckRequestTimeout
    public val limitDownloadKibPerSecond: Long? = builder.limitDownloadKibPerSecond
    public val limitUploadKibPerSecond: Long? = builder.limitUploadKibPerSecond
    public val httpUserAgent: String? = builder.httpUserAgent
    public val extendedOptions: Map<String, String> = builder.extendedOptions.toMap()
    public val verbosity: Int = builder.verbosity
    public val environment: Map<String, String> = builder.environment.toMap()
    public val workingDirectory: Path? = builder.workingDirectory
    public val timeout: Duration? = builder.timeout

    internal fun globalArguments(): List<String> =
        buildList {
            optionalValue("--repo", repository)
            optionalValue("--repository-file", repositoryFile)
            optionalValue("--password-file", passwordFile)
            optionalValue("--password-command", passwordCommand)
            optionalFlag("--insecure-no-password", insecureNoPassword)
            optionalValue("--key-hint", keyHint)
            optionalValue("--cacert", caCertificate)
            optionalValue("--tls-client-cert", tlsClientCertificate)
            optionalFlag("--insecure-tls", insecureTls)
            optionalValue("--cache-dir", cacheDirectory)
            optionalFlag("--no-cache", noCache)
            optionalFlag("--cleanup-cache", cleanupCache)
            optionalValue("--compression", compression?.cliValue)
            optionalValue("--pack-size", packSizeMebibytes)
            optionalFlag("--no-extra-verify", noExtraVerify)
            optionalFlag("--no-lock", noLock)
            optionalValue("--retry-lock", retryLock?.let(::goDuration))
            optionalValue("--stuck-request-timeout", stuckRequestTimeout?.let(::goDuration))
            optionalValue("--limit-download", limitDownloadKibPerSecond)
            optionalValue("--limit-upload", limitUploadKibPerSecond)
            optionalValue("--http-user-agent", httpUserAgent)
            extendedOptions.forEach { (key, value) -> optionalValue("--option", "$key=$value") }
            repeat(verbosity) { add("--verbose") }
        }

    internal fun processEnvironment(): Map<String, String> =
        buildMap {
            putAll(environment)
            password?.let { put(PASSWORD_VARIABLE, it) }
        }

    /** Builds a [ResticConfiguration]; every method sets one value and returns the builder. */
    public class Builder internal constructor() {
        internal var binary: String = DEFAULT_RESTIC_BINARY
        internal var repository: String? = null
        internal var repositoryFile: Path? = null
        internal var passwordFile: Path? = null
        internal var passwordCommand: String? = null
        internal var password: String? = null
        internal var insecureNoPassword: Boolean = false
        internal var keyHint: String? = null
        internal var caCertificate: Path? = null
        internal var tlsClientCertificate: Path? = null
        internal var insecureTls: Boolean = false
        internal var cacheDirectory: Path? = null
        internal var noCache: Boolean = false
        internal var cleanupCache: Boolean = false
        internal var compression: Compression? = null
        internal var packSizeMebibytes: Int? = null
        internal var noExtraVerify: Boolean = false
        internal var noLock: Boolean = false
        internal var retryLock: Duration? = null
        internal var stuckRequestTimeout: Duration? = null
        internal var limitDownloadKibPerSecond: Long? = null
        internal var limitUploadKibPerSecond: Long? = null
        internal var httpUserAgent: String? = null
        internal val extendedOptions: MutableMap<String, String> = linkedMapOf()
        internal var verbosity: Int = 0
        internal val environment: MutableMap<String, String> = linkedMapOf()
        internal var workingDirectory: Path? = null
        internal var timeout: Duration? = null

        /** Path or name of the restic binary; defaults to [DEFAULT_RESTIC_BINARY] from the `PATH`. */
        public fun binary(binary: String): Builder = apply { this.binary = binary }

        /** Connection string as for `restic --repo`, for example `C:/backups`, `sftp:user@host:/path`, `rclone:remote:path`. */
        public fun repository(repository: String): Builder = apply { this.repository = repository }

        /** File holding the connection string (`--repository-file`); alternative to [repository]. */
        public fun repositoryFile(file: Path): Builder = apply { this.repositoryFile = file }

        /** File whose first line is the repository password (`--password-file`). */
        public fun passwordFile(file: Path): Builder = apply { this.passwordFile = file }

        /** Command whose output is the password (`--password-command`), for example `pass show restic`. */
        public fun passwordCommand(command: String): Builder = apply { this.passwordCommand = command }

        /** Password in plain text; passed through the environment variable `RESTIC_PASSWORD`, never as an argument. */
        public fun password(password: String): Builder = apply { this.password = password }

        /** For repositories without a password (`--insecure-no-password`); meant for tests only. */
        @JvmOverloads
        public fun insecureNoPassword(enabled: Boolean = true): Builder = apply { this.insecureNoPassword = enabled }

        /** Key ID restic tries first (`--key-hint`); speeds up repositories with many keys. */
        public fun keyHint(keyId: String): Builder = apply { this.keyHint = keyId }

        /** Additional CA certificate, for example for a REST server with its own CA (`--cacert`). */
        public fun caCertificate(file: Path): Builder = apply { this.caCertificate = file }

        /** PEM file with client certificate and key for TLS authentication (`--tls-client-cert`). */
        public fun tlsClientCertificate(file: Path): Builder = apply { this.tlsClientCertificate = file }

        /** Turns off verification of TLS certificates (`--insecure-tls`); meant for tests only. */
        @JvmOverloads
        public fun insecureTls(enabled: Boolean = true): Builder = apply { this.insecureTls = enabled }

        /** Custom cache directory instead of the default in the user profile (`--cache-dir`). */
        public fun cacheDirectory(directory: Path): Builder = apply { this.cacheDirectory = directory }

        /** Works without a local cache (`--no-cache`); saves space, but makes many commands much slower. */
        @JvmOverloads
        public fun noCache(enabled: Boolean = true): Builder = apply { this.noCache = enabled }

        /** Removes old cache directories of other repositories (`--cleanup-cache`). */
        @JvmOverloads
        public fun cleanupCache(enabled: Boolean = true): Builder = apply { this.cleanupCache = enabled }

        /** Compression level for newly written data (`--compression`). */
        public fun compression(compression: Compression): Builder = apply { this.compression = compression }

        /** Target size of new pack files in MiB (`--pack-size`); larger packs mean fewer files in the backend. */
        public fun packSizeMebibytes(mebibytes: Int): Builder = apply { this.packSizeMebibytes = mebibytes }

        /** Skips the extra check of the data before uploading (`--no-extra-verify`). */
        @JvmOverloads
        public fun noExtraVerify(enabled: Boolean = true): Builder = apply { this.noExtraVerify = enabled }

        /** Works without a repository lock (`--no-lock`); only for read-only commands, for example on read-only media. */
        @JvmOverloads
        public fun noLock(enabled: Boolean = true): Builder = apply { this.noLock = enabled }

        /** How long restic waits for another process's lock instead of giving up right away (`--retry-lock`). */
        public fun retryLock(duration: Duration): Builder = apply { this.retryLock = duration }

        /** When a stuck backend request is aborted and retried (`--stuck-request-timeout`). */
        public fun stuckRequestTimeout(duration: Duration): Builder = apply { this.stuckRequestTimeout = duration }

        /** Download-Limit in KiB/s (`--limit-download`). */
        public fun limitDownloadKibPerSecond(rate: Long): Builder = apply { this.limitDownloadKibPerSecond = rate }

        /** Upload-Limit in KiB/s (`--limit-upload`). */
        public fun limitUploadKibPerSecond(rate: Long): Builder = apply { this.limitUploadKibPerSecond = rate }

        /** User agent for HTTP-based backends (`--http-user-agent`). */
        public fun httpUserAgent(userAgent: String): Builder = apply { this.httpUserAgent = userAgent }

        /** Corresponds to `--option key=value`, for example `sftp.command` or `s3.storage-class`; see [ToolCommands.extendedOptions]. */
        public fun extendedOption(
            key: String,
            value: String,
        ): Builder = apply { extendedOptions[key] = value }

        /** Number of `--verbose` (0 to 2); mainly affects the text output of commands without JSON. */
        public fun verbosity(level: Int): Builder = apply { this.verbosity = level }

        /** Sets an environment variable for the restic process, for example `AWS_ACCESS_KEY_ID` for an S3 backend. */
        public fun environment(
            name: String,
            value: String,
        ): Builder = apply { environment[name] = value }

        /** Working directory of the restic process; relative paths in arguments are resolved against it. */
        public fun workingDirectory(directory: Path): Builder = apply { this.workingDirectory = directory }

        /** Maximum duration of a single restic call; after that it is ended and throws [ResticTimeoutException]. */
        public fun timeout(timeout: Duration): Builder = apply { this.timeout = timeout }

        /** Creates the immutable configuration. */
        public fun build(): ResticConfiguration = ResticConfiguration(this)
    }

    /** Einstieg in den [Builder]. */
    public companion object {
        private const val PASSWORD_VARIABLE: String = "RESTIC_PASSWORD"

        /** A new [Builder] with no values set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Go duration for restic; fractions of a second in milliseconds, so that 500 ms, for example, do not become `0s` (= off). */
private fun goDuration(duration: Duration): String = if (duration.toNanosPart() == 0) "${duration.seconds}s" else "${duration.toMillis()}ms"
