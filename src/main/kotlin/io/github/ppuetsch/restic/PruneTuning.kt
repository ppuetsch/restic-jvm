package io.github.ppuetsch.restic

/**
 * Gemeinsame Stellschrauben von `restic prune` und `restic forget --prune`: Sie wägen freigegebenen Speicher gegen
 * die Menge neu zu packender Daten ab.
 *
 * @property maxUnused Erlaubter Anteil ungenutzter Daten (`--max-unused`).
 * @property maxRepackSize Obergrenze neu zu packender Daten (`--max-repack-size`).
 * @property repackCacheableOnly Packt nur Packs mit Tree-Blobs neu (`--repack-cacheable-only`).
 * @property repackSmall Packt kleine Packs neu (`--repack-small`), seit restic 0.19 ohne Wirkung.
 * @property repackSmallerThan Packt Packs unter dieser Größe neu (`--repack-smaller-than`).
 * @property repackUncompressed Komprimiert bisher unkomprimierte Daten nach (`--repack-uncompressed`).
 */
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

    /** Stellt [PruneTuning] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal var maxUnused: String? = null
        internal var maxRepackSize: String? = null
        internal var repackCacheableOnly: Boolean = false
        internal var repackSmall: Boolean = false
        internal var repackSmallerThan: String? = null
        internal var repackUncompressed: Boolean = false

        /** Erlaubter Anteil ungenutzter Daten: Bytes mit Suffix (`5G`) oder Prozent (`5%`), `unlimited` erlaubt. */
        public fun maxUnused(limit: String): Builder = apply { maxUnused = limit }

        /** Obergrenze der neu zu packenden Daten, Größe mit Suffix wie `10G` (`--max-repack-size`). */
        public fun maxRepackSize(size: String): Builder = apply { maxRepackSize = size }

        /** Packt nur Packs mit Tree-Blobs neu, die im Cache liegen (`--repack-cacheable-only`); spart Downloads. */
        @JvmOverloads
        public fun repackCacheableOnly(enabled: Boolean = true): Builder = apply { repackCacheableOnly = enabled }

        /** Seit restic 0.19 veraltet, kleine Packs werden dort immer neu gepackt; siehe [repackSmallerThan]. */
        @JvmOverloads
        public fun repackSmall(enabled: Boolean = true): Builder = apply { repackSmall = enabled }

        /** Packt Packs unter dieser Größe neu, z.B. `5M` (`--repack-smaller-than`). */
        public fun repackSmallerThan(size: String): Builder = apply { repackSmallerThan = size }

        /** Komprimiert bisher unkomprimierte Daten nach, etwa nach der Migration auf Format 2 (`--repack-uncompressed`). */
        @JvmOverloads
        public fun repackUncompressed(enabled: Boolean = true): Builder = apply { repackUncompressed = enabled }

        /** Erzeugt die unveränderlichen Stellschrauben. */
        public fun build(): PruneTuning = PruneTuning(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: restic-Standardverhalten. */
        @JvmField
        public val DEFAULT: PruneTuning = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * Optionen für [Restic.prune] (`restic prune`).
 *
 * @property tuning Abwägung zwischen freigegebenem Speicher und neu zu packenden Daten.
 * @property dryRun Nur zeigen, was geschehen würde (`--dry-run`).
 * @property unsafeRecoverNoFreeSpace Notfallmodus für ein volles Backend (`--unsafe-recover-no-free-space`).
 * @property cancellationToken Token, über das sich der Aufruf abbrechen lässt.
 */
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

    /** Stellt [PruneOptions] zusammen; jede Methode setzt ein Flag und gibt den Builder zurück. */
    public class Builder internal constructor() {
        internal val tuning: PruneTuning.Builder = PruneTuning.builder()
        internal var dryRun: Boolean = false
        internal var unsafeRecoverNoFreeSpace: String? = null
        internal var cancellationToken: CancellationToken? = null

        /** Zeigt nur, was entfernt und neu gepackt würde (`--dry-run`). */
        @JvmOverloads
        public fun dryRun(enabled: Boolean = true): Builder = apply { dryRun = enabled }

        /** Erlaubter Anteil ungenutzter Daten, siehe [PruneTuning.Builder.maxUnused]. */
        public fun maxUnused(limit: String): Builder = apply { tuning.maxUnused(limit) }

        /** Obergrenze neu zu packender Daten, siehe [PruneTuning.Builder.maxRepackSize]. */
        public fun maxRepackSize(size: String): Builder = apply { tuning.maxRepackSize(size) }

        /** Packt nur cachebare Packs neu, siehe [PruneTuning.Builder.repackCacheableOnly]. */
        @JvmOverloads
        public fun repackCacheableOnly(enabled: Boolean = true): Builder = apply { tuning.repackCacheableOnly(enabled) }

        /** Seit restic 0.19 veraltet, siehe [PruneTuning.Builder.repackSmall]. */
        @JvmOverloads
        public fun repackSmall(enabled: Boolean = true): Builder = apply { tuning.repackSmall(enabled) }

        /** Packt Packs unter dieser Größe neu, siehe [PruneTuning.Builder.repackSmallerThan]. */
        public fun repackSmallerThan(size: String): Builder = apply { tuning.repackSmallerThan(size) }

        /** Komprimiert unkomprimierte Daten nach, siehe [PruneTuning.Builder.repackUncompressed]. */
        @JvmOverloads
        public fun repackUncompressed(enabled: Boolean = true): Builder = apply { tuning.repackUncompressed(enabled) }

        /** UNSICHER: siehe restic-Dokumentation, bevor dies genutzt wird. */
        public fun unsafeRecoverNoFreeSpace(argument: String): Builder = apply { unsafeRecoverNoFreeSpace = argument }

        /** Token, über das sich der Aufruf von einem anderen Thread aus abbrechen lässt. */
        public fun cancellationToken(token: CancellationToken): Builder = apply { cancellationToken = token }

        /** Erzeugt die unveränderlichen Optionen. */
        public fun build(): PruneOptions = PruneOptions(this)
    }

    /** Standardwerte und Einstieg in den [Builder]. */
    public companion object {
        /** Keine Flags gesetzt: restic-Standardverhalten. */
        @JvmField
        public val DEFAULT: PruneOptions = Builder().build()

        /** Ein neuer [Builder] ohne gesetzte Flags. */
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

/**
 * `restic prune` kennt kein JSON; die Kennzahlen werden aus der Textzusammenfassung gelesen und sind `null`,
 * wenn restic sie nicht ausgibt (z.B. bei einem anderen Wortlaut künftiger Versionen). Größen stammen aus
 * Angaben wie „1.234 KiB“ und sind daher auf die angezeigte Genauigkeit gerundet.
 *
 * @property outputLines Die vollständige Textausgabe.
 * @property packsToKeep Packs, die unverändert bleiben.
 * @property packsRepacked Packs, die neu gepackt wurden.
 * @property packsDeleted Packs, die gelöscht wurden.
 * @property bytesFreed Freigegebener Speicher in Bytes.
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
    public fun onErrorLine(line: String) {
        // Absichtlich leer: Wer nur die Standardausgabe braucht, implementiert allein onLine.
    }
}
