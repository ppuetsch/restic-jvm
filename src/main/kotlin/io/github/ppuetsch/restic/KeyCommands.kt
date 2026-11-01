package io.github.ppuetsch.restic

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val KEY_CREATED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val NEW_KEY_ID_PATTERN = Regex("""saved new key with ID (\w+)""")
private const val OWNER_ONLY_FILE_PERMISSIONS: String = "rw-------"
private const val PASSWORD_FILE_PREFIX: String = "restic-new-password"
private const val PASSWORD_FILE_SUFFIX: String = ".txt"

/** `restic key`: neue Passwörter gehen über eine nur für den Eigentümer lesbare Datei, nie über die Kommandozeile. */
internal class KeyCommands(
    private val runner: ProcessRunner,
) {
    fun listKeys(): List<KeyInfo> {
        val lines = mutableListOf<String>()
        runner.runChecked(listOf("key", "list", "--json")) { lines += it }
        return Json.arrayElements(lines).map { node ->
            KeyInfo(
                id = node.text("id"),
                userName = node.text("userName"),
                hostName = node.text("hostName"),
                created = node.textOrNull("created")?.let { LocalDateTime.parse(it, KEY_CREATED_FORMAT) },
                current = node.booleanOrNull("current") ?: false,
            )
        }
    }

    fun addKey(options: NewKeyOptions): KeyAddResult {
        val lines =
            withPasswordFile(options) { passwordFile ->
                runner.collectNonBlankLines(listOf("key", "add") + options.arguments(passwordFile))
            }
        return KeyAddResult(lines.firstNotNullOfOrNull { NEW_KEY_ID_PATTERN.find(it)?.groupValues?.get(1) }, lines)
    }

    fun changePassword(options: NewKeyOptions): CommandOutput =
        CommandOutput(
            withPasswordFile(options) { passwordFile ->
                runner.collectNonBlankLines(listOf("key", "passwd") + options.arguments(passwordFile))
            },
        )

    fun removeKey(keyId: String): CommandOutput = CommandOutput(runner.collectNonBlankLines(listOf("key", "remove", keyId)))

    private fun <T> withPasswordFile(
        options: NewKeyOptions,
        action: (Path?) -> T,
    ): T {
        val password = options.newPassword ?: return action(null)
        val file = createOwnerOnlyTemporaryFile(password)
        try {
            return action(file)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private fun createOwnerOnlyTemporaryFile(content: String): Path {
        val supportsPosixPermissions =
            Files.getFileStore(Path.of(System.getProperty("java.io.tmpdir"))).supportsFileAttributeView("posix")
        val file =
            if (supportsPosixPermissions) {
                val ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_ONLY_FILE_PERMISSIONS))
                Files.createTempFile(PASSWORD_FILE_PREFIX, PASSWORD_FILE_SUFFIX, ownerOnly)
            } else {
                Files.createTempFile(PASSWORD_FILE_PREFIX, PASSWORD_FILE_SUFFIX)
            }
        Files.writeString(file, content)
        return file
    }
}
