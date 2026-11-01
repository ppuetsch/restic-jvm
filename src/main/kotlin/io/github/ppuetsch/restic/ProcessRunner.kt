package io.github.ppuetsch.restic

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val LINE_FEED: Char = '\n'

/** Blockierendes Lesen der Prozess-Streams gehört nicht in den gemeinsamen ForkJoinPool. */
internal val STREAM_READERS: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

private val WATCHDOG: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "restic-watchdog").apply { isDaemon = true } }

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
        try {
            return superviseUntilExit(process, request, onStandardOutputLine)
        } finally {
            request.cancellation?.detach(process)
        }
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

    private fun superviseUntilExit(
        process: Process,
        request: ProcessRequest,
        onStandardOutputLine: (String) -> Unit,
    ): ProcessOutcome {
        val timedOut = AtomicBoolean(false)
        val watchdog = startWatchdog(process, configuration.timeout, timedOut)
        feedStandardInput(process, request.standardInput)
        val standardError = StandardErrorReader(process, request.onStandardErrorLine)
        try {
            consumeStandardOutput(process, request.standardOutput, onStandardOutputLine)
        } catch (
            // Listener dürfen alles werfen; nach einem Abbruch zählt der Abbruch
            @Suppress("TooGenericExceptionCaught")
            failure: Throwable,
        ) {
            throwIfStoppedByUs(request.cancellation, timedOut.get())
            throw failure
        } finally {
            watchdog?.cancel(false)
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) throwIfStoppedByUs(request.cancellation, timedOut.get())
        return ProcessOutcome(exitCode, standardError.await())
    }

    /**
     * Nach Abbruch oder Timeout sind Lesefehler und halbe Ausgaben Folgen des Beendens, nicht die Ursache; gemeldet
     * wird deshalb der Abbruch. Endete restic trotzdem regulär mit 0, gilt das Ergebnis (siehe Aufrufer).
     */
    private fun throwIfStoppedByUs(
        cancellation: CancellationToken?,
        timedOut: Boolean,
    ) {
        if (cancellation?.isCancelled == true) throw ResticCancelledException()
        if (timedOut) throw ResticTimeoutException("restic did not finish within ${configuration.timeout}")
    }
}

private fun consumeStandardOutput(
    process: Process,
    binaryTarget: OutputStream?,
    onLine: (String) -> Unit,
) {
    var consumedCompletely = false
    try {
        if (binaryTarget != null) {
            process.inputStream.transferTo(binaryTarget)
            binaryTarget.flush()
        } else {
            process.inputStream.bufferedReader().forEachLine(onLine)
        }
        consumedCompletely = true
    } finally {
        if (!consumedCompletely) ProcessTree.terminate(process)
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
    STREAM_READERS.execute {
        try {
            process.outputStream.use { standardInput.transferTo(it) }
        } catch (ignored: IOException) {
            // restic hat sich beendet, bevor die Eingabe gelesen war; der Exit-Code erklärt den Grund
        }
    }
}

private fun startWatchdog(
    process: Process,
    timeout: Duration?,
    timedOut: AtomicBoolean,
): ScheduledFuture<*>? {
    if (timeout == null) return null
    return WATCHDOG.schedule({
        timedOut.set(true)
        ProcessTree.terminateForcibly(process)
    }, timeout.toMillis(), TimeUnit.MILLISECONDS)
}

/**
 * Liest stderr vollständig, auch wenn der Listener wirft: Bliebe die Pipe ungelesen, liefe sie voll,
 * restic blockierte beim Schreiben und der Aufruf hinge für immer. Der erste Listener-Fehler wird in [await] geworfen.
 */
private class StandardErrorReader(
    process: Process,
    private val onLine: ((String) -> Unit)?,
) {
    private val listenerFailure = AtomicReference<Throwable?>(null)
    private val text: CompletableFuture<String> = CompletableFuture.supplyAsync({ read(process) }, STREAM_READERS)

    fun await(): String {
        val collected =
            try {
                text.join()
            } catch (failure: CompletionException) {
                throw failure.cause ?: failure
            }
        listenerFailure.get()?.let { throw it }
        return collected
    }

    private fun read(process: Process): String {
        val reader = process.errorStream.bufferedReader()
        if (onLine == null) return reader.readText()
        val collected = StringBuilder()
        reader.forEachLine { line ->
            collected.append(line).append(LINE_FEED)
            notifyListener(line)
        }
        return collected.toString()
    }

    private fun notifyListener(line: String) {
        if (listenerFailure.get() != null) return
        try {
            onLine?.invoke(line)
        } catch (
            // ein Listener darf alles werfen, stderr muss trotzdem weiter gelesen werden
            @Suppress("TooGenericExceptionCaught")
            failure: Throwable,
        ) {
            listenerFailure.compareAndSet(null, failure)
        }
    }
}
