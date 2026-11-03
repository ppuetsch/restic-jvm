package io.github.ppuetsch.restic

/** Exit codes according to the restic documentation (chapter "Scripting"). */
internal object ExitCodes {
    const val SOURCE_UNREADABLE: Int = 3
    const val REPOSITORY_NOT_FOUND: Int = 10
    const val REPOSITORY_LOCKED: Int = 11
    const val WRONG_PASSWORD: Int = 12
    const val INTERRUPTED: Int = 130
}

/**
 * Base of all errors of the library. As a sealed hierarchy it can be distinguished exhaustively with `when` in Kotlin;
 * all subclasses are unchecked exceptions.
 */
public sealed class ResticException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** The restic binary could not be started, usually because it is not on the `PATH`. */
public class ResticNotAvailableException(
    message: String,
    cause: Throwable,
) : ResticException(message, cause)

/** A call exceeded [ResticConfiguration.timeout] or `mount` was not ready in time; restic was ended. */
public class ResticTimeoutException
    @JvmOverloads
    constructor(
        message: String,
        cause: Throwable? = null,
    ) : ResticException(message, cause)

/** The call was cancelled through a [CancellationToken]. */
public class ResticCancelledException : ResticException("restic call was cancelled")

/** restic produced output the library could not read as JSON. */
public class ResticOutputException(
    message: String,
    cause: Throwable?,
) : ResticException(message, cause)

/**
 * restic was started but exited with an error exit code. The subclass follows from the exit code.
 *
 * @property exitCode Exit code of the restic process.
 */
public sealed class ResticCommandException(
    public val exitCode: Int,
    standardError: String,
) : ResticException("restic exited with code $exitCode: ${ErrorOutput.readableMessage(standardError)}") {
    /**
     * The error message as plain text, never raw JSON: JSON messages reduced to their text, text lines unchanged,
     * without blank lines.
     */
    public val errorMessage: String = ErrorOutput.readableMessage(standardError)

    /** Rough cause derived from exit code and message, for example to tell network errors from wrong credentials. */
    public val kind: ResticErrorKind = ErrorOutput.classify(exitCode, errorMessage)
}

/** An error exit code without a specific meaning, usually 1; [ResticCommandException.kind] narrows down the cause. */
public class ResticFailedException(
    exitCode: Int,
    standardError: String,
) : ResticCommandException(exitCode, standardError)

/** Exit code 3: a snapshot was created, but some source files could not be read. */
public class SourceUnreadableException(
    standardError: String,
) : ResticCommandException(ExitCodes.SOURCE_UNREADABLE, standardError)

/** Exit code 10: there is no repository at the given location. */
public class RepositoryNotFoundException(
    standardError: String,
) : ResticCommandException(ExitCodes.REPOSITORY_NOT_FOUND, standardError)

/** Exit code 11: another process holds a lock; see [ResticConfiguration.Builder.retryLock] and [MaintenanceCommands.unlock]. */
public class RepositoryLockedException(
    standardError: String,
) : ResticCommandException(ExitCodes.REPOSITORY_LOCKED, standardError)

/** Exit code 12: the password matches no key of the repository. */
public class WrongPasswordException(
    standardError: String,
) : ResticCommandException(ExitCodes.WRONG_PASSWORD, standardError)

/** Exit code 130: restic was interrupted by a signal such as SIGINT. */
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
