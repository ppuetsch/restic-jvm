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
    implementation("io.github.ppuetsch:restic-jvm:0.3.0")
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

Errors are a sealed `ResticException` hierarchy
(`RepositoryNotFoundException`, `WrongPasswordException`, `RepositoryLockedException`, ...).

### Cancelling

Every command that starts a restic process accepts an optional `CancellationToken`
(`backup`, `restore`, `ls`, `init`, `unlock`, `list`, `cat`, `snapshots`, `stats`, ...; `check` and `prune` take it through their options).
`cancel()` ends restic **and all its child processes** (for example `rclone` behind an `rclone:` repository),
first politely and after two seconds forcibly, on Linux and Windows. The call throws `ResticCancelledException`.
A configured `timeout` ends the process tree the same way.

```kotlin
val token = CancellationToken()
val listing = executor.submit { restic.ls(Restic.LATEST, LsOptions.DEFAULT, token, null) }
token.cancel() // from any thread; the call above throws ResticCancelledException
```

### Error messages and classification

`ResticCommandException.errorMessage` is the readable text:
the `message` of restic's `exit_error` JSON line, other lines unchanged, blank lines removed.
`kind` classifies the failure by exit code and message.

```kotlin
try {
    restic.snapshots()
} catch (failure: ResticCommandException) {
    when (failure.kind) {
        ResticErrorKind.WRONG_PASSWORD -> askForPasswordAgain()
        ResticErrorKind.NETWORK, ResticErrorKind.AUTHENTICATION -> showBackendProblem(failure.errorMessage)
        else -> log(failure.errorMessage)
    }
}
```

`NETWORK` and `AUTHENTICATION` are recognised from typical Go, SSH and S3 messages; anything else with exit code 1 is `OTHER`.

### Check and prune with a live log

`check` and `prune` accept an `OutputLineListener` that receives standard output (`onLine`) and standard error
(`onErrorLine`) line by line while the command runs. The results keep the lines as well.

```kotlin
val listener = object : OutputLineListener {
    override fun onLine(line: String) = log.info(line)
    override fun onErrorLine(line: String) = log.warn(line)
}
val check = restic.check(CheckOptions.builder().readDataSubset("10%").build(), listener)
println("healthy=${check.isHealthy()}, ${check.outputLines.size} lines")

val prune = restic.prune(PruneOptions.DEFAULT, listener)
println("freed ${prune.bytesFreed} bytes, deleted ${prune.packsDeleted} packs, repacked ${prune.packsRepacked} packs")
```

`restic prune` has no JSON output, so `PruneResult` is read from the text summary (tested with restic 0.18):
`bytesFreed` comes from `total prune:` and is rounded to the displayed precision (`1.234 KiB`);
pack counts come from `removing N old packs`, the `N / M packs repacked` progress line or, with `verbose`, `to delete: N packs`.
If restic prints no summary the figures are `null`; the raw `outputLines` are always available.

## Command coverage

| restic | Library |
|---|---|
| `init`, `version` | `init`, `version` |
| `backup` (incl. stdin, stdin-from-command) | `backup`, `backupStandardInput`, `backupCommandOutput` |
| `snapshots`, `ls`, `find` | `snapshots`, `snapshotGroups`, `ls`, `listNodes`, `find`, `findObjects` |
| `restore`, `dump`, `cat` | `restore`, `dump`, `dumpToFile`, `cat` (raw bytes), typed `catConfig`, `catSnapshot`, `catKey`, `catLock`, `catTree`, `catIndex`, `catMasterKey` |
| `forget`, `prune`, `check`, `stats` | `forget`, `prune`, `check`, `stats` |
| `diff`, `copy`, `tag`, `rewrite`, `list` | `diff`, `diffAll`, `copy`, `tag`, `rewrite`, `list`, `listBlobs` |
| `key list/add/passwd/remove` | `listKeys`, `addKey`, `changePassword`, `removeKey` |
| `unlock`, `migrate`, `cache`, `features`, `recover` | same names |
| `repair index/packs/snapshots` | `repairIndex`, `repairPacks`, `repairSnapshots` |
| `mount` (FUSE, not Windows) | `mount` returning a closable `MountHandle` |
| `generate`, `self-update`, `help`, `options` | `generate`, `completion`, `selfUpdate`, `help`, `extendedOptions` |

Global flags (`--no-lock`, `--compression`, `--limit-upload`, `--option`, ...) are on `ResticConfiguration`.

## Notes

- Commands without JSON output (`copy`, `rewrite`, `recover`, `repair`, ...) return their text lines.
- On Windows, restoring a snapshot that contains ancestors such as `C:\Users` fails when restic cannot set their timestamps; restore a sub folder (`latest:/C/Users/me/docs`) instead.
- `check` reports repository damage in `CheckResult.isHealthy()` instead of throwing. Its JSON is parsed into `CheckResult.messages` (`CheckMessage.Summary`/`Error`/`Text`); `outputLines` and the listener get readable text, never raw JSON.
- restic's JSON is always returned as typed objects, never as raw strings; `catText` is only for non-JSON objects.

## Build

```
./gradlew build      # needs a restic binary; set RESTIC_BINARY to its path if it is not on the PATH
```

Apache License 2.0.
