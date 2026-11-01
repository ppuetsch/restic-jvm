package io.github.ppuetsch.restic

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal val resticBinaryForTests: String = System.getenv("RESTIC_BINARY") ?: DEFAULT_RESTIC_BINARY

internal const val TEST_PASSWORD: String = "test-password"

internal fun resticFor(
    repository: Path,
    configure: ResticConfiguration.Builder.() -> Unit = {},
): Restic =
    Restic(
        ResticConfiguration
            .builder()
            .binary(resticBinaryForTests)
            .repository(repository.toString())
            .password(TEST_PASSWORD)
            .apply(configure)
            .build(),
    )

/** Ein frisches lokales Repository samt Quellverzeichnis mit drei Dateien (`a.txt`, `debug.log`, `sub/b.txt`). */
internal class RepositoryFixture(
    workDirectory: Path,
) {
    val source: Path = workDirectory.resolve("source").createDirectories()
    val repository: Path = workDirectory.resolve("repo")
    val restic: Restic = resticFor(repository)

    init {
        source.resolve("a.txt").writeText("hello")
        source.resolve("debug.log").writeText("noise")
        source
            .resolve("sub")
            .createDirectories()
            .resolve("b.txt")
            .writeText("world")
        restic.init()
    }

    fun backup(configure: BackupOptions.Builder.() -> Unit = {}): BackupSummary =
        restic
            .backup(
                listOf(source),
                configure = configure,
            ).summary
}
