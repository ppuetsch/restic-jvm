package io.github.ppuetsch.restic

/**
 * Version der restic-Binary, wie [Restic.version] sie liefert.
 *
 * @property version Versionsnummer, z.B. `0.19.1`.
 * @property goVersion Go-Version, mit der restic gebaut wurde.
 * @property goOs Betriebssystem der Binary, z.B. `linux` oder `windows`.
 * @property goArch Prozessorarchitektur der Binary, z.B. `amd64`.
 */
public data class ResticVersion(
    val version: String,
    val goVersion: String,
    val goOs: String,
    val goArch: String,
) {
    /** `true`, wenn diese Version mindestens `major.minor.patch` ist; Vorabversionen zählen wie ihre Basisversion. */
    public fun isAtLeast(
        major: Int,
        minor: Int,
        patch: Int = 0,
    ): Boolean {
        val actual = (numericParts() + listOf(0, 0, 0)).take(3)
        val required = listOf(major, minor, patch)
        val firstDifference = actual.zip(required).firstOrNull { (have, want) -> have != want }
        return firstDifference == null || firstDifference.first > firstDifference.second
    }

    private fun numericParts(): List<Int> = version.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }
}

/**
 * Ergebnis von [Restic.init].
 *
 * @property id ID des neuen Repositorys.
 * @property repository Ort des Repositorys, wie restic ihn meldet.
 */
public data class InitResult(
    val id: String,
    val repository: String,
)
