package io.github.ppuetsch.restic

/** Repairing a damaged repository (`restic repair`). Obtained from [Restic.repair]. */
public class RepairCommands internal constructor(
    private val runner: ProcessRunner,
) {
    /**
     * Rebuilds the index (`restic repair index`).
     *
     * @param readAllPacks Reads all packs instead of only the unknown ones; slow, but thorough.
     */
    @JvmOverloads
    public fun index(
        readAllPacks: Boolean = false,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = AdminCommands(runner.cancellableBy(cancellationToken)).repairIndex(readAllPacks)

    /** Salvages the readable blobs of damaged packs (`restic repair packs`). */
    @JvmOverloads
    public fun packs(
        packIds: List<String>,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = AdminCommands(runner.cancellableBy(cancellationToken)).repairPacks(packIds)

    /** Repairs snapshots that refer to missing data (`restic repair snapshots`). */
    @JvmOverloads
    public fun snapshots(
        options: RepairSnapshotsOptions = RepairSnapshotsOptions.DEFAULT,
        cancellationToken: CancellationToken? = null,
    ): CommandOutput = AdminCommands(runner.cancellableBy(cancellationToken)).repairSnapshots(options)

    /** Kotlin variant of [snapshots] that builds the options with a builder lambda. */
    @JvmSynthetic
    public fun snapshots(configure: RepairSnapshotsOptions.Builder.() -> Unit): CommandOutput =
        snapshots(RepairSnapshotsOptions.builder().apply(configure).build())
}
