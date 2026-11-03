package io.github.ppuetsch.restic

import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Options for [FileCommands.mount] (`restic mount`, only available on platforms with FUSE, not on Windows).
 *
 * @property filter Selects the mounted snapshots by host, path and tags.
 * @property allowOther Allows other users to access the mount (`--allow-other`).
 * @property noDefaultPermissions Turns off the kernel's permission checks (`--no-default-permissions`).
 * @property ownerRoot Shows root as the owner of all files (`--owner-root`).
 * @property pathTemplates Templates for the directory structure of the snapshots (`--path-template`).
 * @property timeTemplate Time format in the directory names (`--time-template`).
 */
public class MountOptions private constructor(
    builder: Builder,
) {
    public val filter: SnapshotFilter = builder.filter.build()
    public val allowOther: Boolean = builder.allowOther
    public val noDefaultPermissions: Boolean = builder.noDefaultPermissions
    public val ownerRoot: Boolean = builder.ownerRoot
    public val pathTemplates: List<String> = builder.pathTemplates.toList()
    public val timeTemplate: String? = builder.timeTemplate

    internal fun arguments(): List<String> =
        buildList {
            addAll(filter.arguments())
            optionalFlag("--allow-other", allowOther)
            optionalFlag("--no-default-permissions", noDefaultPermissions)
            optionalFlag("--owner-root", ownerRoot)
            pathTemplates.forEach { optionalValue("--path-template", it) }
            optionalValue("--time-template", timeTemplate)
        }

    /** Builds [MountOptions]; every method sets one flag and returns the builder. */
    public class Builder internal constructor() {
        internal val filter: SnapshotFilter.Builder = SnapshotFilter.builder()
        internal var allowOther: Boolean = false
        internal var noDefaultPermissions: Boolean = false
        internal var ownerRoot: Boolean = false
        internal val pathTemplates: MutableList<String> = mutableListOf()
        internal var timeTemplate: String? = null

        /** Mounts only snapshots of this host (`--host`); may be repeated. */
        public fun host(hostname: String): Builder = apply { filter.host(hostname) }

        /** Mounts only snapshots that backed up exactly this path (`--path`); may be repeated. */
        public fun path(absolutePath: String): Builder = apply { filter.path(absolutePath) }

        /** Mounts only snapshots carrying all of these tags (`--tag`); several calls are combined with OR. */
        public fun tag(vararg tags: String): Builder = apply { filter.tag(*tags) }

        /** Erlaubt anderen Benutzern den Zugriff (`--allow-other`); braucht `user_allow_other` in `/etc/fuse.conf`. */
        @JvmOverloads
        public fun allowOther(enabled: Boolean = true): Builder = apply { allowOther = enabled }

        /** Turns off the kernel's permission checks; anyone with access sees all files (`--no-default-permissions`). */
        @JvmOverloads
        public fun noDefaultPermissions(enabled: Boolean = true): Builder = apply { noDefaultPermissions = enabled }

        /** Shows root as the owner of all files (`--owner-root`). */
        @JvmOverloads
        public fun ownerRoot(enabled: Boolean = true): Builder = apply { ownerRoot = enabled }

        /** Template for the directory structure, for example `hosts/%h/%T` (`--path-template`); may be repeated. */
        public fun pathTemplate(template: String): Builder = apply { pathTemplates.add(template) }

        /** Go time format for the directory names, for example `2006-01-02_15-04-05` (`--time-template`). */
        public fun timeTemplate(template: String): Builder = apply { timeTemplate = template }

        /** Creates the immutable options. */
        public fun build(): MountOptions = MountOptions(this)
    }

    /** Default values and entry point to the [Builder]. */
    public companion object {
        /** No flags set: mounts all snapshots. */
        @JvmField
        public val DEFAULT: MountOptions = Builder().build()

        /** A new [Builder] with no flags set. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
