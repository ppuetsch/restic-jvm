package io.github.ppuetsch.restic

/** Exit-Codes laut restic-Dokumentation (Kapitel „Scripting“). */
internal object ExitCodes {
    const val SOURCE_UNREADABLE: Int = 3
    const val REPOSITORY_NOT_FOUND: Int = 10
    const val REPOSITORY_LOCKED: Int = 11
    const val WRONG_PASSWORD: Int = 12
    const val INTERRUPTED: Int = 130
}

/**
 * Basis aller Fehler der Library. Als versiegelte Hierarchie lässt sie sich in Kotlin per `when` vollständig
 * unterscheiden; alle Unterklassen sind ungeprüfte Exceptions.
 */
public sealed class ResticException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Die restic-Binary konnte nicht gestartet werden, meist weil sie nicht im `PATH` liegt. */
public class ResticNotAvailableException(
    message: String,
    cause: Throwable,
) : ResticException(message, cause)

/** Ein Aufruf hat [ResticConfiguration.timeout] überschritten oder `mount` war nicht rechtzeitig bereit; restic wurde beendet. */
public class ResticTimeoutException
    @JvmOverloads
    constructor(
        message: String,
        cause: Throwable? = null,
    ) : ResticException(message, cause)

/** Der Aufruf wurde über ein [CancellationToken] abgebrochen. */
public class ResticCancelledException : ResticException("restic call was cancelled")

/** restic hat Ausgabe geliefert, die die Library nicht als JSON lesen konnte. */
public class ResticOutputException(
    message: String,
    cause: Throwable?,
) : ResticException(message, cause)

/**
 * restic wurde gestartet, beendete sich aber mit einem Fehler-Exit-Code. Die Unterklasse folgt aus dem Exit-Code.
 *
 * @property exitCode Exit-Code des restic-Prozesses.
 */
public sealed class ResticCommandException(
    public val exitCode: Int,
    standardError: String,
) : ResticException("restic exited with code $exitCode: ${ErrorOutput.readableMessage(standardError)}") {
    /**
     * Die Fehlermeldung als Klartext, nie rohes JSON: JSON-Meldungen auf ihren Text reduziert, Textzeilen unverändert,
     * ohne Leerzeilen.
     */
    public val errorMessage: String = ErrorOutput.readableMessage(standardError)

    /** Grobe Ursache aus Exit-Code und Meldung, etwa um Netzwerkfehler von falschen Zugangsdaten zu unterscheiden. */
    public val kind: ResticErrorKind = ErrorOutput.classify(exitCode, errorMessage)
}

/** Ein Fehler-Exit-Code ohne eigene Bedeutung, meist 1; [ResticCommandException.kind] grenzt die Ursache ein. */
public class ResticFailedException(
    exitCode: Int,
    standardError: String,
) : ResticCommandException(exitCode, standardError)

/** Exit-Code 3: Ein Snapshot wurde erzeugt, einzelne Quelldateien waren aber nicht lesbar. */
public class SourceUnreadableException(
    standardError: String,
) : ResticCommandException(ExitCodes.SOURCE_UNREADABLE, standardError)

/** Exit-Code 10: Unter der angegebenen Adresse liegt kein Repository. */
public class RepositoryNotFoundException(
    standardError: String,
) : ResticCommandException(ExitCodes.REPOSITORY_NOT_FOUND, standardError)

/** Exit-Code 11: Ein anderer Prozess hält eine Sperre; siehe [ResticConfiguration.Builder.retryLock] und [Restic.unlock]. */
public class RepositoryLockedException(
    standardError: String,
) : ResticCommandException(ExitCodes.REPOSITORY_LOCKED, standardError)

/** Exit-Code 12: Das Passwort passt zu keinem Schlüssel des Repositorys. */
public class WrongPasswordException(
    standardError: String,
) : ResticCommandException(ExitCodes.WRONG_PASSWORD, standardError)

/** Exit-Code 130: restic wurde durch ein Signal wie SIGINT unterbrochen. */
public class ResticInterruptedException(
    standardError: String,
) : ResticCommandException(ExitCodes.INTERRUPTED, standardError)

internal fun commandExceptionFor(
    exitCode: Int,
    standardError: String,
): ResticCommandException =
    when (exitCode) {
        ExitCodes.SOURCE_UNREADABLE -> SourceUnreadableException(standardError)
        ExitCodes.REPOSITORY_NOT_FOUND -> RepositoryNotFoundException(standardError)
        ExitCodes.REPOSITORY_LOCKED -> RepositoryLockedException(standardError)
        ExitCodes.WRONG_PASSWORD -> WrongPasswordException(standardError)
        ExitCodes.INTERRUPTED -> ResticInterruptedException(standardError)
        else -> ResticFailedException(exitCode, standardError)
    }
