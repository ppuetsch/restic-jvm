package io.github.ppuetsch.restic

import java.nio.file.Path
import java.time.LocalDateTime

/**
 * Options for [KeyCommands.add] (`restic key add`) and [KeyCommands.changePassword] (`restic key passwd`).
 *
 * @property host Host name recorded in the new key (`--host`).
 * @property user User name recorded in the new key (`--user`).
 * @property newPasswordFile File holding the new password (`--new-password-file`).
 * @property newPassword New password in plain text; handed over through a short-lived temporary file.
 * @property newInsecureNoPassword New key without a password (`--new-insecure-no-password`).
 */
public class NewKeyOptions private constructor(
    builder: Builder,
) {
    public val host: String? = builder.host
    public val user: String? = builder.user
    public val newPasswordFile: Path? = builder.newPasswordFile
    public val newPassword: String? = builder.newPassword
    public val newInsecureNoPassword: Boolean = builder.newInsecureNoPassword

    internal fun arguments(passwordFile: Path?): List<String> =
        buildList {
            optionalValue("--host", host)
            optionalValue("--user", user)
            optionalValue("--new-password-file", passwordFile ?: newPasswordFile)
            optionalFlag("--new-insecure-no-password", newInsecureNoPassword)
        }

    /** Builds [NewKeyOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal var host: String? = null
        internal var user: String? = null
        internal var newPasswordFile: Path? = null
        internal var newPassword: String? = null
        internal var newInsecureNoPassword: Boolean = false

        /** Host name recorded in the new key (`--host`); defaults to the machine name. */
        public fun host(hostname: String): Builder = apply { host = hostname }

        /** User name recorded in the new key (`--user`); defaults to the current user. */
        public fun user(username: String): Builder = apply { user = username }

        /** File whose first line is the new password (`--new-password-file`). */
        public fun newPasswordFile(file: Path): Builder = apply { newPasswordFile = file }

        /** restic reads the new password only from a file or a terminal, so the library briefly writes it to a protected temporary file. */
        public fun newPassword(password: String): Builder = apply { newPassword = password }

        /** Creates the new key without a password (`--new-insecure-no-password`); meant for tests only. */
        @JvmOverloads
        public fun newInsecureNoPassword(enabled: Boolean = true): Builder = apply { newInsecureNoPassword = enabled }

        /** Creates the immutable options. */
        public fun build(): NewKeyOptions = NewKeyOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: restic prompts for the new password, which fails without a terminal. */
        @JvmField
        public val DEFAULT: NewKeyOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
