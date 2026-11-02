package io.github.ppuetsch.restic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** Führt die Java-Beispiele aus docs/module.md aus, damit die Doku nicht von der API abweicht. */
class DocumentationExamplesTest {
    private static final String SPECIAL_FILE = "mySpecialFile.txt";
    private static final String SPECIAL_CONTENT = "nur in einem Snapshot";

    /** Ein beliebiges Java-Objekt, das gesichert werden soll. */
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
        Files.writeString(source.resolve("other.txt"), "immer da");
        restic.backup(List.of(source));
        Files.writeString(source.resolve(SPECIAL_FILE), SPECIAL_CONTENT);
        restic.backup(List.of(source));
    }

    @Test
    void printAllLocks() {
        List<String> printed = new ArrayList<>();

        for (String lockId : restic.list(ListObjectType.LOCKS)) {
            RepositoryLock lock = restic.catLock(lockId);
            printed.add(lockId + ": pid " + lock.getPid() + " on " + lock.getHostname() + " since " + lock.getTime()
                    + (lock.getExclusive() ? " (exclusive)" : ""));
        }

        assertTrue(printed.isEmpty(), "an idle repository holds no locks");
    }

    @Test
    void iterateAllSnapshots() {
        List<String> printed = new ArrayList<>();

        for (Snapshot snapshot : restic.snapshots()) {
            printed.add(snapshot.getShortId() + " " + snapshot.getTime() + " " + snapshot.getHostname() + " " + snapshot.getPaths());
        }

        assertEquals(2, printed.size());
    }

    @Test
    void snapshotsContainingAFile() {
        Set<String> snapshotIds = restic.find(List.of(SPECIAL_FILE)).stream()
                .map(FindSnapshotMatches::getSnapshotId)
                .collect(Collectors.toSet());
        List<Snapshot> snapshotsWithFile = restic.snapshots().stream()
                .filter(snapshot -> snapshotIds.contains(snapshot.getId()))
                .toList();

        assertEquals(1, snapshotsWithFile.size());
        assertEquals(restic.snapshots().get(1).getId(), snapshotsWithFile.get(0).getId());
    }

    @Test
    void extractAFileIntoMemory() {
        FindSnapshotMatches hit = restic.find(List.of(SPECIAL_FILE)).get(0);
        String pathInSnapshot = hit.getMatches().get(0).getPath();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        restic.dump(hit.getSnapshotId(), pathInSnapshot, buffer);
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
        restic.dump(result.getSummary().getSnapshotId(), "/settings.json", buffer);
        Settings restored = mapper.readValue(buffer.toByteArray(), Settings.class);

        assertEquals(settings, restored);
    }
}
