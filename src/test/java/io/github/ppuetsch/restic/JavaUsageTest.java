package io.github.ppuetsch.restic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    void versionAndInitAreUsableFromJava(@TempDir Path temporaryDirectory) {
        String binary = System.getenv("RESTIC_BINARY") != null ? System.getenv("RESTIC_BINARY") : "restic";
        Restic restic = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("repo").toString())
                .password("pw")
                .build());

        assertTrue(restic.version().isAtLeast(0, 17, 0));
        InitResult result = restic.init(InitOptions.builder().repositoryVersion("2").build());
        assertEquals(64, result.getId().length());
        assertThrows(ResticFailedException.class, restic::init);
    }

    @Test
    void backupWithListenerIsUsableFromJava(@TempDir Path temporaryDirectory) throws Exception {
        String binary = System.getenv("RESTIC_BINARY") != null ? System.getenv("RESTIC_BINARY") : "restic";
        Restic restic = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("repo").toString())
                .password("pw")
                .build());
        restic.init();
        Path source = Files.createDirectory(temporaryDirectory.resolve("src"));
        Files.writeString(source.resolve("a.txt"), "hello");

        List<BackupEvent> events = new ArrayList<>();
        BackupOptions options = BackupOptions.builder().tag("java").exclude("*.tmp").build();
        BackupResult result = restic.backup(List.of(source), options, events::add);

        assertEquals(1, result.getSummary().getFilesNew());
        assertTrue(events.get(events.size() - 1) instanceof BackupEvent.Summary);
        assertFalse(result.getHasUnreadableSources());
    }
}
