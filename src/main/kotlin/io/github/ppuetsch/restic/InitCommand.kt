package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/** `restic init` and `restic version`: both answer with a single JSON line at the end of their output. */
internal class InitCommand(
    private val runner: ProcessRunner,
) {
    fun init(options: InitOptions): InitResult {
        val node = lastJsonLine(ProcessRequest(listOf("init", "--json") + options.arguments(), options.environment()))
        return InitResult(node.text("id"), node.text("repository"))
    }

    fun version(): ResticVersion {
        val node = lastJsonLine(ProcessRequest(listOf("version", "--json")))
        return ResticVersion(node.text("version"), node.text("go_version"), node.text("go_os"), node.text("go_arch"))
    }

    private fun lastJsonLine(request: ProcessRequest): JsonNode {
        var lastLine = ""
        runner.runChecked(request) { line -> if (line.isNotBlank()) lastLine = line }
        return Json.parse(lastLine)
    }
}
