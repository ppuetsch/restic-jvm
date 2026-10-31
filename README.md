# restic-jvm

Kotlin library (fully usable from Java) that drives the [restic](https://restic.net) backup CLI.
It starts the `restic` binary, speaks its documented `--json` interface and maps results to typed objects.

- JVM 25, a single runtime dependency (`jackson-databind`, never exposed in the public API)
- Repositories are addressed with restic connection strings: `C:/backups/repo`, `sftp:user@host:/path`, `rclone:remote:path`, `s3:...`
- Passwords never appear on the command line (environment variable, file or command)
- Streaming results (backup progress, `ls`, `restore`, `diff`) via listeners, no extra dependencies
- Every restic command and flag has a counterpart; tested end to end against real repositories on Linux and Windows

You need the `restic` binary (0.17 or newer) on the `PATH`, or pass its location with `binary(...)`.

## Install

Releases are published to GitHub Packages (`https://maven.pkg.github.com/ppuetsch/restic-jvm`); Maven Central is planned.

```kotlin
dependencies {
    implementation("io.github.ppuetsch:restic-jvm:0.1.0")
}
```

## Kotlin

```kotlin
val restic = Restic(
    ResticConfiguration.builder()
        .repository("sftp:backup@host:/srv/restic")
        .passwordFile(Path.of("/etc/restic.pw"))
        .build(),
)

restic.init()

val result = restic.backup(listOf(Path.of("/home/me"))) {
    tag("daily")
    exclude("*.tmp")
    excludeCaches()
}
println("snapshot ${result.summary.snapshotId}, ${result.summary.filesNew} new files")

restic.forget { keepDaily(7); keepWeekly(4); prune() }
```

## Java

```java
Restic restic = new Restic(ResticConfiguration.builder()
        .repository("C:/backups/repo")
        .password("secret")
        .build());

BackupResult result = restic.backup(
        List.of(Path.of("C:/Users/me/Documents")),
        BackupOptions.builder().tag("daily").exclude("*.tmp").build(),
        event -> {
            if (event instanceof BackupEvent.Status status) {
                System.out.printf("%.0f%%%n", status.getPercentDone() * 100);
            }
        });

for (Snapshot snapshot : restic.snapshots()) {
    System.out.println(snapshot.getShortId() + " " + snapshot.getTime());
}
```

Long running calls take a `CancellationToken`; errors are a sealed `ResticException` hierarchy
(`RepositoryNotFoundException`, `WrongPasswordException`, `RepositoryLockedException`, ...).

## Command coverage

| restic | Library |
|---|---|
| `init`, `version` | `init`, `version` |
| `backup` (incl. stdin, stdin-from-command) | `backup`, `backupStandardInput`, `backupCommandOutput` |
| `snapshots`, `ls`, `find` | `snapshots`, `snapshotGroups`, `ls`, `listNodes`, `lsNcdu`, `find`, `findObjects` |
| `restore`, `dump`, `cat` | `restore`, `dump`, `dumpToFile`, `cat`, `catText` |
| `forget`, `prune`, `check`, `stats` | `forget`, `prune`, `check`, `stats` |
| `diff`, `copy`, `tag`, `rewrite`, `list` | `diff`, `diffAll`, `copy`, `tag`, `rewrite`, `list`, `listBlobs` |
| `key list/add/passwd/remove` | `listKeys`, `addKey`, `changePassword`, `removeKey` |
| `unlock`, `migrate`, `cache`, `features`, `recover` | same names |
| `repair index/packs/snapshots` | `repairIndex`, `repairPacks`, `repairSnapshots` |
| `mount` (FUSE, not Windows) | `mount` returning a closable `MountHandle` |
| `generate`, `self-update`, `help`, `options` | `generate`, `completion`, `selfUpdate`, `help`, `extendedOptions` |

Global flags (`--no-lock`, `--compression`, `--limit-upload`, `--option`, ...) are on `ResticConfiguration`.

## Notes

- Commands without JSON output (`prune`, `copy`, `rewrite`, `recover`, `repair`, ...) return their text lines.
- On Windows, restoring a snapshot that contains ancestors such as `C:\Users` fails when restic cannot set their timestamps; restore a sub folder (`latest:/C/Users/me/docs`) instead.
- `check` reports repository damage in `CheckResult.isHealthy()` instead of throwing.

## Build

```
./gradlew build      # needs a restic binary; set RESTIC_BINARY to its path if it is not on the PATH
```

Apache License 2.0.
