package io.github.ppuetsch.restic

/** Keeping the repository healthy and small: prune, check, stats, locks, caches. Obtained from [Restic.maintenance]. */
public class MaintenanceCommands internal constructor(
    private val runner: ProcessRunner,
) {
    /**
     * Frees space no snapshot references any more (`restic prune`).
     *
     * @param listener Receives the text output while restic runs.
     */
    @JvmOverloads
    public fun prune(
        options: PruneOptions = PruneOptions.DEFAULT,
        listener: OutputLineListener? = null,
    ): PruneResult = PruneCommand(runner).prune(options, listener)

    /** Kotlin variant of [prune] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun prune(configure: PruneOptions.Builder.() -> Unit): PruneResult = prune(PruneOptions.builder().apply(configure).build())

    /**
     * Checks the repository for errors (`restic check`).
     *
     * A damaged repository does not cause an exception but [CheckResult.isHealthy] = `false`;
     * exceptions occur only if the check itself could not run.
     *
     * @param listener Receives the messages in readable form while restic runs.
     */
    @JvmOverloads
    public fun check(
        options: CheckOptions = CheckOptions.DEFAULT,
        listener: OutputLineListener? = null,
    ): CheckResult = CheckAndStatsCommand(runner).check(options, listener)

    /** Kotlin variant of [check] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun check(
        listener: OutputLineListener? = null,
        configure: CheckOptions.Builder.() -> Unit,
    ): CheckResult = check(CheckOptions.builder().apply(configure).build(), listener)

    /** Sizes and counts of the repository or of selected snapshots (`restic stats`). */
    @JvmOverloads
    public fun stats(
        options: StatsOptions = StatsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): StatsResult = CheckAndStatsCommand(runner.cancellableBy(cancellationToken ?: options.cancellationToken)).stats(options)

    /** Kotlin variant of [stats] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun stats(configure: StatsOptions.Builder.() -> Unit): StatsResult = stats(StatsOptions.builder().apply(configure).build())

    /**
     * Removes stale locks (`restic unlock`).
     *
     * @param removeAll Also removes locks of running processes; only use it when you are sure none is running any more.
     */
    @JvmOverloads
    public fun unlock(
        removeAll: Boolean = false,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).unlock(removeAll)

    /**
     * Runs migrations of the repository format (`restic migrate`).
     *
     * @param names Migrations to run, for example `upgrade_repo_v2`; without names restic lists the available ones.
     * @param force Runs the migration even if restic advises against it.
     */
    @JvmOverloads
    public fun migrate(
        names: List<String> = emptyList(),
        force: Boolean = false,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).migrate(names, force)

    /** Shows or cleans up the local cache directories (`restic cache`). */
    @JvmOverloads
    public fun cache(
        options: CacheOptions = CacheOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = admin(cancellationToken).cache(options)

    /** Kotlin variant of [cache] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun cache(configure: CacheOptions.Builder.() -> Unit): CommandOutput = cache(CacheOptions.builder().apply(configure).build())

    /** Rebuilds snapshots from orphaned trees (`restic recover`). */
    @JvmOverloads
    public fun recover(cancellationToken: CancellationToken? = null): CommandOutput = admin(cancellationToken).recover()

    /** The feature flags of the restic binary with stability and default value (`restic features`). */
    @JvmOverloads
    public fun features(cancellationToken: CancellationToken? = null): List<FeatureFlag> = admin(cancellationToken).features()

    private fun admin(cancellationToken: CancellationToken?): AdminCommands = AdminCommands(runner.cancellableBy(cancellationToken))
}
