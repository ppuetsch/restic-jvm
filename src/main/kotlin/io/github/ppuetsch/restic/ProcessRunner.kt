package io.github.ppuetsch.restic

import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class ProcessOutcome(
    val exitCode: Int,
    val standardError: String,
)

internal class ProcessRunner(private val configuration: ResticConfiguration) {
    fun run(
        arguments: List<String>,
        extraEnvironment: Map<String, String> = emptyMap(),
        onStandardOutputLine: (String) -> Unit = {},
    ): ProcessOutcome {
        val process = start(arguments, extraEnvironment)
        val timedOut = AtomicBoolean(false)
        val watchdog = startWatchdog(process, timedOut)
        val standardError = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }
        try {
            process.inputStream.bufferedReader().forEachLine(onStandardOutputLine)
            process.waitFor()
        } finally {
            watchdog?.cancel(false)
            process.destroy()
        }
        val timeout = configuration.timeout
        if (timedOut.get()) throw ResticTimeoutException("restic did not finish within $timeout")
        return ProcessOutcome(process.exitValue(), standardError.get())
    }

    /** Wirft bei Exit-Code != 0 die passende [ResticCommandException]. */
    fun runChecked(
        arguments: List<String>,
        extraEnvironment: Map<String, String> = emptyMap(),
        onStandardOutputLine: (String) -> Unit = {},
    ): ProcessOutcome {
        val outcome = run(arguments, extraEnvironment, onStandardOutputLine)
        if (outcome.exitCode != 0) throw commandExceptionFor(outcome.exitCode, outcome.standardError)
        return outcome
    }

    private fun start(
        arguments: List<String>,
        extraEnvironment: Map<String, String>,
    ): Process {
        val builder = ProcessBuilder(listOf(configuration.binary) + configuration.globalArguments() + arguments)
        builder.environment().putAll(configuration.processEnvironment())
        builder.environment().putAll(extraEnvironment)
        configuration.workingDirectory?.let { builder.directory(it.toFile()) }
        return try {
            builder.start()
        } catch (failure: IOException) {
            throw ResticNotAvailableException("Cannot start '${configuration.binary}': ${failure.message}", failure)
        }
    }

    private fun startWatchdog(
        process: Process,
        timedOut: AtomicBoolean,
    ): ScheduledFuture<*>? {
        val timeout = configuration.timeout ?: return null
        return WATCHDOG.schedule({
            timedOut.set(true)
            process.destroyForcibly()
        }, timeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    private companion object {
        val WATCHDOG: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "restic-watchdog").apply { isDaemon = true } }
    }
}
