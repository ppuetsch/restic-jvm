package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.Duration

public const val DEFAULT_RESTIC_BINARY: String = "restic"

public enum class Compression(internal val cliValue: String) {
    AUTO("auto"),
    OFF("off"),
    MAX("max"),
}

/**
 * Unveränderliche Verbindungs- und Prozesseinstellungen samt aller globalen restic-Flags.
 * Ein Passwort im Klartext wird nie als Argument übergeben, damit es nicht in der Prozessliste auftaucht.
 */
public class ResticConfiguration private constructor(builder: Builder) {
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

        public fun binary(binary: String): Builder = apply { this.binary = binary }

        /** Connection-String wie bei `restic --repo`, z.B. `C:/backups`, `sftp:user@host:/path`, `rclone:remote:path`. */
        public fun repository(repository: String): Builder = apply { this.repository = repository }

        public fun repositoryFile(file: Path): Builder = apply { this.repositoryFile = file }

        public fun passwordFile(file: Path): Builder = apply { this.passwordFile = file }

        public fun passwordCommand(command: String): Builder = apply { this.passwordCommand = command }

        /** Wird über die Umgebungsvariable `RESTIC_PASSWORD` übergeben. */
        public fun password(password: String): Builder = apply { this.password = password }

        @JvmOverloads
        public fun insecureNoPassword(enabled: Boolean = true): Builder = apply { this.insecureNoPassword = enabled }

        public fun keyHint(keyId: String): Builder = apply { this.keyHint = keyId }

        public fun caCertificate(file: Path): Builder = apply { this.caCertificate = file }

        public fun tlsClientCertificate(file: Path): Builder = apply { this.tlsClientCertificate = file }

        @JvmOverloads
        public fun insecureTls(enabled: Boolean = true): Builder = apply { this.insecureTls = enabled }

        public fun cacheDirectory(directory: Path): Builder = apply { this.cacheDirectory = directory }

        @JvmOverloads
        public fun noCache(enabled: Boolean = true): Builder = apply { this.noCache = enabled }

        @JvmOverloads
        public fun cleanupCache(enabled: Boolean = true): Builder = apply { this.cleanupCache = enabled }

        public fun compression(compression: Compression): Builder = apply { this.compression = compression }

        public fun packSizeMebibytes(mebibytes: Int): Builder = apply { this.packSizeMebibytes = mebibytes }

        @JvmOverloads
        public fun noExtraVerify(enabled: Boolean = true): Builder = apply { this.noExtraVerify = enabled }

        @JvmOverloads
        public fun noLock(enabled: Boolean = true): Builder = apply { this.noLock = enabled }

        public fun retryLock(duration: Duration): Builder = apply { this.retryLock = duration }

        public fun stuckRequestTimeout(duration: Duration): Builder = apply { this.stuckRequestTimeout = duration }

        public fun limitDownloadKibPerSecond(rate: Long): Builder = apply { this.limitDownloadKibPerSecond = rate }

        public fun limitUploadKibPerSecond(rate: Long): Builder = apply { this.limitUploadKibPerSecond = rate }

        public fun httpUserAgent(userAgent: String): Builder = apply { this.httpUserAgent = userAgent }

        /** Entspricht `--option key=value`, z.B. `sftp.command` oder `s3.storage-class`. */
        public fun extendedOption(
            key: String,
            value: String,
        ): Builder = apply { extendedOptions[key] = value }

        /** Anzahl `--verbose` (0 bis 2). */
        public fun verbosity(level: Int): Builder = apply { this.verbosity = level }

        public fun environment(
            name: String,
            value: String,
        ): Builder = apply { environment[name] = value }

        public fun workingDirectory(directory: Path): Builder = apply { this.workingDirectory = directory }

        /** Maximale Laufzeit eines einzelnen restic-Aufrufs. */
        public fun timeout(timeout: Duration): Builder = apply { this.timeout = timeout }

        public fun build(): ResticConfiguration = ResticConfiguration(this)
    }

    public companion object {
        private const val PASSWORD_VARIABLE: String = "RESTIC_PASSWORD"

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Go-Dauer für restic; Bruchteile von Sekunden in Millisekunden, damit z.B. 500 ms nicht zu `0s` (= aus) werden. */
private fun goDuration(duration: Duration): String = if (duration.toNanosPart() == 0) "${duration.seconds}s" else "${duration.toMillis()}ms"
