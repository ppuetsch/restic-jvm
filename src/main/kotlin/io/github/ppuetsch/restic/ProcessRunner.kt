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

/** Blocking reads of process streams do not belong in the common ForkJoinPool. */
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
    /** A runner whose calls can be cancelled through [token], unless they already carry a token of their own. */
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
        // a cancelled token must win deterministically; a started restic could otherwise finish first and succeed
        if (request.cancellation?.isCancelled == true) throw ResticCancelledException()
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

    /** Throws the matching [ResticCommandException] for an exit code other than 0. */
    fun runChecked(
        request: ProcessRequest,
        onStandardOutputLine: (String) -> Unit = {},
    ): ProcessOutcome {
        val outcome = run(request, onStandardOutputLine)
        if (outcome.exitCode != 0) throw commandExceptionFor(outcome.exitCode, outcome.standardError)
        return outcome
    }

    /** Starts the process and leaves input, output and ending it to the caller. */
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
        var outputConsumed = false
        try {
            consumeStandardOutput(process, request.standardOutput, onStandardOutputLine)
            outputConsumed = true
        } finally {
            watchdog?.cancel(false)
            // a listener may have thrown anything; after a cancellation, the cancellation is what counts
            if (!outputConsumed) throwIfStoppedByUs(request.cancellation, timedOut.get())
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) throwIfStoppedByUs(request.cancellation, timedOut.get())
        return ProcessOutcome(exitCode, standardError.await())
    }

    /**
     * After a cancellation or timeout, read errors and partial output are consequences of ending the process, not the
     * cause, so the cancellation is reported. If restic still ended regularly with 0, the result stands (see caller).
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
            // restic exited before the input was read; the exit code explains why
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
 * Reads stderr completely, even if the listener throws: an unread pipe would fill up, restic would block while
 * writing, and the call would hang forever. The first listener failure is thrown in [await].
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
        // a listener may throw anything, stderr must still be read to the end
        runCatching { onLine?.invoke(line) }.onFailure { failure -> listenerFailure.compareAndSet(null, failure) }
    }
}
