package io.github.ppuetsch.restic

import java.io.IOException
import java.io.InputStream
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

internal class ProcessRequest(
    val arguments: List<String>,
    val extraEnvironment: Map<String, String> = emptyMap(),
    val standardInput: InputStream? = null,
    val cancellation: CancellationToken? = null,
)

internal class ProcessRunner(private val configuration: ResticConfiguration) {
    fun run(
        arguments: List<String>,
        onStandardOutputLine: (String) -> Unit = {},
    ): ProcessOutcome = run(ProcessRequest(arguments), onStandardOutputLine)

    fun run(
        request: ProcessRequest,
        onStandardOutputLine: (String) -> Unit = {},
    ): ProcessOutcome {
        val process = start(request)
        request.cancellation?.attach(process)
        val timedOut = AtomicBoolean(false)
        val watchdog = startWatchdog(process, timedOut)
        feedStandardInput(process, request.standardInput)
        val standardError = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }
        try {
            process.inputStream.bufferedReader().forEachLine(onStandardOutputLine)
            process.waitFor()
        } finally {
            watchdog?.cancel(false)
            process.destroy()
        }
        if (request.cancellation?.isCancelled == true) throw ResticCancelledException()
        if (timedOut.get()) throw ResticTimeoutException("restic did not finish within ${configuration.timeout}")
        return ProcessOutcome(process.exitValue(), standardError.get())
    }

    fun runChecked(
        arguments: List<String>,
        onStandardOutputLine: (String) -> Unit = {},
    ): ProcessOutcome = runChecked(ProcessRequest(arguments), onStandardOutputLine)

    /** Wirft bei Exit-Code != 0 die passende [ResticCommandException]. */
    fun runChecked(
        request: ProcessRequest,
        onStandardOutputLine: (String) -> Unit = {},
    ): ProcessOutcome {
        val outcome = run(request, onStandardOutputLine)
        if (outcome.exitCode != 0) throw commandExceptionFor(outcome.exitCode, outcome.standardError)
        return outcome
    }

    private fun start(request: ProcessRequest): Process {
        val builder = ProcessBuilder(listOf(configuration.binary) + configuration.globalArguments() + request.arguments)
        builder.environment().putAll(configuration.processEnvironment())
        builder.environment().putAll(request.extraEnvironment)
        configuration.workingDirectory?.let { builder.directory(it.toFile()) }
        return try {
            builder.start()
        } catch (failure: IOException) {
            throw ResticNotAvailableException("Cannot start '${configuration.binary}': ${failure.message}", failure)
        }
    }

    private fun feedStandardInput(
        process: Process,
        standardInput: InputStream?,
    ) {
        if (standardInput == null) {
            process.outputStream.close()
            return
        }
        Thread.ofVirtual().start {
            try {
                process.outputStream.use { standardInput.transferTo(it) }
            } catch (ignored: IOException) {
                // restic hat sich beendet, bevor die Eingabe gelesen war; der Exit-Code erklärt den Grund
            }
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
