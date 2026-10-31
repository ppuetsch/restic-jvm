package io.github.ppuetsch.restic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Nutzt die API wie ein Java-Fremdnutzer: nur öffentliche Typen, keine Kotlin-Besonderheiten. */
class JavaUsageTest {
    @Test
    void configurationBuilderIsUsableFromJava() {
        ResticConfiguration configuration = ResticConfiguration.builder()
                .binary("restic")
                .repository("sftp:user@host:/backups")
                .passwordFile(Path.of("password.txt"))
                .environment("RESTIC_COMPRESSION", "max")
                .timeout(Duration.ofMinutes(5))
                .build();

        assertEquals("sftp:user@host:/backups", configuration.getRepository());
        assertEquals("restic", configuration.getBinary());
    }
}
