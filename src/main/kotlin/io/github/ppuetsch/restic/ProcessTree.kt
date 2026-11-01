package io.github.ppuetsch.restic

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val FORCIBLE_KILL_DELAY_MILLIS: Long = 2_000

/**
 * Beendet restic samt aller Kindprozesse (z.B. rclone oder ssh als Backend-Helfer).
 * `destroy()` allein erreicht diese nicht. Unter Linux hängen verwaiste Kinder nach dem Tod des Vaters an init und sind
 * über ihn nicht mehr auffindbar, deshalb wird die Liste der Nachfahren vor dem Beenden festgehalten. Die verzögerte
 * Zwangsbeendigung sucht zusätzlich erneut, um Kinder zu erwischen, die während des Beendens noch gestartet wurden
 * (unter Windows bleibt die Eltern-PID erhalten, dort werden sie so gefunden).
 */
internal object ProcessTree {
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "restic-process-reaper").apply { isDaemon = true } }

    fun terminate(process: Process) {
        val descendants = process.descendants().toList()
        process.destroy()
        descendants.forEach(ProcessHandle::destroy)
        scheduler.schedule({ killSurvivors(process, descendants) }, FORCIBLE_KILL_DELAY_MILLIS, TimeUnit.MILLISECONDS)
    }

    fun terminateForcibly(process: Process) {
        val descendants = process.descendants().toList()
        process.destroyForcibly()
        descendants.forEach(ProcessHandle::destroyForcibly)
        scheduler.schedule({ killSurvivors(process, descendants) }, FORCIBLE_KILL_DELAY_MILLIS, TimeUnit.MILLISECONDS)
    }

    private fun killSurvivors(
        process: Process,
        knownDescendants: List<ProcessHandle>,
    ) {
        val lateDescendants = process.descendants().toList()
        if (process.isAlive) process.destroyForcibly()
        (knownDescendants + lateDescendants).filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly)
    }
}
