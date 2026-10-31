package io.github.ppuetsch.restic

public data class ResticVersion(
    val version: String,
    val goVersion: String,
    val goOs: String,
    val goArch: String,
) {
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

public data class InitResult(
    val id: String,
    val repository: String,
)
