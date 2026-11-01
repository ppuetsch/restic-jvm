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
    implementation("io.github.ppuetsch:restic-jvm:0.6.0")
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

Every command that starts a restic process accepts an optional `CancellationToken`:

- every options class takes it: `cancellationToken(token)` on the builders of `BackupOptions`, `RestoreOptions`,
  `CheckOptions`, `PruneOptions`, `ForgetOptions`, `CopyOptions`, `RewriteOptions`, `DiffOptions`, `LsOptions`,
  `SnapshotsOptions`, `FindOptions` and `StatsOptions`;
- `ls` also has the overload `ls(snapshotId, options, cancellationToken, listener)`; `listNodes`, `snapshots`,
  `snapshotGroups`, `find`, `findObjects` and `stats` also take it as their last parameter.
  **A token passed directly wins over the one in the options**; if it is `null`, the options token applies;
- methods without options take it as their last parameter, e.g. `unlock(false, token)`, `listKeys(token)`, `help(listOf("backup"), token)`.

`cancel()` ends restic **and all its child processes** (for example `rclone` behind an `rclone:` repository),
first politely and after two seconds forcibly, on Linux and Windows. The call throws `ResticCancelledException`.
One token may be shared by several calls, also in parallel; `cancel()` stops all of them, and a cancelled token stays cancelled.
A configured `timeout` ends the process tree the same way and throws `ResticTimeoutException`.
If restic still finished successfully at the moment of cancellation, its result is returned.

```kotlin
val token = CancellationToken()
val listing = executor.submit { restic.listNodes(Restic.LATEST, LsOptions.builder().cancellationToken(token).build()) }
token.cancel() // from any thread; the call above throws ResticCancelledException
```

### Error messages and classification

`ResticCommandException.errorMessage` is the readable text: restic's `exit_error` and `error` JSON lines reduced to
their message (`item: message` where restic names a file), other JSON messages to their `message` or type,
plain text lines unchanged, blank lines removed. The raw output is not exposed.
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

Errors that `backup` and `restore` report for single files (`BackupEvent.Error`, `RestoreEvent.Error`) offer the same
line through `readable()`: `/home/me/locked.db: permission denied`.

### Check and prune with a live log

`check`, `prune`, `forget`, `copy` and `rewrite` accept an `OutputLineListener` that receives standard output (`onLine`)
and standard error (`onErrorLine`) line by line while the command runs; JSON messages arrive as readable text.
An exception thrown by the listener aborts the call and is rethrown to the caller. The results keep the lines as well.

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
./gradlew build                   # needs a restic binary; set RESTIC_BINARY to its path if it is not on the PATH
./gradlew pitest                  # mutation testing of the parsers and argument building
./gradlew updateKotlinAbi         # accept an intended change of the public API
./gradlew cyclonedxDirectBom      # CycloneDX SBOM in build/reports/sbom
./gradlew dependencyCheckAnalyze  # OWASP dependency check; set NVD_API_KEY for a fast NVD download
./gradlew sonar                   # SonarCloud analysis; needs SONAR_TOKEN
```

`build` fails on any of these:

- a failing end-to-end test, a ktlint or detekt finding (`detektMain`, `detektTest` with type resolution)
- a compiler warning (Kotlin and Java)
- a change of the public API that is not recorded in `api/restic-jvm.api`
- line coverage below 90 % or branch coverage below 70 % (JaCoCo)

CI runs this on Linux and Windows against restic 0.18 and 0.19, plus PIT (mutation score at least 95 %), CodeQL,
OpenSSF Scorecard, the OWASP dependency check and SonarCloud. Findings go to GitHub code scanning; everything repeats
weekly to catch newly published vulnerabilities. `main` only accepts pull requests with green checks.

Versions live in `gradle/libs.versions.toml`. Resolved versions are locked (`*.lockfile`) and every artifact is checked
against its SHA-256 in `gradle/verification-metadata.xml`. After changing a version, rewrite both:

```
./gradlew --write-locks --write-verification-metadata sha256 build javadocJar cyclonedxDirectBom pitest
```

GitHub Actions are pinned to commit SHAs; Dependabot keeps them current.

### Verifying a release

Each release carries the jars, their PGP signatures, a CycloneDX SBOM and the public key `signing-key.asc`
(fingerprint `7B34 1314 7ACE 1EDB 715F 6293 05A9 61D4 163E 33E5`), plus a GitHub build provenance attestation:

```
gpg --import signing-key.asc
gpg --verify restic-jvm-0.7.0.jar.asc restic-jvm-0.7.0.jar
gh attestation verify restic-jvm-0.7.0.jar --repo ppuetsch/restic-jvm
```

Apache License 2.0.
