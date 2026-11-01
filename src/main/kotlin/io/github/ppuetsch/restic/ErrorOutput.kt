package io.github.ppuetsch.restic

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** Grobe Ursache eines fehlgeschlagenen restic-Aufrufs, abgeleitet aus Exit-Code und Meldung. */
public enum class ResticErrorKind {
    WRONG_PASSWORD,
    REPOSITORY_LOCKED,
    REPOSITORY_NOT_FOUND,
    SOURCE_UNREADABLE,
    INTERRUPTED,

    /** Backend nicht erreichbar: Verbindung verweigert, Host unbekannt, Zeitüberschreitung. */
    NETWORK,

    /** Backend lehnt die Zugangsdaten oder Berechtigung ab. */
    AUTHENTICATION,
    OTHER,
}

private const val EXIT_ERROR_MESSAGE_TYPE: String = "exit_error"

private val NETWORK_HINTS: List<String> =
    listOf(
        "connection refused",
        "connection reset",
        "no such host",
        "i/o timeout",
        "dial tcp",
        "network is unreachable",
        "tls handshake",
        "temporary failure in name resolution",
        "could not resolve host",
    )

private val AUTHENTICATION_HINTS: List<String> =
    listOf(
        "unauthorized",
        "access denied",
        "permission denied (publickey",
        "authentication failed",
        "unable to authenticate",
        "invalid credentials",
        "invalidaccesskeyid",
        "signaturedoesnotmatch",
        "status code: 401",
        "status code: 403",
        "403 forbidden",
        "401 unauthorized",
    )

internal object ErrorOutput {
    private val mapper = ObjectMapper()

    fun readableMessage(standardError: String): String =
        standardError.lineSequence()
            .filter { it.isNotBlank() }
            .joinToString("\n") { line -> exitErrorMessage(line) ?: line.trimEnd() }

    fun classify(
        exitCode: Int,
        message: String,
    ): ResticErrorKind =
        when (exitCode) {
            ExitCodes.SOURCE_UNREADABLE -> ResticErrorKind.SOURCE_UNREADABLE
            ExitCodes.REPOSITORY_NOT_FOUND -> ResticErrorKind.REPOSITORY_NOT_FOUND
            ExitCodes.REPOSITORY_LOCKED -> ResticErrorKind.REPOSITORY_LOCKED
            ExitCodes.WRONG_PASSWORD -> ResticErrorKind.WRONG_PASSWORD
            ExitCodes.INTERRUPTED -> ResticErrorKind.INTERRUPTED
            else -> classifyByMessage(message.lowercase())
        }

    private fun classifyByMessage(lowerCaseMessage: String): ResticErrorKind =
        when {
            AUTHENTICATION_HINTS.any(lowerCaseMessage::contains) -> ResticErrorKind.AUTHENTICATION
            NETWORK_HINTS.any(lowerCaseMessage::contains) -> ResticErrorKind.NETWORK
            else -> ResticErrorKind.OTHER
        }

    private fun exitErrorMessage(line: String): String? {
        val node = parseObjectOrNull(line) ?: return null
        if (node.messageType() != EXIT_ERROR_MESSAGE_TYPE) return null
        return node.textOrNull("message")
    }

    private fun parseObjectOrNull(line: String): JsonNode? {
        if (!line.trimStart().startsWith("{")) return null
        return try {
            mapper.readTree(line)?.takeIf(JsonNode::isObject)
        } catch (notJson: JsonProcessingException) {
            null
        }
    }
}
