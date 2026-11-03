package io.github.ppuetsch.restic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** Runs the Java examples from docs/module.md so the documentation cannot drift away from the API. */
class DocumentationExamplesTest {
    private static final String SPECIAL_FILE = "mySpecialFile.txt";
    private static final String SPECIAL_CONTENT = "only in one snapshot";

    /** Any Java object that should be backed up. */
    record Settings(String name, int retries) {
    }

    @TempDir
    Path temporaryDirectory;

    private Restic restic;
    private Path source;

    @BeforeEach
    void createRepositoryWithTwoSnapshots() throws Exception {
        String binary = System.getenv("RESTIC_BINARY") != null ? System.getenv("RESTIC_BINARY") : "restic";
        restic = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("repo").toString())
                .password("pw")
                .build());
        restic.init();
        source = Files.createDirectories(temporaryDirectory.resolve("source").resolve("nested"));
        Files.writeString(source.resolve("other.txt"), "always there");
        restic.backup(List.of(source));
        Files.writeString(source.resolve(SPECIAL_FILE), SPECIAL_CONTENT);
        restic.backup(List.of(source));
    }

    @Test
    void printAllLocks() {
        HeldLock heldLock = new HeldLock(restic);
        try {
            List<String> lines = restic.list(ListObjectType.LOCKS).stream()
                    .map(lock -> lock.getId() + ": pid " + lock.getPid() + " on " + lock.getHostname() + " since " + lock.getTime()
                            + (lock.getExclusive() ? " (exclusive)" : ""))
                    .toList();
            lines.forEach(System.out::println);

            assertEquals(1, lines.size());
            assertTrue(lines.getFirst().contains(": pid "));
        } finally {
            heldLock.close();
        }
    }

    @Test
    void iterateAllSnapshots() {
        List<String> lines = restic.snapshots().stream()
                .map(snapshot -> snapshot.getShortId() + " " + snapshot.getTime() + " " + snapshot.getHostname() + " " + snapshot.getPaths())
                .toList();
        lines.forEach(System.out::println);

        assertEquals(2, lines.size());
    }

    @Test
    void snapshotsContainingAFile() {
        List<Snapshot> snapshotsWithFile = restic.find(List.of(SPECIAL_FILE)).stream()
                .map(FindSnapshotMatches::getSnapshot)
                .toList();

        assertEquals(List.of(restic.snapshots().getLast().getId()), snapshotsWithFile.stream().map(Snapshot::getId).toList());
    }

    @Test
    void extractAFileIntoMemory() {
        FindSnapshotMatches hit = restic.find(List.of(SPECIAL_FILE)).getFirst();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        restic.files().dump(hit.getSnapshotId(), hit.getMatches().getFirst().getPath(), buffer);
        String content = buffer.toString(StandardCharsets.UTF_8);

        assertEquals(SPECIAL_CONTENT, content);
    }

    @Test
    void backUpAJavaObject() {
        JsonMapper mapper = JsonMapper.builder().build();
        Settings settings = new Settings("nightly", 3);

        byte[] json = mapper.writeValueAsBytes(settings);
        BackupResult result = restic.backupStandardInput(
                new ByteArrayInputStream(json),
                BackupOptions.builder().stdinFilename("settings.json").tag("settings").build());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        restic.files().dump(result.getSummary().getSnapshotId(), "/settings.json", buffer);
        Settings restored = mapper.readValue(buffer.toByteArray(), Settings.class);

        assertEquals(settings, restored);
    }
}
