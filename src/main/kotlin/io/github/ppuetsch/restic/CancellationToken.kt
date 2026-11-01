package io.github.ppuetsch.restic

import java.util.concurrent.atomic.AtomicReference

/**
 * Bricht einen laufenden restic-Aufruf von einem anderen Thread aus ab.
 * Der Aufruf wirft dann [ResticCancelledException]. Beendet werden restic und alle seine Kindprozesse.
 */
public class CancellationToken {
    private val runningProcess = AtomicReference<Process?>(null)

    @Volatile
    public var isCancelled: Boolean = false
        private set

    public fun cancel() {
        isCancelled = true
        runningProcess.get()?.let(ProcessTree::terminate)
    }

    internal fun attach(process: Process) {
        runningProcess.set(process)
        if (isCancelled) ProcessTree.terminate(process)
    }
}
