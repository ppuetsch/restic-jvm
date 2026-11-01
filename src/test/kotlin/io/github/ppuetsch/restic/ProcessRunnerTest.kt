package io.github.ppuetsch.restic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.writeText

class ProcessRunnerTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val resticBinary: String = System.getenv("RESTIC_BINARY") ?: DEFAULT_RESTIC_BINARY

    private fun runnerFor(
        repositoryName: String,
        password: String = "secret",
    ): ProcessRunner =
        ProcessRunner(
            ResticConfiguration
                .builder()
                .binary(resticBinary)
                .repository(temporaryDirectory.resolve(repositoryName).toString())
                .password(password)
                .build(),
        )

    @Test
    fun `version printed on standard output`() {
        val lines = mutableListOf<String>()
        ProcessRunner(ResticConfiguration.builder().binary(resticBinary).build())
            .runChecked(listOf("version"), onStandardOutputLine = lines::add)
        assertTrue(lines.single().startsWith("restic "), lines.toString())
    }

    @Test
    fun `missing repository maps to RepositoryNotFoundException`() {
        assertThrows<RepositoryNotFoundException> { runnerFor("gibtsnicht").runChecked(listOf("snapshots")) }
    }

    @Test
    fun `wrong password maps to WrongPasswordException`() {
        runnerFor("repo").runChecked(listOf("init"))
        assertThrows<WrongPasswordException> { runnerFor("repo", "falsch").runChecked(listOf("snapshots")) }
    }

    @Test
    fun `unknown binary maps to ResticNotAvailableException`() {
        val runner = ProcessRunner(ResticConfiguration.builder().binary("restic-gibt-es-nicht").build())
        assertThrows<ResticNotAvailableException> { runner.run(listOf("version")) }
    }

    @Test
    fun `exit code is mapped to generic failure`() {
        val failure = assertThrows<ResticCommandException> { runnerFor("repo").runChecked(listOf("nonexistent-command")) }
        assertEquals(1, failure.exitCode)
    }

    @Test
    fun `timeout kills a hanging process`() {
        val hangingProgram = temporaryDirectory.resolve("Hang.java")
        hangingProgram.writeText("public class Hang { public static void main(String[] a) throws Exception { Thread.sleep(60000); } }")
        val javaLauncher = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val configuration =
            ResticConfiguration
                .builder()
                .binary(javaLauncher)
                .timeout(Duration.ofSeconds(2))
                .build()
        assertThrows<ResticTimeoutException> { ProcessRunner(configuration).run(listOf(hangingProgram.toString())) }
    }
}
