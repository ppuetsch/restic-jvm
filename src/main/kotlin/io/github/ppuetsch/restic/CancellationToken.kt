package io.github.ppuetsch.restic

import java.util.concurrent.ConcurrentHashMap

/**
 * Cancels running restic calls from another thread; the calls then throw [ResticCancelledException].
 * restic and all its child processes are ended. One token may be passed to several calls, also running in parallel;
 * [cancel] stops all of them. Once cancelled it stays cancelled: later calls with this token end immediately.
 */
public class CancellationToken {
    private val runningProcesses: MutableSet<Process> = ConcurrentHashMap.newKeySet()

    /** `true` once [cancel] has been called. */
    @Volatile
    public var isCancelled: Boolean = false
        private set

    /** Cancels all running and future calls with this token; calling it more than once does no harm. */
    public fun cancel() {
        isCancelled = true
        runningProcesses.forEach(ProcessTree::terminate)
    }

    internal fun attach(process: Process) {
        runningProcesses.add(process)
        if (isCancelled) ProcessTree.terminate(process)
    }

    internal fun detach(process: Process) {
        runningProcesses.remove(process)
    }
}
