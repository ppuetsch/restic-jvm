package io.github.ppuetsch.restic

import java.nio.file.Path
import java.util.Locale

private const val CACHE_DIRECTORY_NAME: String = "restic-jvm"

/** The platforms a `restic-jvm-binary-*` artifact exists for, keyed by operating system and architecture family. */
private val PLATFORM_BY_OS_AND_ARCHITECTURE: Map<Pair<String, String>, String> =
    mapOf(
        ("linux" to "x86_64") to "linux-x86_64",
        ("linux" to "aarch64") to "linux-aarch64",
        ("windows" to "x86_64") to "windows-x86_64",
        ("macos" to "x86_64") to "macos-x86_64",
        ("macos" to "aarch64") to "macos-aarch64",
    )

/** The machine the JVM runs on, in the terms of the `restic-jvm-binary-*` artifacts. */
internal object HostPlatform {
    /** For example `linux-x86_64`; `null` if no binary artifact exists for this platform. */
    fun current(
        osName: String = System.getProperty("os.name"),
        osArchitecture: String = System.getProperty("os.arch"),
    ): String? = PLATFORM_BY_OS_AND_ARCHITECTURE[operatingSystemFamily(osName) to architectureFamily(osArchitecture)]

    /** Where binaries are extracted unless [ResticConfiguration.Builder.bundledBinaryDirectory] says otherwise. */
    fun defaultCacheDirectory(): Path {
        val userHome = Path.of(System.getProperty("user.home"))
        val cacheRoot =
            when (operatingSystemFamily(System.getProperty("os.name"))) {
                "windows" -> System.getenv("LOCALAPPDATA")?.let(Path::of) ?: userHome.resolve("AppData/Local")
                "macos" -> userHome.resolve("Library/Caches")
                else -> System.getenv("XDG_CACHE_HOME")?.let(Path::of) ?: userHome.resolve(".cache")
            }
        return cacheRoot.resolve(CACHE_DIRECTORY_NAME)
    }

    private fun operatingSystemFamily(osName: String): String {
        val name = osName.lowercase(Locale.ROOT)
        return when {
            name.startsWith("windows") -> "windows"
            name.startsWith("mac") || name.startsWith("darwin") -> "macos"
            else -> name
        }
    }

    private fun architectureFamily(osArchitecture: String): String =
        when (osArchitecture.lowercase(Locale.ROOT)) {
            "amd64", "x86_64", "x64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> osArchitecture
        }
}
