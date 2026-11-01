package io.github.ppuetsch.restic

/** Gemeinsame Stellschrauben von `restic prune` und `restic forget --prune`. */
public class PruneTuning private constructor(
    builder: Builder,
) {
    public val maxUnused: String? = builder.maxUnused
    public val maxRepackSize: String? = builder.maxRepackSize
    public val repackCacheableOnly: Boolean = builder.repackCacheableOnly
    public val repackSmall: Boolean = builder.repackSmall
    public val repackSmallerThan: String? = builder.repackSmallerThan
    public val repackUncompressed: Boolean = builder.repackUncompressed

    internal fun arguments(): List<String> =
        buildList {
            optionalValue("--max-unused", maxUnused)
            optionalValue("--max-repack-size", maxRepackSize)
            optionalFlag("--repack-cacheable-only", repackCacheableOnly)
            optionalFlag("--repack-small", repackSmall)
            optionalValue("--repack-smaller-than", repackSmallerThan)
            optionalFlag("--repack-uncompressed", repackUncompressed)
        }

    public class Builder internal constructor() {
        internal var maxUnused: String? = null
        internal var maxRepackSize: String? = null
        internal var repackCacheableOnly: Boolean = false
        internal var repackSmall: Boolean = false
        internal var repackSmallerThan: String? = null
        internal var repackUncompressed: Boolean = false

        /** Erlaubter Anteil ungenutzter Daten: Bytes mit Suffix (`5G`) oder Prozent (`5%`), `unlimited` erlaubt. */
        public fun maxUnused(limit: String): Builder = apply { maxUnused = limit }

        public fun maxRepackSize(size: String): Builder = apply { maxRepackSize = size }

        @JvmOverloads
        public fun repackCacheableOnly(enabled: Boolean = true): Builder = apply { repackCacheableOnly = enabled }

        /** Seit restic 0.19 veraltet, kleine Packs werden dort immer neu gepackt; siehe [repackSmallerThan]. */
        @JvmOverloads
        public fun repackSmall(enabled: Boolean = true): Builder = apply { repackSmall = enabled }

        public fun repackSmallerThan(size: String): Builder = apply { repackSmallerThan = size }

        @JvmOverloads
        public fun repackUncompressed(enabled: Boolean = true): Builder = apply { repackUncompressed = enabled }

        public fun build(): PruneTuning = PruneTuning(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: PruneTuning = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/** Optionen für `restic prune`. */
public class PruneOptions private constructor(
    builder: Builder,
) {
    public val tuning: PruneTuning = builder.tuning.build()
    public val dryRun: Boolean = builder.dryRun
    public val unsafeRecoverNoFreeSpace: String? = builder.unsafeRecoverNoFreeSpace
    public val cancellationToken: CancellationToken? = builder.cancellationToken

    internal fun arguments(): List<String> =
        buildList {
            addAll(tuning.arguments())
            optionalFlag("--dry-run", dryRun)
            optionalValue("--unsafe-recover-no-free-space", unsafeRecoverNoFreeSpace)
        }

    public class Builder internal constructor() {
        internal val tuning: PruneTuning.Builder = PruneTuning.builder()
        internal var dryRun: Boolean = false
        internal var unsafeRecoverNoFreeSpace: String? = null
        internal var cancellationToken: CancellationToken? = null

        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        public fun maxUnused(limit: String): Builder = apply { tuning.maxUnused(limit) }

        public fun maxRepackSize(size: String): Builder = apply { tuning.maxRepackSize(size) }

        @JvmOverloads
        public fun repackCacheableOnly(enabled: Boolean = true): Builder = apply { tuning.repackCacheableOnly(enabled) }

        @JvmOverloads
        public fun repackSmall(enabled: Boolean = true): Builder = apply { tuning.repackSmall(enabled) }

        public fun repackSmallerThan(size: String): Builder = apply { tuning.repackSmallerThan(size) }

        @JvmOverloads
        public fun repackUncompressed(enabled: Boolean = true): Builder = apply { tuning.repackUncompressed(enabled) }

        /** UNSICHER: siehe restic-Dokumentation, bevor dies genutzt wird. */
        public fun unsafeRecoverNoFreeSpace(argument: String): Builder = apply { unsafeRecoverNoFreeSpace = argument }

        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        public fun build(): PruneOptions = PruneOptions(this)
    }

    public companion object {
        @JvmField
        public val DEFAULT: PruneOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * `restic prune` kennt kein JSON; die Kennzahlen werden aus der Textzusammenfassung gelesen und sind `null`,
 * wenn restic sie nicht ausgibt (z.B. bei einem anderen Wortlaut künftiger Versionen). Größen stammen aus
 * Angaben wie „1.234 KiB“ und sind daher auf die angezeigte Genauigkeit gerundet.
 */
public data class PruneResult
    @JvmOverloads
    constructor(
        val outputLines: List<String>,
        val packsToKeep: Int? = null,
        val packsRepacked: Int? = null,
        val packsDeleted: Int? = null,
        val bytesFreed: Long? = null,
    )

/** Empfängt die Ausgabe eines Befehls zeilenweise, während er läuft. */
public fun interface OutputLineListener {
    /** Eine Zeile der Standardausgabe. */
    public fun onLine(line: String)

    /** Eine Zeile der Fehlerausgabe; standardmäßig ignoriert. */
    public fun onErrorLine(line: String) {}
}
