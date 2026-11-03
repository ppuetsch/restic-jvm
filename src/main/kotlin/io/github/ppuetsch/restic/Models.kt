package io.github.ppuetsch.restic

/**
 * Version of the restic binary, as [ToolCommands.version] returns it.
 *
 * @property version Version number, for example `0.19.1`.
 * @property goVersion Go version restic was built with.
 * @property goOs Operating system of the binary, for example `linux` or `windows`.
 * @property goArch Processor architecture of the binary, for example `amd64`.
 */
public data class ResticVersion(
    val version: String,
    val goVersion: String,
    val goOs: String,
    val goArch: String,
) {
    /** `true` if this version is at least `major.minor.patch`; pre-releases count as their base version. */
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
 * Result of [Restic.init].
 *
 * @property id ID of the new repository.
 * @property repository Location of the repository, as restic reports it.
 */
public data class InitResult(
    val id: String,
    val repository: String,
)
