package io.github.ppuetsch.restic

import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.OffsetDateTime

internal object Json {
    private val mapper = JsonMapper.builder().build()

    /** `null` for lines that are not JSON, for example progress text between the JSON lines. */
    fun parseOrNull(text: String): JsonNode? =
        if (text.isBlank()) {
            null
        } else {
            try {
                mapper.readTree(text)
            } catch (ignoredNotJson: JacksonException) {
                null
            }
        }

    /**
     * The elements of the JSON arrays in the output. Other lines, such as the text `find --show-pack-id` prints after the
     * document or `null` for an empty list, are skipped; Jackson 3 rejects text after a document.
     */
    fun arrayElements(lines: List<String>): List<JsonNode> =
        lines.mapNotNull(::parseOrNull).filter(JsonNode::isArray).flatMap(JsonNode::toList)

    fun parse(text: String): JsonNode =
        try {
            mapper.readTree(text) ?: throw ResticOutputException("Empty output from restic", null)
        } catch (failure: JacksonException) {
            throw ResticOutputException("Cannot parse restic output: $text", failure)
        }
}

internal fun JsonNode.textOrNull(field: String): String? = get(field)?.takeUnless { it.isNull }?.asString()

internal fun JsonNode.text(field: String): String = textOrNull(field).orEmpty()

internal fun JsonNode.longOrNull(field: String): Long? = get(field)?.takeUnless { it.isNull }?.asLong()

internal fun JsonNode.long(field: String): Long = longOrNull(field) ?: 0L

internal fun JsonNode.instantOrNull(field: String): Instant? = textOrNull(field)?.let { OffsetDateTime.parse(it).toInstant() }

internal fun JsonNode.textList(field: String): List<String> = get(field)?.mapElements { it.asString() }.orEmpty()

internal fun JsonNode.messageType(): String? = textOrNull("message_type")

internal fun JsonNode.doubleOrNull(field: String): Double? = get(field)?.takeUnless { it.isNull }?.asDouble()

internal fun JsonNode.booleanOrNull(field: String): Boolean? = get(field)?.takeUnless { it.isNull }?.asBoolean()

/** Jackson 3 has its own `JsonNode.map`, which hides Kotlin's `map` over the array elements. */
internal fun <T> JsonNode.mapElements(transform: (JsonNode) -> T): List<T> = toList().map(transform)
