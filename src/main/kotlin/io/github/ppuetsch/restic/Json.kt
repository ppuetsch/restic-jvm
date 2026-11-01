package io.github.ppuetsch.restic

import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.OffsetDateTime

internal object Json {
    private val mapper = JsonMapper.builder().build()

    /** `null` für Zeilen, die kein JSON sind, z.B. Fortschrittstext zwischen den JSON-Zeilen. */
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
     * Die Elemente der JSON-Arrays in der Ausgabe. Andere Zeilen, etwa der Text von `find --show-pack-id` hinter dem
     * Dokument oder `null` für eine leere Liste, werden übergangen; Jackson 3 lehnt Text hinter einem Dokument ab.
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

/** Jackson 3 hat ein eigenes `JsonNode.map`, das Kotlins `map` über die Array-Elemente verdeckt. */
internal fun <T> JsonNode.mapElements(transform: (JsonNode) -> T): List<T> = toList().map(transform)
