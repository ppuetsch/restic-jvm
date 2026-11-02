package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.Duration

/** Name der restic-Binary, wenn [ResticConfiguration.Builder.binary] nicht gesetzt ist; gesucht wird sie im `PATH`. */
public const val DEFAULT_RESTIC_BINARY: String = "restic"

/** Kompressionsstufe für neu geschriebene Daten (`--compression`); wirkt nur bei Repositorys ab Format 2. */
public enum class Compression(
    internal val cliValue: String,
) {
    /** Schneller Kompromiss aus Geschwindigkeit und Größe; Standard von restic. */
    AUTO("auto"),

    /** Keine Kompression. */
    OFF("off"),

    /** Stärkste Kompression auf Kosten der Geschwindigkeit. */
    MAX("max"),
}

/**
 * Unveränderliche Verbindungs- und Prozesseinstellungen samt aller globalen restic-Flags; Grundlage einer [Restic]-Instanz.
 *
 * Ein Passwort im Klartext wird nie als Argument übergeben, sondern über die Umgebungsvariable `RESTIC_PASSWORD`,
 * damit es nicht in der Prozessliste auftaucht.
 *
 * ```java
 * ResticConfiguration configuration = ResticConfiguration.builder()
 *     .repository("sftp:backup@nas:/srv/restic")
 *     .passwordFile(Path.of("/etc/restic/password"))
 *     .compression(Compression.MAX)
 *     .build();
 * ```
 *
 * @property binary Pfad oder Name der restic-Binary.
 * @property repository Repository als Connection-String (`--repo`).
 * @property repositoryFile Datei, die den Connection-String enthält (`--repository-file`).
 * @property passwordFile Datei mit dem Repository-Passwort (`--password-file`).
 * @property passwordCommand Kommando, das das Passwort ausgibt (`--password-command`).
 * @property password Passwort im Klartext, übergeben als `RESTIC_PASSWORD`.
 * @property insecureNoPassword Repository ohne Passwort (`--insecure-no-password`).
 * @property keyHint ID des zuerst zu probierenden Schlüssels (`--key-hint`).
 * @property caCertificate Zusätzliches CA-Zertifikat für TLS (`--cacert`).
 * @property tlsClientCertificate Client-Zertifikat samt Schlüssel für TLS (`--tls-client-cert`).
 * @property insecureTls Schaltet die Prüfung von TLS-Zertifikaten ab (`--insecure-tls`).
 * @property cacheDirectory Eigenes Cache-Verzeichnis (`--cache-dir`).
 * @property noCache Arbeitet ohne lokalen Cache (`--no-cache`).
 * @property cleanupCache Entfernt alte Cache-Verzeichnisse (`--cleanup-cache`).
 * @property compression Kompressionsstufe (`--compression`).
 * @property packSizeMebibytes Zielgröße neuer Pack-Dateien in MiB (`--pack-size`).
 * @property noExtraVerify Spart die zusätzliche Prüfung vor dem Hochladen (`--no-extra-verify`).
 * @property noLock Arbeitet ohne Sperre, nur für lesende Befehle (`--no-lock`).
 * @property retryLock Wie lange auf eine fremde Sperre gewartet wird (`--retry-lock`).
 * @property stuckRequestTimeout Ab wann ein hängender Backend-Request neu gestartet wird (`--stuck-request-timeout`).
 * @property limitDownloadKibPerSecond Download-Limit in KiB/s (`--limit-download`).
 * @property limitUploadKibPerSecond Upload-Limit in KiB/s (`--limit-upload`).
 * @property httpUserAgent User-Agent für HTTP-Backends (`--http-user-agent`).
 * @property extendedOptions Backend-Optionen (`--option key=value`).
 * @property verbosity Anzahl `--verbose`.
 * @property environment Zusätzliche Umgebungsvariablen für den restic-Prozess, etwa Zugangsdaten eines Backends.
 * @property workingDirectory Arbeitsverzeichnis des restic-Prozesses; relative Pfade beziehen sich darauf.
 * @property timeout Maximale Laufzeit eines einzelnen Aufrufs.
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

    /** Stellt eine [ResticConfiguration] zusammen; jede Methode setzt einen Wert und gibt den Builder zurück. */
    @Suppress("TooManyFunctions") // eine Methode je restic-Flag; das Builder-API bildet die CLI vollständig ab
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

        /** Pfad oder Name der restic-Binary; Standard ist [DEFAULT_RESTIC_BINARY] aus dem `PATH`. */
        public fun binary(binary: String): Builder = apply { this.binary = binary }

        /** Connection-String wie bei `restic --repo`, z.B. `C:/backups`, `sftp:user@host:/path`, `rclone:remote:path`. */
        public fun repository(repository: String): Builder = apply { this.repository = repository }

        /** Datei, die den Connection-String enthält (`--repository-file`); Alternative zu [repository]. */
        public fun repositoryFile(file: Path): Builder = apply { this.repositoryFile = file }

        /** Datei, deren erste Zeile das Repository-Passwort ist (`--password-file`). */
        public fun passwordFile(file: Path): Builder = apply { this.passwordFile = file }

        /** Kommando, dessen Ausgabe das Passwort ist (`--password-command`), z.B. `pass show restic`. */
        public fun passwordCommand(command: String): Builder = apply { this.passwordCommand = command }

        /** Passwort im Klartext; wird über die Umgebungsvariable `RESTIC_PASSWORD` übergeben, nie als Argument. */
        public fun password(password: String): Builder = apply { this.password = password }

        /** Für Repositorys ohne Passwort (`--insecure-no-password`); nur für Tests gedacht. */
        @JvmOverloads
        public fun insecureNoPassword(enabled: Boolean = true): Builder = apply { this.insecureNoPassword = enabled }

        /** Schlüssel-ID, die restic zuerst probiert (`--key-hint`); beschleunigt Repositorys mit vielen Schlüsseln. */
        public fun keyHint(keyId: String): Builder = apply { this.keyHint = keyId }

        /** Zusätzliches CA-Zertifikat, etwa für einen REST-Server mit eigener CA (`--cacert`). */
        public fun caCertificate(file: Path): Builder = apply { this.caCertificate = file }

        /** PEM-Datei mit Client-Zertifikat und Schlüssel für TLS-Authentifizierung (`--tls-client-cert`). */
        public fun tlsClientCertificate(file: Path): Builder = apply { this.tlsClientCertificate = file }

        /** Schaltet die Prüfung von TLS-Zertifikaten ab (`--insecure-tls`); nur für Tests gedacht. */
        @JvmOverloads
        public fun insecureTls(enabled: Boolean = true): Builder = apply { this.insecureTls = enabled }

        /** Eigenes Cache-Verzeichnis statt des Standards im Benutzerprofil (`--cache-dir`). */
        public fun cacheDirectory(directory: Path): Builder = apply { this.cacheDirectory = directory }

        /** Arbeitet ohne lokalen Cache (`--no-cache`); spart Platz, macht viele Befehle aber deutlich langsamer. */
        @JvmOverloads
        public fun noCache(enabled: Boolean = true): Builder = apply { this.noCache = enabled }

        /** Entfernt alte Cache-Verzeichnisse anderer Repositorys (`--cleanup-cache`). */
        @JvmOverloads
        public fun cleanupCache(enabled: Boolean = true): Builder = apply { this.cleanupCache = enabled }

        /** Kompressionsstufe für neu geschriebene Daten (`--compression`). */
        public fun compression(compression: Compression): Builder = apply { this.compression = compression }

        /** Zielgröße neuer Pack-Dateien in MiB (`--pack-size`); größere Packs bedeuten weniger Dateien im Backend. */
        public fun packSizeMebibytes(mebibytes: Int): Builder = apply { this.packSizeMebibytes = mebibytes }

        /** Spart die zusätzliche Prüfung der Daten vor dem Hochladen (`--no-extra-verify`). */
        @JvmOverloads
        public fun noExtraVerify(enabled: Boolean = true): Builder = apply { this.noExtraVerify = enabled }

        /** Arbeitet ohne Repository-Sperre (`--no-lock`); nur für lesende Befehle, etwa auf schreibgeschützten Medien. */
        @JvmOverloads
        public fun noLock(enabled: Boolean = true): Builder = apply { this.noLock = enabled }

        /** Wie lange restic auf eine fremde Sperre wartet, statt sofort abzubrechen (`--retry-lock`). */
        public fun retryLock(duration: Duration): Builder = apply { this.retryLock = duration }

        /** Ab wann ein hängender Backend-Request abgebrochen und wiederholt wird (`--stuck-request-timeout`). */
        public fun stuckRequestTimeout(duration: Duration): Builder = apply { this.stuckRequestTimeout = duration }

        /** Download-Limit in KiB/s (`--limit-download`). */
        public fun limitDownloadKibPerSecond(rate: Long): Builder = apply { this.limitDownloadKibPerSecond = rate }

        /** Upload-Limit in KiB/s (`--limit-upload`). */
        public fun limitUploadKibPerSecond(rate: Long): Builder = apply { this.limitUploadKibPerSecond = rate }

        /** User-Agent für HTTP-basierte Backends (`--http-user-agent`). */
        public fun httpUserAgent(userAgent: String): Builder = apply { this.httpUserAgent = userAgent }

        /** Entspricht `--option key=value`, z.B. `sftp.command` oder `s3.storage-class`; siehe [Restic.extendedOptions]. */
        public fun extendedOption(
            key: String,
            value: String,
        ): Builder = apply { extendedOptions[key] = value }

        /** Anzahl `--verbose` (0 bis 2); beeinflusst vor allem die Textausgabe von Befehlen ohne JSON. */
        public fun verbosity(level: Int): Builder = apply { this.verbosity = level }

        /** Setzt eine Umgebungsvariable für den restic-Prozess, z.B. `AWS_ACCESS_KEY_ID` für ein S3-Backend. */
        public fun environment(
            name: String,
            value: String,
        ): Builder = apply { environment[name] = value }

        /** Arbeitsverzeichnis des restic-Prozesses; relative Pfade in Argumenten beziehen sich darauf. */
        public fun workingDirectory(directory: Path): Builder = apply { this.workingDirectory = directory }

        /** Maximale Laufzeit eines einzelnen restic-Aufrufs; danach wird er beendet und wirft [ResticTimeoutException]. */
        public fun timeout(timeout: Duration): Builder = apply { this.timeout = timeout }

        /** Erzeugt die unveränderliche Konfiguration. */
        public fun build(): ResticConfiguration = ResticConfiguration(this)
    }

    /** Einstieg in den [Builder]. */
    public companion object {
        private const val PASSWORD_VARIABLE: String = "RESTIC_PASSWORD"

        /** Ein neuer [Builder] ohne gesetzte Werte. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Go-Dauer für restic; Bruchteile von Sekunden in Millisekunden, damit z.B. 500 ms nicht zu `0s` (= aus) werden. */
private fun goDuration(duration: Duration): String = if (duration.toNanosPart() == 0) "${duration.seconds}s" else "${duration.toMillis()}ms"
