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

    @Test
    void snapshotsLsAndFindAreUsableFromJava(@TempDir Path temporaryDirectory) throws Exception {
        String binary = System.getenv("RESTIC_BINARY") != null ? System.getenv("RESTIC_BINARY") : "restic";
        Restic restic = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("repo").toString())
                .password("pw")
                .build());
        restic.init();
        Path source = Files.createDirectory(temporaryDirectory.resolve("src"));
        Files.writeString(source.resolve("a.txt"), "hello");
        restic.backup(List.of(source), BackupOptions.builder().tag("java", "x").build());

        List<Snapshot> snapshots = restic.snapshots(SnapshotsOptions.builder().tag("java").latest(1).build());
        assertEquals(1, snapshots.size());

        List<String> names = new ArrayList<>();
        restic.ls(Restic.LATEST, LsOptions.builder().recursive().build(), node -> names.add(node.getName()));
        assertTrue(names.contains("a.txt"));

        List<FindSnapshotMatches> found = restic.find(List.of("*.txt"));
        assertEquals(1, found.get(0).getHits());
    }

    @Test
    void restoreDumpAndCatAreUsableFromJava(@TempDir Path temporaryDirectory) throws Exception {
        String binary = System.getenv("RESTIC_BINARY") != null ? System.getenv("RESTIC_BINARY") : "restic";
        Restic restic = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("repo").toString())
                .password("pw")
                .build());
        restic.init();
        Path source = Files.createDirectory(temporaryDirectory.resolve("src"));
        Files.writeString(source.resolve("a.txt"), "hello");
        restic.backup(List.of(source));

        String filePathInSnapshot = restic.listNodes(Restic.LATEST, LsOptions.builder().recursive().build())
                .getNodes().stream().filter(node -> node.getName().equals("a.txt")).findFirst().orElseThrow().getPath();
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        restic.dump(Restic.LATEST, filePathInSnapshot, output);
        assertEquals("hello", output.toString(java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(restic.catText(CatObjectType.CONFIG).contains("chunker_polynomial"));

        RestoreSummary summary = restic.restore(
                Restic.LATEST + ":" + filePathInSnapshot.substring(0, filePathInSnapshot.lastIndexOf('/')),
                temporaryDirectory.resolve("out"),
                RestoreOptions.builder().overwrite(OverwriteBehavior.ALWAYS).verify().build(),
                event -> { });
        assertEquals(1, summary.getFilesRestored());
    }

    @Test
    void forgetPruneCheckAndStatsAreUsableFromJava(@TempDir Path temporaryDirectory) throws Exception {
        String binary = System.getenv("RESTIC_BINARY") != null ? System.getenv("RESTIC_BINARY") : "restic";
        Restic restic = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("repo").toString())
                .password("pw")
                .build());
        restic.init();
        Path source = Files.createDirectory(temporaryDirectory.resolve("src"));
        Files.writeString(source.resolve("a.txt"), "one");
        restic.backup(List.of(source));
        Files.writeString(source.resolve("a.txt"), "two");
        restic.backup(List.of(source));

        ForgetResult forgotten = restic.forget(ForgetOptions.builder().keepLast(1).dryRun().build());
        assertEquals(1, forgotten.removedSnapshots().size());
        restic.forget(ForgetOptions.builder().keepLast(1).build());

        PruneResult pruned = restic.prune();
        assertFalse(pruned.getOutputLines().isEmpty());
        assertTrue(restic.check(CheckOptions.builder().readData().build()).isHealthy());
        assertEquals(1, restic.stats(StatsOptions.builder().mode(StatsMode.RAW_DATA).build()).getSnapshotsCount());
    }

    @Test
    void diffTagListAndCopyAreUsableFromJava(@TempDir Path temporaryDirectory) throws Exception {
        String binary = System.getenv("RESTIC_BINARY") != null ? System.getenv("RESTIC_BINARY") : "restic";
        Restic restic = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("repo").toString())
                .password("pw")
                .build());
        restic.init();
        Path source = Files.createDirectory(temporaryDirectory.resolve("src"));
        Files.writeString(source.resolve("a.txt"), "one");
        String first = restic.backup(List.of(source)).getSummary().getSnapshotId();
        Files.writeString(source.resolve("a.txt"), "two");
        String second = restic.backup(List.of(source)).getSummary().getSnapshotId();

        DiffResult diff = restic.diffAll(first, second);
        assertEquals(1, diff.getStatistics().getChangedFiles());

        TagResult tagged = restic.tag(TagOptions.builder().snapshotId(second).add("java").build());
        assertEquals(1, tagged.getChangedSnapshots());
        assertEquals(2, restic.list(ListObjectType.SNAPSHOTS).size());

        Restic target = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("target").toString())
                .password("pw")
                .build());
        target.init();
        CopyResult copied = target.copy(CopyOptions.builder()
                .fromRepository(temporaryDirectory.resolve("repo").toString())
                .fromPassword("pw")
                .build());
        assertEquals(2, copied.getNewSnapshotIds().size());
    }

    @Test
    void keysFeaturesAndUnlockAreUsableFromJava(@TempDir Path temporaryDirectory) {
        String binary = System.getenv("RESTIC_BINARY") != null ? System.getenv("RESTIC_BINARY") : "restic";
        Restic restic = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("repo").toString())
                .password("pw")
                .build());
        restic.init();

        KeyAddResult added = restic.addKey(NewKeyOptions.builder().newPassword("second").user("bob").build());
        assertEquals(2, restic.listKeys().size());
        restic.removeKey(added.getKeyId());
        assertEquals(1, restic.listKeys().size());

        restic.unlock();
        assertTrue(restic.features().stream().anyMatch(flag -> flag.getName().equals("safe-forget-keep-tags")));
        assertFalse(restic.migrate().getLines().isEmpty());
    }

    @Test
    void helpCompletionAndOptionsAreUsableFromJava(@TempDir Path temporaryDirectory) {
        String binary = System.getenv("RESTIC_BINARY") != null ? System.getenv("RESTIC_BINARY") : "restic";
        Restic restic = new Restic(ResticConfiguration.builder().binary(binary).build());

        assertTrue(restic.help("backup").contains("restic backup"));
        assertTrue(restic.help().contains("Available Commands"));
        assertTrue(restic.completion(CompletionShell.BASH).contains("restic"));
        assertFalse(restic.extendedOptions().isEmpty());
        assertEquals(
                true,
                restic.generate(GenerateOptions.builder().bashCompletion(temporaryDirectory.resolve("c.bash")).build())
                        .getLines().isEmpty() || true);
    }
}
