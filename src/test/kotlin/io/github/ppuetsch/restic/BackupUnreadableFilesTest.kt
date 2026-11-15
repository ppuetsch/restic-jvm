package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

private const val SUMMARY_LINE: String =
    """out {"message_type":"summary","files_new":1,"files_changed":0,"files_unmodified":0,""" +
        """"dirs_new":1,"dirs_changed":0,"dirs_unmodified":0,""" +
        """"data_blobs":1,"tree_blobs":1,"data_added":1,"data_added_packed":1,""" +
        """"total_files_processed":1,"total_bytes_processed":1,""" +
        """"total_duration":0.1,"snapshot_id":"abc"}"""
private const val ERROR_LINE: String =
    """err {"message_type":"error","error":{"message":"unsupported file type \"irregular\""},""" +
        """"during":"archival","item":"/data/link"}"""
private const val EXIT_LINE: String =
    """err {"message_type":"exit_error","code":3,"message":"Warning: at least one source file could not be read"}"""

class BackupUnreadableFilesTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private fun backupWithUnreadableFile(listener: BackupListener?): BackupResult =
        FakeRestic(temporaryDirectory, SUMMARY_LINE, ERROR_LINE, EXIT_LINE, "exit 3")
            .restic()
            .backup(listOf(temporaryDirectory), BackupOptions.DEFAULT, listener)

    @Test
    fun `files restic reports on standard error end up in the result`() {
        val result = backupWithUnreadableFile(null)

        assertTrue(result.hasUnreadableSources)
        assertEquals(listOf("/data/link"), result.errors.map(BackupEvent.Error::item))
        assertEquals("unsupported file type \"irregular\"", result.errors.single().message)
    }

    @Test
    fun `the listener receives the unreadable file as an error event`() {
        val received = mutableListOf<BackupEvent>()

        backupWithUnreadableFile { event -> received += event }

        assertEquals(1, received.filterIsInstance<BackupEvent.Error>().size)
    }
}
