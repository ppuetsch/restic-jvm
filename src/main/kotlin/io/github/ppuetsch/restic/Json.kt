package io.github.ppuetsch.restic

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.OffsetDateTime

internal object Json {
    private val mapper = ObjectMapper()

    fun parse(text: String): JsonNode =
        try {
            mapper.readTree(text) ?: throw ResticOutputException("Empty output from restic", null)
        } catch (failure: JsonProcessingException) {
            throw ResticOutputException("Cannot parse restic output: $text", failure)
        }
}

internal fun JsonNode.textOrNull(field: String): String? = get(field)?.takeUnless { it.isNull }?.asText()

internal fun JsonNode.text(field: String): String = textOrNull(field) ?: ""

internal fun JsonNode.longOrNull(field: String): Long? = get(field)?.takeUnless { it.isNull }?.asLong()

internal fun JsonNode.long(field: String): Long = longOrNull(field) ?: 0L

internal fun JsonNode.instantOrNull(field: String): Instant? = textOrNull(field)?.let { OffsetDateTime.parse(it).toInstant() }

internal fun JsonNode.textList(field: String): List<String> = get(field)?.map { it.asText() } ?: emptyList()

internal fun JsonNode.messageType(): String? = textOrNull("message_type")

internal fun JsonNode.doubleOrNull(field: String): Double? = get(field)?.takeUnless { it.isNull }?.asDouble()

internal fun JsonNode.booleanOrNull(field: String): Boolean? = get(field)?.takeUnless { it.isNull }?.asBoolean()
