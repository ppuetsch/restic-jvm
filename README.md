# restic-jvm

[![CI](https://github.com/ppuetsch/restic-jvm/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/ppuetsch/restic-jvm/actions/workflows/ci.yml?query=branch%3Amain)
[![CodeQL](https://github.com/ppuetsch/restic-jvm/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/ppuetsch/restic-jvm/actions/workflows/codeql.yml?query=branch%3Amain)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=ppuetsch_restic-jvm&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=ppuetsch_restic-jvm)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=ppuetsch_restic-jvm&metric=coverage)](https://sonarcloud.io/component_measures?id=ppuetsch_restic-jvm&metric=coverage)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/ppuetsch/restic-jvm/badge)](https://scorecard.dev/viewer/?uri=github.com/ppuetsch/restic-jvm)
[![Documentation](https://readthedocs.org/projects/restic-jvm/badge/?version=latest)](https://restic-jvm.readthedocs.io/)

[![Release](https://img.shields.io/github/v/release/ppuetsch/restic-jvm?sort=semver)](https://github.com/ppuetsch/restic-jvm/releases/latest)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://openjdk.org/projects/jdk/17/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![restic](https://img.shields.io/badge/restic-0.18%20%7C%200.19-blue)](https://restic.net)
[![License](https://img.shields.io/github/license/ppuetsch/restic-jvm)](LICENSE)

Kotlin library (fully usable from Java) that drives the [restic](https://restic.net) backup CLI.
It starts the `restic` binary, speaks its documented `--json` interface and maps results to typed objects.

- Java 17 or newer, a single runtime dependency (`jackson-databind`, never exposed in the public API)
- Repositories are addressed with restic connection strings: `C:/backups/repo`, `sftp:user@host:/path`, `rclone:remote:path`, `s3:...`
- Passwords never appear on the command line (environment variable, file or command)
- Streaming results (backup progress, `ls`, `restore`, `diff`) via listeners, no extra dependencies
- Every restic command and flag has a counterpart; tested end to end against real repositories on Linux and Windows

restic itself comes either from the `PATH`, from `binary(...)`, or bundled with the library (see below).

API reference for every release: **[restic-jvm.readthedocs.io](https://restic-jvm.readthedocs.io/)**. Its start page
has more examples: listing locks, iterating snapshots, finding the snapshots that contain a file, reading a file into
memory and backing up a Java object.

## Install

Releases are published to GitHub Packages (`https://maven.pkg.github.com/ppuetsch/restic-jvm`); Maven Central is planned.

```kotlin
dependencies {
    implementation("io.github.ppuetsch:restic-jvm:0.7.0")
}
```

### Bundled restic

To run with nothing but Java installed, add the artifact with the restic binary for the platform the service runs on:

```kotlin
runtimeOnly("io.github.ppuetsch:restic-jvm-binary-linux-x86_64:0.7.0")
```

Available: `linux-x86_64`, `linux-aarch64`, `windows-x86_64`, `macos-x86_64`, `macos-aarch64`, or
`restic-jvm-binaries-all` for all of them (the library then picks the matching one at runtime). Name the platform
the service *runs* on, not the one it is built on: a Linux Docker image built on Windows needs `linux-x86_64`.

Without `binary(...)`, the library uses the bundled binary for the running platform if one is on the class path, and
`restic` from the `PATH` otherwise. On first use it extracts the binary into the user's cache directory
(`bundledBinaryDirectory(...)` changes that, for example for read-only containers) and checks its SHA-256 before every
first run in a JVM. The binaries are restic's official release builds (BSD 2-Clause, license included); the build
pins their checksums, which match restic's signed `SHA256SUMS`.

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
restic.maintenance().check()
```

The everyday commands (`init`, `backup`, `snapshots`, `list`, `find`, `restore`, `forget`) are methods of `Restic`;
the rest is grouped: `files()` (ls, dump, diff, mount), `modify()` (tag, rewrite, copy), `maintenance()` (prune,
check, stats, unlock, ...), `repair()`, `keys()`, `cat()` and `tools()` (version, completion, help, ...).

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

restic.snapshots().stream()
        .map(snapshot -> snapshot.getShortId() + " " + snapshot.getTime())
        .forEach(System.out::println);
```

### Repository objects

`list` returns typed objects (`Lock`, `Key`, `Index`, `Pack`, `Blob`, `Snapshot`). Listing reads only their IDs; a lock,
key or index file reads its content on the first access to any other property, once, with one `restic cat` call.
Find results load their snapshot the same way.

```java
restic.list(ListObjectType.LOCKS).forEach(lock ->
        System.out.println(lock.getId() + ": pid " + lock.getPid() + " on " + lock.getHostname()));

List<Snapshot> withFile = restic.find(List.of("mySpecialFile.txt")).stream()
        .map(FindSnapshotMatches::getSnapshot)
        .toList();
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
- methods without options take it as their last parameter, e.g. `maintenance().unlock(false, token)`, `keys().list(token)`, `tools().help(listOf("backup"), token)`.

`cancel()` ends restic **and all its child processes** (for example `rclone` behind an `rclone:` repository),
first politely and after two seconds forcibly, on Linux and Windows. The call throws `ResticCancelledException`.
One token may be shared by several calls, also in parallel; `cancel()` stops all of them, and a cancelled token stays cancelled.
A configured `timeout` ends the process tree the same way and throws `ResticTimeoutException`.
If restic still finished successfully at the moment of cancellation, its result is returned.

```kotlin
val token = CancellationToken()
val listing = executor.submit { restic.files().listNodes(Restic.LATEST, LsOptions.builder().cancellationToken(token).build()) }
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
val check = restic.maintenance().check(CheckOptions.builder().readDataSubset("10%").build(), listener)
println("healthy=${check.isHealthy()}, ${check.outputLines.size} lines")

val prune = restic.maintenance().prune(PruneOptions.DEFAULT, listener)
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

Building needs JDK 25; the library is compiled for Java 17 (bytecode and API), and
`-PtestJavaVersion=17` runs the tests on a Java 17 runtime, as CI does for the restic 0.18.1 legs.

```
./gradlew build                   # needs a restic binary; set RESTIC_BINARY to its path if it is not on the PATH
./gradlew pitest                  # mutation testing of the parsers and argument building
./gradlew updateKotlinAbi         # accept an intended change of the public API
./gradlew cyclonedxDirectBom      # CycloneDX SBOM in build/reports/sbom
./gradlew dependencyCheckAnalyze  # OWASP dependency check; set NVD_API_KEY for a fast NVD download
./gradlew sonar                   # SonarCloud analysis; needs SONAR_TOKEN
./gradlew dokkaGeneratePublicationHtml  # API docs in build/dokka/html
python scripts/vulnerable_locks.py      # locked dependencies (incl. build tools) with known vulnerabilities (OSV)
```

`build` fails on any of these:

- a failing end-to-end test, a ktlint or detekt finding (`detektMain`, `detektTest` with type resolution)
- a compiler warning (Kotlin and Java)
- a change of the public API that is not recorded in `api/restic-jvm.api`
- line coverage below 90 % or branch coverage below 70 % (JaCoCo)

CI runs this on Linux and Windows against restic 0.18 and 0.19, plus PIT (mutation score at least 95 %), the API docs
(any public declaration without KDoc fails them), CodeQL,
OpenSSF Scorecard, the OWASP dependency check and SonarCloud. Findings go to GitHub code scanning; everything repeats
weekly to catch newly published vulnerabilities. `main` only accepts pull requests with green checks.

Versions live in `gradle/libs.versions.toml`. Resolved versions are locked (`*.lockfile`) and every artifact is checked
against its SHA-256 in `gradle/verification-metadata.xml`. After changing a version, rewrite both:

```
./gradlew --write-locks --write-verification-metadata sha256 build javadocJar cyclonedxDirectBom pitest
```

Build tools pull in libraries of their own (Dokka, ktlint, the Kotlin plugin). Where those carry known vulnerabilities,
`build.gradle.kts` raises them to a fixed version with the `floor-*` entries of the version catalog.

GitHub Actions are pinned to commit SHAs; Dependabot keeps them current.

### API documentation

The API reference is generated from the KDoc with Dokka and published on
[restic-jvm.readthedocs.io](https://restic-jvm.readthedocs.io/), one version per release tag (switch versions in the
Read the Docs menu). Read the Docs runs `scripts/build-docs-readthedocs.sh`; CI runs the same script on every pull
request.

### Verifying a release

Each release carries the jars, their PGP signatures, a CycloneDX SBOM and the public key `signing-key.asc`
(fingerprint `7B34 1314 7ACE 1EDB 715F 6293 05A9 61D4 163E 33E5`), plus a GitHub build provenance attestation:

```
gpg --import signing-key.asc
gpg --verify restic-jvm-0.7.0.jar.asc restic-jvm-0.7.0.jar
gh attestation verify restic-jvm-0.7.0.jar --repo ppuetsch/restic-jvm
```

Apache License 2.0.
