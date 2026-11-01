package io.github.ppuetsch.restic

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val FAKE_SOURCE_RESOURCE: String = "/FakeRestic.java"
private val IS_WINDOWS: Boolean = System.getProperty("os.name").startsWith("Windows")
private val PID_POLL_INTERVAL: Duration = Duration.ofMillis(100)

/** Ein scriptbares Ersatzprogramm für restic, siehe `FakeRestic.java` für die Anweisungen. */
internal class FakeRestic(private val directory: Path, vararg scenario: String) {
    private val source: Path = directory.resolve("FakeRestic.java")
    private val scenarioFile: Path = directory.resolve("scenario.txt")
    private val launcher: Path = directory.resolve(if (IS_WINDOWS) "fake-restic.cmd" else "fake-restic.sh")

    init {
        source.writeText(readSourceResource())
        scenarioFile.writeText(scenario.joinToString("\n"))
        writeLauncher()
    }

    fun restic(configure: ResticConfiguration.Builder.() -> Unit = {}): Restic =
        Restic(
            ResticConfiguration.builder()
                .binary(launcher.toString())
                .environment("FAKE_RESTIC_SCENARIO", scenarioFile.toString())
                .environment("FAKE_RESTIC_SOURCE", source.toString())
                .apply(configure)
                .build(),
        )

    private fun writeLauncher() {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        if (IS_WINDOWS) {
            launcher.writeText("@\"$java\" \"$source\" %*\r\n")
        } else {
            launcher.writeText("#!/bin/sh\nexec \"$java\" \"$source\" \"$@\"\n")
            launcher.toFile().setExecutable(true)
        }
    }

    private fun readSourceResource(): String =
        checkNotNull(FakeRestic::class.java.getResourceAsStream(FAKE_SOURCE_RESOURCE)).use { String(it.readAllBytes()) }

    fun pidWrittenTo(pidFileName: String): Path = directory.resolve(pidFileName)
}

internal fun Path.awaitPid(timeout: Duration): Long {
    val deadline = System.nanoTime() + timeout.toNanos()
    while (System.nanoTime() < deadline) {
        if (exists() && Files.size(this) > 0) return readText().trim().toLong()
        Thread.sleep(PID_POLL_INTERVAL.toMillis())
    }
    error("no pid written to $this within $timeout")
}

internal fun Long.awaitExit(timeout: Duration): Boolean {
    val deadline = System.nanoTime() + timeout.toNanos()
    while (isRunning() && System.nanoTime() < deadline) Thread.sleep(PID_POLL_INTERVAL.toMillis())
    return !isRunning()
}

internal fun Long.isRunning(): Boolean = ProcessHandle.of(this).map(ProcessHandle::isAlive).orElse(false)
