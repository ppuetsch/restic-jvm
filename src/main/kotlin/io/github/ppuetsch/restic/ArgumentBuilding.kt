package io.github.ppuetsch.restic

internal fun MutableList<String>.optionalValue(
    name: String,
    value: Any?,
) {
    if (value != null) addAll(listOf(name, value.toString()))
}

internal fun MutableList<String>.optionalFlag(
    name: String,
    enabled: Boolean,
) {
    if (enabled) add(name)
}
