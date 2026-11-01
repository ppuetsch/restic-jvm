package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Die Fixtures sind unveränderte Ausgaben von restic 0.18.1 (`--json`). */
private const val WRONG_PASSWORD_STDERR =
    """{"message_type":"exit_error","code":12,"message":"Fatal: wrong password or no key found"}"""

private const val REPOSITORY_MISSING_STDERR =
    """{"message_type":"exit_error","code":10,"message":"Fatal: repository does not exist: unable to open config file: """ +
        """GetFileAttributesEx nope\\config: The system cannot find the path specified.\nIs there a repository at the """ +
        """following location?\n./nope"}"""

private const val UNREACHABLE_SFTP_STDERR =
    "ssh: Could not resolve hostname localhost.invalid: Name or service not known\n" +
        """{"message_type":"exit_error","code":1,"message":"Fatal: unable to open repository at """ +
        """sftp:nobody@localhost.invalid:/x: unable to start the sftp session, error: error receiving version packet """ +
        """from server: server unexpectedly closed connection: unexpected EOF"}""" + "\n"

private const val S3_WITHOUT_CREDENTIALS_STDERR =
    """{"message_type":"exit_error","code":1,"message":"Fatal: unable to open repository at s3:http://127.0.0.1:1/bucket: """ +
        """s3.getCredentials: no credentials found. Use `-o s3.unsafe-anonymous-auth=true` for anonymous authentication"}"""

class ErrorMessageTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `backup and restore errors have the same readable line as listener output`() {
        val backupError = BackupEvent.Error(item = "/home/me/locked.db", during = "archival", message = "permission denied")
        val restoreError = RestoreEvent.Error(item = "/home/me/locked.db", during = "restore", message = "permission denied")
        val listenerLine =
            ErrorOutput.readableLine(
                """{"message_type":"error","error":{"message":"permission denied"},"during":"archival","item":"/home/me/locked.db"}""",
            )

        assertEquals("/home/me/locked.db: permission denied", backupError.readable())
        assertEquals(backupError.readable(), restoreError.readable())
        assertEquals(listenerLine, backupError.readable())
    }

    @Test
    fun `errors without a path are just the message`() {
        assertEquals("scan failed", BackupEvent.Error(item = null, during = "scan", message = "scan failed").readable())
        assertEquals("scan failed", BackupEvent.Error(item = "", during = "scan", message = "scan failed").readable())
    }

    @Test
    fun `exit_error line is reduced to its message`() {
        val exception = commandExceptionFor(12, WRONG_PASSWORD_STDERR)

        assertEquals("Fatal: wrong password or no key found", exception.errorMessage)
        assertEquals("restic exited with code 12: Fatal: wrong password or no key found", exception.message)
    }

    @Test
    fun `multi line messages keep their line breaks`() {
        val exception = commandExceptionFor(10, REPOSITORY_MISSING_STDERR)

        assertTrue(exception.errorMessage.startsWith("Fatal: repository does not exist"), exception.errorMessage)
        assertEquals(listOf("Is there a repository at the following location?", "./nope"), exception.errorMessage.lines().takeLast(2))
    }

    @Test
    fun `plain text lines stay and blank lines are dropped next to json`() {
        val exception = commandExceptionFor(1, "\n$UNREACHABLE_SFTP_STDERR\n\n")

        val lines = exception.errorMessage.lines()
        assertEquals("ssh: Could not resolve hostname localhost.invalid: Name or service not known", lines.first())
        assertTrue(lines[1].startsWith("Fatal: unable to open repository at sftp:"), lines[1])
        assertEquals(2, lines.size)
    }

    @Test
    fun `error messages are reduced to item and message and broken json stays text`() {
        val stderr =
            listOf(
                """{"message_type":"error","error":{"message":"permission denied"},"during":"archival","item":"/x"}""",
                "{not json at all",
                "",
            ).joinToString("\n")

        assertEquals(listOf("/x: permission denied", "{not json at all"), commandExceptionFor(1, stderr).errorMessage.lines())
    }

    @Test
    fun `json messages of other types never come back as raw json`() {
        val stderr = listOf("""{"message_type":"verbose_status","message":"loading index"}""", """{"message_type":"status"}""")

        val message = commandExceptionFor(1, stderr.joinToString("\n")).errorMessage

        assertEquals(listOf("loading index", "restic message: status"), message.lines())
    }

    @Test
    fun `empty stderr gives an empty message`() {
        assertEquals("", commandExceptionFor(1, "").errorMessage)
    }

    @Test
    fun `exit codes decide the kind`() {
        assertEquals(ResticErrorKind.WRONG_PASSWORD, commandExceptionFor(12, WRONG_PASSWORD_STDERR).kind)
        assertEquals(ResticErrorKind.REPOSITORY_NOT_FOUND, commandExceptionFor(10, REPOSITORY_MISSING_STDERR).kind)
        assertEquals(ResticErrorKind.REPOSITORY_LOCKED, commandExceptionFor(11, "").kind)
        assertEquals(ResticErrorKind.SOURCE_UNREADABLE, commandExceptionFor(3, "").kind)
        assertEquals(ResticErrorKind.INTERRUPTED, commandExceptionFor(130, "").kind)
    }

    @Test
    fun `unreachable host is a network error`() {
        assertEquals(ResticErrorKind.NETWORK, commandExceptionFor(1, UNREACHABLE_SFTP_STDERR).kind)
    }

    @Test
    fun `rejected credentials are an authentication error`() {
        val stderr =
            """{"message_type":"exit_error","code":1,""" +
                """"message":"Fatal: ... The request signature we calculated does not match: SignatureDoesNotMatch"}"""

        assertEquals(ResticErrorKind.AUTHENTICATION, commandExceptionFor(1, stderr).kind)
    }

    @Test
    fun `other failures are classified as other`() {
        assertEquals(ResticErrorKind.OTHER, commandExceptionFor(1, S3_WITHOUT_CREDENTIALS_STDERR).kind)
    }

    @Test
    fun `real restic wrong password error carries a readable message`() {
        val repository = temporaryDirectory.resolve("repo")
        resticFor(repository).init()

        val failure = assertThrows<WrongPasswordException> { resticFor(repository) { password("anders") }.snapshots() }

        assertEquals("Fatal: wrong password or no key found", failure.errorMessage)
        assertEquals(ResticErrorKind.WRONG_PASSWORD, failure.kind)
    }

    @Test
    fun `real restic missing repository error carries a readable message`() {
        val failure = assertThrows<RepositoryNotFoundException> { resticFor(temporaryDirectory.resolve("fehlt")).snapshots() }

        assertTrue(failure.errorMessage.startsWith("Fatal: repository does not exist"), failure.errorMessage)
        assertEquals(ResticErrorKind.REPOSITORY_NOT_FOUND, failure.kind)
    }
}
