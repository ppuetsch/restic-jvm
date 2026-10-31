package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.Duration

public const val DEFAULT_RESTIC_BINARY: String = "restic"

/**
 * Unveränderliche Verbindungs- und Prozesseinstellungen. Das Passwort wird nie als
 * Argument übergeben, damit es nicht in der Prozessliste auftaucht.
 */
public class ResticConfiguration private constructor(
    public val binary: String,
    public val repository: String?,
    public val passwordFile: Path?,
    public val password: String?,
    public val environment: Map<String, String>,
    public val workingDirectory: Path?,
    public val timeout: Duration?,
) {
    internal fun globalArguments(): List<String> =
        buildList {
            repository?.let { addAll(listOf("--repo", it)) }
            passwordFile?.let { addAll(listOf("--password-file", it.toString())) }
        }

    internal fun processEnvironment(): Map<String, String> =
        buildMap {
            putAll(environment)
            password?.let { put(PASSWORD_VARIABLE, it) }
        }

    public class Builder {
        private var binary: String = DEFAULT_RESTIC_BINARY
        private var repository: String? = null
        private var passwordFile: Path? = null
        private var password: String? = null
        private val environment: MutableMap<String, String> = linkedMapOf()
        private var workingDirectory: Path? = null
        private var timeout: Duration? = null

        public fun binary(binary: String): Builder = apply { this.binary = binary }

        /** Connection-String wie bei `restic --repo`, z.B. `C:/backups`, `sftp:user@host:/path`, `rclone:remote:path`. */
        public fun repository(repository: String): Builder = apply { this.repository = repository }

        public fun passwordFile(passwordFile: Path): Builder = apply { this.passwordFile = passwordFile }

        public fun password(password: String): Builder = apply { this.password = password }

        public fun environment(
            name: String,
            value: String,
        ): Builder = apply { environment[name] = value }

        public fun workingDirectory(workingDirectory: Path): Builder = apply { this.workingDirectory = workingDirectory }

        public fun timeout(timeout: Duration): Builder = apply { this.timeout = timeout }

        public fun build(): ResticConfiguration =
            ResticConfiguration(
                binary,
                repository,
                passwordFile,
                password,
                environment.toMap(),
                workingDirectory,
                timeout,
            )
    }

    public companion object {
        private const val PASSWORD_VARIABLE: String = "RESTIC_PASSWORD"

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
