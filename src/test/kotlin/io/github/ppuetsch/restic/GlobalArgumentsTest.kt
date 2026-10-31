package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration

class GlobalArgumentsTest {
    @Test
    fun `empty configuration produces no arguments`() {
        assertEquals(emptyList<String>(), ResticConfiguration.builder().build().globalArguments())
    }

    @Test
    fun `all global flags are translated`() {
        val configuration =
            ResticConfiguration.builder()
                .repository("sftp:user@host:/backups")
                .passwordFile(Path.of("pw.txt"))
                .compression(Compression.MAX)
                .noCache()
                .noLock()
                .retryLock(Duration.ofMinutes(5))
                .limitUploadKibPerSecond(100)
                .limitDownloadKibPerSecond(200)
                .packSizeMebibytes(64)
                .extendedOption("sftp.command", "ssh x")
                .verbosity(2)
                .build()

        assertEquals(
            listOf(
                "--repo", "sftp:user@host:/backups",
                "--password-file", "pw.txt",
                "--no-cache",
                "--compression", "max",
                "--pack-size", "64",
                "--no-lock",
                "--retry-lock", "300s",
                "--limit-download", "200",
                "--limit-upload", "100",
                "--option", "sftp.command=ssh x",
                "--verbose", "--verbose",
            ),
            configuration.globalArguments(),
        )
    }

    @Test
    fun `password goes to environment and not to arguments`() {
        val configuration = ResticConfiguration.builder().password("geheim").build()
        assertEquals(emptyList<String>(), configuration.globalArguments())
        assertEquals("geheim", configuration.processEnvironment()["RESTIC_PASSWORD"])
    }
}
