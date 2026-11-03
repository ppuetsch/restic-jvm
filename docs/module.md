# Module restic-jvm

Kotlin library that drives the [restic](https://restic.net) CLI and is just as convenient from Java as from Kotlin.
It starts the `restic` binary, speaks its documented `--json` interface and maps the results to types.

## Getting started

Everything starts at [Restic][io.github.ppuetsch.restic.Restic]: one instance per repository, created from a
[ResticConfiguration][io.github.ppuetsch.restic.ResticConfiguration]. Every restic command is a method; an options
builder collects its flags, for example [BackupOptions][io.github.ppuetsch.restic.BackupOptions].

```kotlin
val restic = Restic(
    ResticConfiguration.builder()
        .repository("sftp:backup@host:/srv/restic")
        .passwordFile(Path.of("/etc/restic.pw"))
        .build(),
)

val result = restic.backup(listOf(Path.of("/home/me"))) {
    tag("daily")
    exclude("*.tmp")
}
restic.forget { keepDaily(7); keepWeekly(4); prune() }
```

```java
Restic restic = new Restic(ResticConfiguration.builder()
        .repository("C:/backups/repo")
        .password("secret")
        .build());

BackupResult result = restic.backup(
        List.of(Path.of("C:/Users/me/Documents")),
        BackupOptions.builder().tag("daily").exclude("*.tmp").build());
```

## Where to find a command

The everyday commands are methods of [Restic][io.github.ppuetsch.restic.Restic]: `init`, `backup`, `snapshots`, `list`,
`find`, `restore` and `forget`. Everything else is grouped by topic:

| Call | Commands |
|---|---|
| `restic.files()` | `ls`, `dump`, `diff`, `mount` |
| `restic.modify()` | `tag`, `rewrite`, `copy` |
| `restic.maintenance()` | `prune`, `check`, `stats`, `unlock`, `migrate`, `cache`, `recover`, `features` |
| `restic.repair()` | `index`, `packs`, `snapshots` |
| `restic.keys()` | `list`, `add`, `changePassword`, `remove` |
| `restic.cat()` | `config`, `snapshot`, `key`, `lock`, `tree`, `index`, `masterKey`, `raw`, `text` |
| `restic.tools()` | `version`, `generate`, `completion`, `selfUpdate`, `help`, `extendedOptions` |

## Ground rules

- **restic** comes from [ResticConfiguration.Builder.binary][io.github.ppuetsch.restic.ResticConfiguration.Builder.binary],
  otherwise from a `restic-jvm-binary-<platform>` artifact on the class path (extracted and checked on first use),
  otherwise from the `PATH`.
- **Passwords** never appear on the command line; they go through environment variables, files or commands.
- **Errors** form a sealed hierarchy below [ResticException][io.github.ppuetsch.restic.ResticException]; restic's
  exit code selects the subclass, and [ResticErrorKind][io.github.ppuetsch.restic.ResticErrorKind] narrows down the cause.
- **Cancelling** works through a [CancellationToken][io.github.ppuetsch.restic.CancellationToken], which ends restic
  together with its child processes.
- **Running output** (progress, entries of `ls`, changes of `diff`) arrives through listeners, without collecting
  everything in memory.
- **Repository objects** returned by [Restic.list][io.github.ppuetsch.restic.Restic.list] know their ID right away and
  read the rest on first access, once; see [RepositoryObject][io.github.ppuetsch.restic.RepositoryObject].

## Examples

All examples assume a ready `restic` instance and run as tests against a real repository
(`DocumentationExamplesTest`, `DocumentationExamplesKotlinTest`).

### Print all locks of a repository

[Restic.list][io.github.ppuetsch.restic.Restic.list] with `LOCKS` returns [Lock][io.github.ppuetsch.restic.Lock]
objects. Their content is read on the first access to a property such as `pid`, with a single `restic cat lock` that
fills all properties at once. A repository nobody is working on usually has no locks; stale ones are removed by
[restic.maintenance().unlock()][io.github.ppuetsch.restic.MaintenanceCommands.unlock].

```java
restic.list(ListObjectType.LOCKS).stream()
        .map(lock -> lock.getId() + ": pid " + lock.getPid() + " on " + lock.getHostname() + " since " + lock.getTime()
                + (lock.getExclusive() ? " (exclusive)" : ""))
        .forEach(System.out::println);
```

```kotlin
restic.list(ListObjectType.LOCKS)
    .map { lock -> "${lock.id}: pid ${lock.pid} on ${lock.hostname} since ${lock.time}" + if (lock.exclusive) " (exclusive)" else "" }
    .forEach(::println)
```

### Iterate over all snapshots

[Restic.snapshots][io.github.ppuetsch.restic.Restic.snapshots] returns them oldest first;
[SnapshotsOptions][io.github.ppuetsch.restic.SnapshotsOptions] filters by host, path or tag.

```java
restic.snapshots().stream()
        .map(snapshot -> snapshot.getShortId() + " " + snapshot.getTime() + " " + snapshot.getHostname() + " " + snapshot.getPaths())
        .forEach(System.out::println);
```

```kotlin
restic.snapshots()
    .map { "${it.shortId} ${it.time} ${it.hostname} ${it.paths}" }
    .forEach(::println)
```

### All snapshots that contain a certain file

[Restic.find][io.github.ppuetsch.restic.Restic.find] searches by name pattern (`*` and `?` allowed) and lists the
matches per snapshot. Each result reads its [Snapshot][io.github.ppuetsch.restic.Snapshot] on first access.

```java
List<Snapshot> snapshotsWithFile = restic.find(List.of("mySpecialFile.txt")).stream()
        .map(FindSnapshotMatches::getSnapshot)
        .toList();
```

```kotlin
val snapshotsWithFile = restic.find(listOf("mySpecialFile.txt")).map { it.snapshot }
```

### Read a file into memory

[restic.files().dump][io.github.ppuetsch.restic.FileCommands.dump] writes a file from a snapshot to any `OutputStream`, here a byte
array. The path is the one inside the snapshot, on Windows for example `/C/Users/me/notes.txt`; the easiest way to get
it is from a `find` match.

```java
FindSnapshotMatches hit = restic.find(List.of("mySpecialFile.txt")).get(0);

ByteArrayOutputStream buffer = new ByteArrayOutputStream();
restic.files().dump(hit.getSnapshotId(), hit.getMatches().get(0).getPath(), buffer);
String content = buffer.toString(StandardCharsets.UTF_8);
```

```kotlin
val hit = restic.find(listOf("mySpecialFile.txt")).first()

val buffer = ByteArrayOutputStream()
restic.files().dump(hit.snapshotId, hit.matches.first().path, buffer)
val content = buffer.toString(Charsets.UTF_8)
```

### Back up a Java object and get it back

restic stores bytes, so the object is serialized first, here with Jackson as JSON (any other format works the same
way). [Restic.backupStandardInput][io.github.ppuetsch.restic.Restic.backupStandardInput] stores the bytes as one file
in the root of the snapshot, named by `stdinFilename`.

```java
record Settings(String name, int retries) {}

JsonMapper mapper = JsonMapper.builder().build();
Settings settings = new Settings("nightly", 3);

byte[] json = mapper.writeValueAsBytes(settings);
BackupResult result = restic.backupStandardInput(
        new ByteArrayInputStream(json),
        BackupOptions.builder().stdinFilename("settings.json").tag("settings").build());

ByteArrayOutputStream buffer = new ByteArrayOutputStream();
restic.files().dump(result.getSummary().getSnapshotId(), "/settings.json", buffer);
Settings restored = mapper.readValue(buffer.toByteArray(), Settings.class);
```

# Package io.github.ppuetsch.restic

The whole public API: the facade [Restic][io.github.ppuetsch.restic.Restic], the configuration, one options builder
per command and the result types.
