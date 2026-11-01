package io.github.ppuetsch.restic

/** Exit-Codes laut restic-Dokumentation (Kapitel „Scripting“). */
internal object ExitCodes {
    const val SOURCE_UNREADABLE: Int = 3
    const val REPOSITORY_NOT_FOUND: Int = 10
    const val REPOSITORY_LOCKED: Int = 11
    const val WRONG_PASSWORD: Int = 12
    const val INTERRUPTED: Int = 130
}

public sealed class ResticException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Die restic-Binary konnte nicht gestartet werden. */
public class ResticNotAvailableException(message: String, cause: Throwable) : ResticException(message, cause)

public class ResticTimeoutException(message: String) : ResticException(message)

/** Der Aufruf wurde über ein [CancellationToken] abgebrochen. */
public class ResticCancelledException : ResticException("restic call was cancelled")

/** restic hat Ausgabe geliefert, die die Library nicht als JSON lesen konnte. */
public class ResticOutputException(message: String, cause: Throwable?) : ResticException(message, cause)

/** restic wurde gestartet, beendete sich aber mit einem Fehler-Exit-Code. */
public sealed class ResticCommandException(
    public val exitCode: Int,
    standardError: String,
) : ResticException("restic exited with code $exitCode: ${ErrorOutput.readableMessage(standardError)}") {
    /** Die Fehlermeldung als Klartext: bei `exit_error`-Zeilen deren `message`, andere Zeilen unverändert, ohne Leerzeilen. */
    public val errorMessage: String = ErrorOutput.readableMessage(standardError)

    public val kind: ResticErrorKind = ErrorOutput.classify(exitCode, errorMessage)
}

public class ResticFailedException(exitCode: Int, standardError: String) : ResticCommandException(exitCode, standardError)

/** Exit-Code 3: Ein Snapshot wurde erzeugt, einzelne Quelldateien waren aber nicht lesbar. */
public class SourceUnreadableException(standardError: String) :
    ResticCommandException(ExitCodes.SOURCE_UNREADABLE, standardError)

public class RepositoryNotFoundException(standardError: String) :
    ResticCommandException(ExitCodes.REPOSITORY_NOT_FOUND, standardError)

public class RepositoryLockedException(standardError: String) :
    ResticCommandException(ExitCodes.REPOSITORY_LOCKED, standardError)

public class WrongPasswordException(standardError: String) :
    ResticCommandException(ExitCodes.WRONG_PASSWORD, standardError)

public class ResticInterruptedException(standardError: String) :
    ResticCommandException(ExitCodes.INTERRUPTED, standardError)

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
