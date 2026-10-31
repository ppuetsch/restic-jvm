package io.github.ppuetsch.restic

import java.nio.file.Path

internal val resticBinaryForTests: String = System.getenv("RESTIC_BINARY") ?: DEFAULT_RESTIC_BINARY

internal const val TEST_PASSWORD: String = "test-password"

internal fun resticFor(
    repository: Path,
    configure: ResticConfiguration.Builder.() -> Unit = {},
): Restic =
    Restic(
        ResticConfiguration.builder()
            .binary(resticBinaryForTests)
            .repository(repository.toString())
            .password(TEST_PASSWORD)
            .apply(configure)
            .build(),
    )
