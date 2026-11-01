package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
            ResticConfiguration
                .builder()
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
                "--repo",
                "sftp:user@host:/backups",
                "--password-file",
                "pw.txt",
                "--no-cache",
                "--compression",
                "max",
                "--pack-size",
                "64",
                "--no-lock",
                "--retry-lock",
                "300s",
                "--limit-download",
                "200",
                "--limit-upload",
                "100",
                "--option",
                "sftp.command=ssh x",
                "--verbose",
                "--verbose",
            ),
            configuration.globalArguments(),
        )
    }

    @Test
    fun `connection and cache flags are translated`() {
        val configuration =
            ResticConfiguration
                .builder()
                .repositoryFile(Path.of("repo.txt"))
                .passwordCommand("pass show restic")
                .insecureNoPassword()
                .keyHint("abc123")
                .caCertificate(Path.of("ca.pem"))
                .tlsClientCertificate(Path.of("client.pem"))
                .insecureTls()
                .cacheDirectory(Path.of("cache"))
                .cleanupCache()
                .noExtraVerify()
                .stuckRequestTimeout(Duration.ofMinutes(1))
                .httpUserAgent("restic-jvm-test")
                .build()

        assertEquals(
            listOf(
                "--repository-file",
                "repo.txt",
                "--password-command",
                "pass show restic",
                "--insecure-no-password",
                "--key-hint",
                "abc123",
                "--cacert",
                "ca.pem",
                "--tls-client-cert",
                "client.pem",
                "--insecure-tls",
                "--cache-dir",
                "cache",
                "--cleanup-cache",
                "--no-extra-verify",
                "--stuck-request-timeout",
                "60s",
                "--http-user-agent",
                "restic-jvm-test",
            ),
            configuration.globalArguments(),
        )
    }

    @Test
    fun `configuration exposes every value it was built with`() {
        val configuration = fullyConfigured()

        assertEquals("/opt/restic", configuration.binary)
        assertEquals("rest:http://host/", configuration.repository)
        assertEquals(Path.of("repo.txt"), configuration.repositoryFile)
        assertEquals(Path.of("pw.txt"), configuration.passwordFile)
        assertEquals("pass show restic", configuration.passwordCommand)
        assertEquals("geheim", configuration.password)
        assertTrue(configuration.insecureNoPassword)
        assertEquals("abc123", configuration.keyHint)
        assertEquals(Path.of("ca.pem"), configuration.caCertificate)
        assertEquals(Path.of("client.pem"), configuration.tlsClientCertificate)
        assertTrue(configuration.insecureTls)
        assertEquals(Path.of("cache"), configuration.cacheDirectory)
        assertTrue(configuration.noCache)
        assertTrue(configuration.cleanupCache)
        assertEquals(Compression.AUTO, configuration.compression)
        assertEquals(16, configuration.packSizeMebibytes)
        assertTrue(configuration.noExtraVerify)
        assertTrue(configuration.noLock)
        assertEquals(Duration.ofSeconds(30), configuration.retryLock)
        assertEquals(Duration.ofMinutes(2), configuration.stuckRequestTimeout)
        assertEquals(300L, configuration.limitDownloadKibPerSecond)
        assertEquals(400L, configuration.limitUploadKibPerSecond)
        assertEquals("restic-jvm-test", configuration.httpUserAgent)
        assertEquals(mapOf("s3.region" to "eu"), configuration.extendedOptions)
        assertEquals(1, configuration.verbosity)
        assertEquals(mapOf("AWS_PROFILE" to "backup"), configuration.environment)
        assertEquals(Path.of("work"), configuration.workingDirectory)
        assertEquals(Duration.ofHours(1), configuration.timeout)
    }

    @Test
    fun `defaults are off and empty`() {
        val configuration = ResticConfiguration.builder().build()

        assertEquals("restic", configuration.binary)
        assertFalse(configuration.insecureNoPassword || configuration.insecureTls || configuration.noCache)
        assertFalse(configuration.cleanupCache || configuration.noExtraVerify || configuration.noLock)
        assertNull(configuration.workingDirectory)
        assertNull(configuration.timeout)
        assertEquals(emptyMap<String, String>(), configuration.processEnvironment())
    }

    @Test
    fun `environment variables are passed alongside the password`() {
        val environment = fullyConfigured().processEnvironment()

        assertEquals(mapOf("AWS_PROFILE" to "backup", "RESTIC_PASSWORD" to "geheim"), environment)
    }

    private fun fullyConfigured(): ResticConfiguration =
        ResticConfiguration
            .builder()
            .binary("/opt/restic")
            .repository("rest:http://host/")
            .repositoryFile(Path.of("repo.txt"))
            .passwordFile(Path.of("pw.txt"))
            .passwordCommand("pass show restic")
            .password("geheim")
            .insecureNoPassword()
            .keyHint("abc123")
            .caCertificate(Path.of("ca.pem"))
            .tlsClientCertificate(Path.of("client.pem"))
            .insecureTls()
            .cacheDirectory(Path.of("cache"))
            .noCache()
            .cleanupCache()
            .compression(Compression.AUTO)
            .packSizeMebibytes(16)
            .noExtraVerify()
            .noLock()
            .retryLock(Duration.ofSeconds(30))
            .stuckRequestTimeout(Duration.ofMinutes(2))
            .limitDownloadKibPerSecond(300)
            .limitUploadKibPerSecond(400)
            .httpUserAgent("restic-jvm-test")
            .extendedOption("s3.region", "eu")
            .verbosity(1)
            .environment("AWS_PROFILE", "backup")
            .workingDirectory(Path.of("work"))
            .timeout(Duration.ofHours(1))
            .build()

    @Test
    fun `password goes to environment and not to arguments`() {
        val configuration = ResticConfiguration.builder().password("geheim").build()
        assertEquals(emptyList<String>(), configuration.globalArguments())
        assertEquals("geheim", configuration.processEnvironment()["RESTIC_PASSWORD"])
    }

    @Test
    fun `durations below a second are passed in milliseconds instead of being cut to zero`() {
        val configuration =
            ResticConfiguration
                .builder()
                .retryLock(Duration.ofMillis(1500))
                .stuckRequestTimeout(Duration.ofMillis(500))
                .build()

        assertEquals(
            listOf("--retry-lock", "1500ms", "--stuck-request-timeout", "500ms"),
            configuration.globalArguments(),
        )
    }
}
