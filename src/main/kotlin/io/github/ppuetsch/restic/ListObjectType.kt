package io.github.ppuetsch.restic

import java.time.Instant

/**
 * The kinds of objects [Restic.list] can enumerate; the type parameter is what the list contains.
 *
 * ```java
 * restic.list(ListObjectType.LOCKS).forEach(lock -> System.out.println(lock.getId() + " held by pid " + lock.getPid()));
 * ```
 */
public class ListObjectType<T : Any> private constructor(
    private val name: String,
    internal val listWith: (ProcessRunner) -> List<T>,
) {
    /** The kind's name, for example `LOCKS`. */
    override fun toString(): String = name

    /** The available kinds. */
    public companion object {
        /** Pack files holding the actual data. */
        @JvmField
        public val PACKS: ListObjectType<Pack> = ListObjectType("PACKS") { runner -> listEntries(runner, "packs").map(::Pack) }

        /** Index files; their content is read on first access. */
        @JvmField
        public val INDEX: ListObjectType<Index> =
            ListObjectType("INDEX") { runner -> listEntries(runner, "index").map { indexIn(runner, it) } }

        /**
         * All snapshots, oldest first and complete: unlike the other kinds this reads every snapshot in a single
         * `restic snapshots` call, which is far cheaper than one `restic cat` per snapshot. Same as [Restic.snapshots].
         */
        @JvmField
        public val SNAPSHOTS: ListObjectType<Snapshot> =
            ListObjectType("SNAPSHOTS") { runner -> SnapshotsCommand(runner).snapshots(SnapshotsOptions.DEFAULT) }

        /** Key files; their content is read on first access. */
        @JvmField
        public val KEYS: ListObjectType<Key> = ListObjectType("KEYS") { runner -> listEntries(runner, "keys").map { keyIn(runner, it) } }

        /** Locks; their content is read on first access. A repository nobody is working on usually has none. */
        @JvmField
        public val LOCKS: ListObjectType<Lock> =
            ListObjectType("LOCKS") { runner -> listEntries(runner, "locks").map { lockIn(runner, it) } }

        /** All blobs with their type. */
        @JvmField
        public val BLOBS: ListObjectType<Blob> =
            ListObjectType("BLOBS") { runner ->
                listEntries(runner, "blobs").map { line ->
                    val typeAndId = line.split(' ')
                    Blob(id = typeAndId.getOrElse(1) { "" }, type = typeAndId[0])
                }
            }
    }
}

private fun listEntries(
    runner: ProcessRunner,
    cliType: String,
): List<String> {
    val lines = mutableListOf<String>()
    runner.runChecked(listOf("list", cliType)) { line -> line.trim().takeIf(String::isNotEmpty)?.let(lines::add) }
    return lines
}
