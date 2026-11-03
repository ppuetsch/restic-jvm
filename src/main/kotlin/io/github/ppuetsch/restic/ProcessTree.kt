package io.github.ppuetsch.restic

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val FORCIBLE_KILL_DELAY_MILLIS: Long = 2_000

/**
 * Ends restic together with all its child processes (for example rclone or ssh as backend helpers).
 * `destroy()` alone does not reach them. On Linux, orphaned children are re-parented to init when their parent dies and
 * can no longer be found through it, so the list of descendants is captured before ending anything. The delayed forced
 * kill searches again to catch children that were started while ending
 * (on Windows the parent PID is kept, so they are found that way).
 */
internal object ProcessTree {
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "restic-process-reaper").apply { isDaemon = true } }

    fun terminate(process: Process) {
        val descendants = descendantsOf(process)
        process.destroy()
        descendants.forEach(ProcessHandle::destroy)
        scheduler.schedule({ killSurvivors(process, descendants) }, FORCIBLE_KILL_DELAY_MILLIS, TimeUnit.MILLISECONDS)
    }

    fun terminateForcibly(process: Process) {
        val descendants = descendantsOf(process)
        process.destroyForcibly()
        descendants.forEach(ProcessHandle::destroyForcibly)
        scheduler.schedule({ killSurvivors(process, descendants) }, FORCIBLE_KILL_DELAY_MILLIS, TimeUnit.MILLISECONDS)
    }

    private fun killSurvivors(
        process: Process,
        knownDescendants: List<ProcessHandle>,
    ) {
        val lateDescendants = descendantsOf(process)
        if (process.isAlive) process.destroyForcibly()
        (knownDescendants + lateDescendants).filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly)
    }

    private fun descendantsOf(process: Process): List<ProcessHandle> = process.descendants().toList()
}
