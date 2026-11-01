package io.github.ppuetsch.restic

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val LINE_FEED: Char = '\n'

internal class ProcessOutcome(
    val exitCode: Int,
    val standardError: String,
)

internal class ProcessRequest(
    val arguments: List<String>,
    val extraEnvironment: Map<String, String> = emptyMap(),
    val standardInput: InputStream? = null,
    val cancellation: CancellationToken? = null,
    val standardOutput: OutputStream? = null,
    val onStandardErrorLine: ((String) -> Unit)? = null,
) {
    fun withDefaultCancellation(token: CancellationToken?): ProcessRequest =
        if (cancellation != null || token == null) {
            this
        } else {
            ProcessRequest(arguments, extraEnvironment, standardInput, token, standardOutput, onStandardErrorLine)
        }
}

internal class ProcessRunner(
    private val configuration: ResticConfiguration,
    private val defaultCancellation: CancellationToken? = null,
) {
    /** Ein Runner, dessen Aufrufe über [token] abbrechbar sind, sofern sie nicht schon ein eigenes Token tragen. */
    fun cancellableBy(token: CancellationToken?): ProcessRunner = if (token == null) this else ProcessRunner(configuration, token)

    fun run(
        arguments: List<String>,
        onStandardOutputLine: (String) -> Unit = {},
    ): ProcessOutcome = run(ProcessRequest(arguments), onStandardOutputLine)

    fun run(
        plainRequest: ProcessRequest,
        onStandardOutputLine: (String) -> Unit = {},
    ): ProcessOutcome {
        val request = plainRequest.withDefaultCancellation(defaultCancellation)
        val process = start(request)
        request.cancellation?.attach(process)
        val timedOut = AtomicBoolean(false)
        val watchdog = startWatchdog(process, timedOut)
        feedStandardInput(process, request.standardInput)
        val standardError = CompletableFuture.supplyAsync { readStandardError(process, request.onStandardErrorLine) }
        try {
            consumeStandardOutput(process, request.standardOutput, onStandardOutputLine)
            process.waitFor()
        } catch (failure: IOException) {
            val wasStoppedByUs = timedOut.get() || request.cancellation?.isCancelled == true
            if (!wasStoppedByUs) throw failure
        } finally {
            watchdog?.cancel(false)
            if (process.isAlive) ProcessTree.terminate(process)
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

    /** Startet den Prozess und überlässt dem Aufrufer Ein- und Ausgabe sowie das Beenden. */
    fun startProcess(request: ProcessRequest): Process = start(request)

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

    private fun readStandardError(
        process: Process,
        onLine: ((String) -> Unit)?,
    ): String {
        val reader = process.errorStream.bufferedReader()
        if (onLine == null) return reader.readText()
        val collected = StringBuilder()
        reader.forEachLine { line ->
            collected.append(line).append(LINE_FEED)
            onLine(line)
        }
        return collected.toString()
    }

    private fun consumeStandardOutput(
        process: Process,
        binaryTarget: OutputStream?,
        onLine: (String) -> Unit,
    ) {
        if (binaryTarget != null) {
            process.inputStream.transferTo(binaryTarget)
            binaryTarget.flush()
        } else {
            process.inputStream.bufferedReader().forEachLine(onLine)
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
            ProcessTree.terminateForcibly(process)
        }, timeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    private companion object {
        val WATCHDOG: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "restic-watchdog").apply { isDaemon = true } }
    }
}
