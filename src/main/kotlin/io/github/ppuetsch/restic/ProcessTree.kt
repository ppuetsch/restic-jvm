package io.github.ppuetsch.restic

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val FORCIBLE_KILL_DELAY_MILLIS: Long = 2_000

/**
 * Beendet restic samt aller Kindprozesse (z.B. rclone oder ssh als Backend-Helfer).
 * `destroy()` allein erreicht diese nicht; nach dem Beenden des Vaters wären sie außerdem nicht mehr auffindbar,
 * deshalb wird die Liste der Nachfahren vorher festgehalten.
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
    }

    private fun killSurvivors(
        process: Process,
        descendants: List<ProcessHandle>,
    ) {
        if (process.isAlive) process.destroyForcibly()
        descendants.filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly)
    }
}
