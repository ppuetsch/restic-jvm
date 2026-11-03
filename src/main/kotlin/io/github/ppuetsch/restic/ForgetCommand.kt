package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/**
 * Why a snapshot is kept.
 *
 * @property snapshotId ID of the kept snapshot.
 * @property matches The rules that apply, for example `daily snapshot` or `last snapshot`.
 */
public data class ForgetReason(
    val snapshotId: String,
    val matches: List<String>,
)

/**
 * The decisions for one group of snapshots.
 *
 * @property host Host of the group, if grouped by host.
 * @property paths Paths of the group, if grouped by paths.
 * @property tags Tags of the group, if grouped by tags.
 * @property keep Kept snapshots.
 * @property remove Removed snapshots.
 * @property reasons For each kept snapshot, the rules that apply.
 */
public data class ForgetGroup(
    val host: String?,
    val paths: List<String>,
    val tags: List<String>,
    val keep: List<Snapshot>,
    val remove: List<Snapshot>,
    val reasons: List<ForgetReason>,
)

/**
 * Result of [Restic.forget].
 *
 * @property groups The decisions per group.
 * @property pruneOutputLines Only with `prune`: the text output of the subsequent prune run.
 */
public data class ForgetResult(
    val groups: List<ForgetGroup>,
    val pruneOutputLines: List<String>,
) {
    /** All removed snapshots across all groups. */
    public fun removedSnapshots(): List<Snapshot> = groups.flatMap { it.remove }
}

private fun forgetGroupFrom(node: JsonNode) =
    ForgetGroup(
        host = node.textOrNull("host"),
        paths = node.textList("paths"),
        tags = node.textList("tags"),
        keep = node.get("keep")?.mapElements(::snapshotFrom).orEmpty(),
        remove = node.get("remove")?.mapElements(::snapshotFrom).orEmpty(),
        reasons =
            node
                .get("reasons")
                ?.mapElements { reason ->
                    ForgetReason(reason.get("snapshot")?.textOrNull("id").orEmpty(), reason.textList("matches"))
                }.orEmpty(),
    )

/** Prune progress such as `[0:00] 100.00%` also starts with `[`, but is not JSON. */
private fun isGroupDocument(line: String): Boolean = Json.parseOrNull(line)?.isArray == true

internal class ForgetCommand(
    private val runner: ProcessRunner,
) {
    fun forget(
        options: ForgetOptions,
        outputListener: OutputLineListener?,
    ): ForgetResult {
        var groups: List<ForgetGroup>? = null
        val pruneOutput = mutableListOf<String>()
        val request =
            ProcessRequest(
                listOf("forget", "--json") + options.arguments(),
                cancellation = options.cancellationToken,
                onStandardErrorLine = readableErrorLines(outputListener),
            )
        runner.runChecked(request) { line ->
            if (line.isBlank()) return@runChecked
            when {
                groups == null && isGroupDocument(line) -> {
                    groups = Json.parse(line).mapElements(::forgetGroupFrom)
                }

                else -> {
                    pruneOutput += line
                    outputListener?.onLine(line)
                }
            }
        }
        return ForgetResult(groups.orEmpty(), pruneOutput)
    }
}
