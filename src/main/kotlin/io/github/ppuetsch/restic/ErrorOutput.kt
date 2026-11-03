package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/** Rough cause of a failed restic call, derived from exit code and message. */
public enum class ResticErrorKind {
    /** The password matches no key of the repository (exit code 12). */
    WRONG_PASSWORD,

    /** Another process holds a lock (exit code 11). */
    REPOSITORY_LOCKED,

    /** There is no repository at the given location (exit code 10). */
    REPOSITORY_NOT_FOUND,

    /** Some source files could not be read (exit code 3). */
    SOURCE_UNREADABLE,

    /** restic was interrupted by a signal (exit code 130). */
    INTERRUPTED,

    /** Backend not reachable: connection refused, unknown host, timeout. */
    NETWORK,

    /** The backend rejects the credentials or the permission. */
    AUTHENTICATION,

    /** None of the other causes applies; details are in [ResticCommandException.errorMessage]. */
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

/** Turns restic's output lines into plain text so raw JSON never leaks out. */
internal object ErrorOutput {
    fun readableMessage(standardError: String): String =
        standardError.lineSequence().mapNotNull(::readableLine).joinToString(LINE_SEPARATOR)

    /**
     * `exit_error` and `error` are reduced to their message, other JSON messages to `message` or their type;
     * text stays unchanged. Blank lines yield `null`.
     */
    fun readableLine(line: String): String? =
        when {
            line.isBlank() -> null
            else -> Json.parseOrNull(line)?.takeIf(JsonNode::isObject)?.let(::readableMessageOf) ?: line.trimEnd()
        }

    private fun readableMessageOf(node: JsonNode): String =
        when (node.messageType()) {
            EXIT_ERROR_MESSAGE_TYPE -> node.text("message")
            ERROR_MESSAGE_TYPE -> errorText(node)
            else -> node.textOrNull("message") ?: "restic message: ${node.messageType().orEmpty()}"
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
        val item = node.textOrNull("item") ?: node.get("error")?.textOrNull("item")
        return readableError(item, message)
    }
}

/** Uniform plain-text form of all of restic's error messages about a file: `path: message`. */
internal fun readableError(
    item: String?,
    message: String,
): String = if (item.isNullOrEmpty()) message else "$item: $message"

/** Forwards stderr lines as plain text to the listener and optionally collects them. */
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
