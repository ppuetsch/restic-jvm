package io.github.ppuetsch.restic

import tools.jackson.databind.JsonNode

/**
 * Options for [FileCommands.diff] and [FileCommands.diffAll] (`restic diff`).
 *
 * @property metadata Also reports changes of metadata only (`--metadata`).
 * @property cancellationToken Token that cancels the call.
 */
public class DiffOptions private constructor(
    builder: Builder,
) {
    public val metadata: Boolean = builder.metadata
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> = buildList { optionalFlag("--metadata", metadata) }

    /** Builds [DiffOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal var metadata: Boolean = false
        internal var cancellationToken: CancellationToken? = null

        /** Also reports changes of metadata only, such as permissions or timestamps (`--metadata`). */
        @JvmOverloads
        public fun metadata(enabled: Boolean = true): Builder = apply { metadata = enabled }

        /** Token that cancels the call from another thread. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Creates the immutable options. */
        public fun build(): DiffOptions = DiffOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: restic's default behaviour. */
        @JvmField
        public val DEFAULT: DiffOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
