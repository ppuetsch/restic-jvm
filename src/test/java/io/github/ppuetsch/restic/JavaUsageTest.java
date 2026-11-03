package io.github.ppuetsch.restic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses the API like an outside Java user: public types only, no Kotlin specifics. */
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

        assertTrue(restic.tools().version().isAtLeast(0, 17, 0));
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
        restic.files().ls(Restic.LATEST, LsOptions.builder().recursive().build(), node -> names.add(node.getName()));
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

        String filePathInSnapshot = restic.files().listNodes(Restic.LATEST, LsOptions.builder().recursive().build())
                .getNodes().stream().filter(node -> node.getName().equals("a.txt")).findFirst().orElseThrow().getPath();
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        restic.files().dump(Restic.LATEST, filePathInSnapshot, output);
        assertEquals("hello", output.toString(java.nio.charset.StandardCharsets.UTF_8));

        assertTrue(restic.cat().text(CatObjectType.CONFIG).contains("chunker_polynomial"));

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

        PruneResult pruned = restic.maintenance().prune();
        assertFalse(pruned.getOutputLines().isEmpty());
        assertTrue(restic.maintenance().check(CheckOptions.builder().readData().build()).isHealthy());
        assertEquals(1, restic.maintenance().stats(StatsOptions.builder().mode(StatsMode.RAW_DATA).build()).getSnapshotsCount());
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

        DiffResult diff = restic.files().diffAll(first, second);
        assertEquals(1, diff.getStatistics().getChangedFiles());

        TagResult tagged = restic.modify().tag(TagOptions.builder().snapshotId(second).add("java").build());
        assertEquals(1, tagged.getChangedSnapshots());
        assertEquals(2, restic.list(ListObjectType.SNAPSHOTS).size());

        Restic target = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("target").toString())
                .password("pw")
                .build());
        target.init();
        CopyResult copied = target.modify().copy(CopyOptions.builder()
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

        KeyAddResult added = restic.keys().add(NewKeyOptions.builder().newPassword("second").user("bob").build());
        assertEquals(2, restic.keys().list().size());
        restic.keys().remove(added.getKeyId());
        assertEquals(1, restic.keys().list().size());

        restic.maintenance().unlock();
        assertTrue(restic.maintenance().features().stream().anyMatch(flag -> flag.getName().equals("safe-forget-keep-tags")));
        assertFalse(restic.maintenance().migrate().getLines().isEmpty());
    }

    @Test
    void helpCompletionAndOptionsAreUsableFromJava(@TempDir Path temporaryDirectory) throws Exception {
        String binary = System.getenv("RESTIC_BINARY") != null ? System.getenv("RESTIC_BINARY") : "restic";
        Restic restic = new Restic(ResticConfiguration.builder().binary(binary).build());

        assertTrue(restic.tools().help("backup").contains("restic backup"));
        assertTrue(restic.tools().help().contains("Available Commands"));
        assertTrue(restic.tools().completion(CompletionShell.BASH).contains("restic"));
        assertFalse(restic.tools().extendedOptions().isEmpty());
        Path completionFile = temporaryDirectory.resolve("c.bash");
        restic.tools().generate(GenerateOptions.builder().bashCompletion(completionFile).build());
        assertTrue(Files.size(completionFile) > 0);
    }

    @Test
    void cancellationErrorsAndListenersAreUsableFromJava(@TempDir Path temporaryDirectory) {
        String binary = System.getenv("RESTIC_BINARY") != null ? System.getenv("RESTIC_BINARY") : "restic";
        Restic restic = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("repo").toString())
                .password("pw")
                .build());
        restic.init(InitOptions.DEFAULT, new CancellationToken());
        assertTrue(restic.snapshots(SnapshotsOptions.DEFAULT, new CancellationToken()).isEmpty());
        assertTrue(restic.list(ListObjectType.SNAPSHOTS, null).isEmpty());

        java.util.List<String> lines = new java.util.ArrayList<>();
        CheckResult checked = restic.maintenance().check(CheckOptions.DEFAULT, lines::add);
        assertTrue(checked.isHealthy());
        assertEquals(checked.getOutputLines(), lines);

        PruneResult pruned = restic.maintenance().prune(PruneOptions.DEFAULT, line -> {});
        assertNotNull(pruned.getBytesFreed());

        CancellationToken cancelled = new CancellationToken();
        cancelled.cancel();
        assertThrows(ResticCancelledException.class, () -> restic.maintenance().unlock(false, cancelled));
        assertThrows(ResticCancelledException.class, () -> restic.maintenance().features(cancelled));
        java.util.List<String> helpPath = java.util.List.of("backup");
        assertThrows(ResticCancelledException.class, () -> restic.tools().help(helpPath, cancelled));
        DiffOptions cancelledDiff = DiffOptions.builder().cancellationToken(cancelled).build();
        assertThrows(ResticCancelledException.class, () -> restic.files().diffAll("a", "b", cancelledDiff));
        assertFalse(restic.tools().version(new CancellationToken()).getVersion().isEmpty());
        BackupEvent backupEvent = new BackupEvent.Error("/home/me/locked.db", "archival", "permission denied");
        if (backupEvent instanceof BackupEvent.Error error) {
            assertEquals("/home/me/locked.db: permission denied", error.readable());
        }
        LsOptions cancelledLs = LsOptions.builder().cancellationToken(cancelled).build();
        assertEquals(cancelled, cancelledLs.getCancellationToken());
        assertThrows(ResticCancelledException.class, () -> restic.files().listNodes(Restic.LATEST, cancelledLs));
        SnapshotsOptions cancelledSnapshots = SnapshotsOptions.builder().cancellationToken(cancelled).build();
        assertThrows(ResticCancelledException.class, () -> restic.snapshots(cancelledSnapshots));
        java.util.List<String> patterns = java.util.List.of("*.txt");
        FindOptions cancelledFind = FindOptions.builder().cancellationToken(cancelled).build();
        assertThrows(ResticCancelledException.class, () -> restic.find(patterns, cancelledFind));
        StatsOptions cancelledStats = StatsOptions.builder().cancellationToken(cancelled).build();
        assertThrows(ResticCancelledException.class, () -> restic.maintenance().stats(cancelledStats));
        assertThrows(ResticCancelledException.class, () -> restic.files().ls(Restic.LATEST, LsOptions.DEFAULT, cancelled, null));

        java.util.List<String> outputLines = new java.util.ArrayList<>();
        java.util.List<String> errorLines = new java.util.ArrayList<>();
        PruneResult prunedAgain = restic.maintenance().prune(PruneOptions.DEFAULT, new OutputLineListener() {
            @Override
            public void onLine(String line) {
                outputLines.add(line);
            }

            @Override
            public void onErrorLine(String line) {
                errorLines.add(line);
            }
        });
        assertEquals(prunedAgain.getOutputLines(), outputLines);
        errorLines.forEach(line -> assertFalse(line.isBlank(), "readable error lines are never blank"));

        Restic wrongPassword = new Restic(ResticConfiguration.builder()
                .binary(binary)
                .repository(temporaryDirectory.resolve("repo").toString())
                .password("falsch")
                .build());
        WrongPasswordException failure = assertThrows(WrongPasswordException.class, wrongPassword::snapshots);
        assertEquals(ResticErrorKind.WRONG_PASSWORD, failure.getKind());
        assertEquals("Fatal: wrong password or no key found", failure.getErrorMessage());
    }
}
