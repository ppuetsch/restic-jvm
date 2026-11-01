package io.github.ppuetsch.restic

import java.util.concurrent.ConcurrentHashMap

/**
 * Bricht laufende restic-Aufrufe von einem anderen Thread aus ab; die Aufrufe werfen dann [ResticCancelledException].
 * Beendet werden restic und alle seine Kindprozesse. Ein Token darf an mehrere, auch parallele Aufrufe gegeben werden;
 * [cancel] trifft alle. Einmal abgebrochen bleibt es abgebrochen: spätere Aufrufe mit diesem Token enden sofort.
 */
public class CancellationToken {
    private val runningProcesses: MutableSet<Process> = ConcurrentHashMap.newKeySet()

    @Volatile
    public var isCancelled: Boolean = false
        private set

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
