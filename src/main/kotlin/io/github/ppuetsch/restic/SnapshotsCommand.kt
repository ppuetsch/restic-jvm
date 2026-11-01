package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

internal class SnapshotsCommand(
    private val runner: ProcessRunner,
) {
    fun snapshots(options: SnapshotsOptions): List<Snapshot> {
        val document = readDocument(options.arguments())
        val isGrouped = document.firstOrNull()?.has("group_key") == true
        return if (isGrouped) document.flatMap { snapshotGroupFrom(it).snapshots } else document.map(::snapshotFrom)
    }

    fun snapshotGroups(options: SnapshotsOptions): List<SnapshotGroup> {
        val defaultGrouping = if (options.groupBy == null) listOf("--group-by", DEFAULT_GROUPING) else emptyList()
        return readDocument(options.arguments() + defaultGrouping).map(::snapshotGroupFrom)
    }

    private fun readDocument(optionArguments: List<String>): List<JsonNode> {
        val lines = mutableListOf<String>()
        runner.runChecked(listOf("snapshots", "--json") + optionArguments) { lines += it }
        return Json.arrayElements(lines)
    }

    private companion object {
        const val DEFAULT_GROUPING: String = "host,paths"
    }
}
