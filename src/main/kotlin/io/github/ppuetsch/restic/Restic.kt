package io.github.ppuetsch.restic

import com.fasterxml.jackson.databind.JsonNode

/** Einstiegspunkt: eine Instanz pro Repository-Konfiguration; thread-safe, da zustandslos. */
public class Restic(public val configuration: ResticConfiguration) {
    private val runner = ProcessRunner(configuration)

    public fun version(): ResticVersion {
        val node = lastJsonLine(listOf("version", "--json"))
        return ResticVersion(node.text("version"), node.text("go_version"), node.text("go_os"), node.text("go_arch"))
    }

    @JvmOverloads
    public fun init(options: InitOptions = InitOptions.DEFAULT): InitResult {
        val node = lastJsonLine(listOf("init", "--json") + options.arguments(), options.environment())
        return InitResult(node.text("id"), node.text("repository"))
    }

    @JvmSynthetic
    public fun init(configure: InitOptions.Builder.() -> Unit): InitResult = init(InitOptions.builder().apply(configure).build())

    private fun lastJsonLine(
        arguments: List<String>,
        environment: Map<String, String> = emptyMap(),
    ): JsonNode {
        var lastLine = ""
        runner.runChecked(arguments, environment) { line -> if (line.isNotBlank()) lastLine = line }
        return Json.parse(lastLine)
    }
}
