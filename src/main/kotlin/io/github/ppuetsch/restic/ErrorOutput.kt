package io.github.ppuetsch.restic

import com.fasterxml.jackson.databind.JsonNode

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
private const val ERROR_MESSAGE_TYPE: String = "error"
private const val LINE_SEPARATOR: String = "\n"

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

/** Macht aus restic-Ausgabezeilen Klartext, damit nach außen nie rohes JSON gelangt. */
internal object ErrorOutput {
    fun readableMessage(standardError: String): String =
        standardError.lineSequence().mapNotNull(::readableLine).joinToString(LINE_SEPARATOR)

    /**
     * `exit_error` und `error` werden auf ihre Meldung reduziert, andere JSON-Meldungen auf `message` oder ihren Typ;
     * Text bleibt unverändert. Leerzeilen ergeben `null`.
     */
    fun readableLine(line: String): String? {
        if (line.isBlank()) return null
        val node = Json.parseOrNull(line)?.takeIf(JsonNode::isObject) ?: return line.trimEnd()
        return when (node.messageType()) {
            EXIT_ERROR_MESSAGE_TYPE -> node.text("message")
            ERROR_MESSAGE_TYPE -> errorText(node)
            else -> node.textOrNull("message") ?: "restic message: ${node.messageType().orEmpty()}"
        }
    }

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

    private fun errorText(node: JsonNode): String {
        val message = node.get("error")?.textOrNull("message") ?: node.text("message")
        val item = node.textOrNull("item")?.takeIf(String::isNotEmpty) ?: return message
        return "$item: $message"
    }
}

/** Leitet stderr-Zeilen als Klartext an den Listener weiter und sammelt sie optional. */
internal fun readableErrorLines(
    listener: OutputLineListener?,
    collected: MutableList<String>? = null,
): (String) -> Unit =
    { rawLine ->
        ErrorOutput.readableLine(rawLine)?.let { line ->
            collected?.add(line)
            listener?.onErrorLine(line)
        }
    }
