# Module restic-jvm

Kotlin-Library, die die [restic](https://restic.net)-CLI steuert und aus Java genauso gut nutzbar ist wie aus Kotlin.
Sie startet die `restic`-Binary, spricht deren dokumentierte `--json`-Schnittstelle und bildet die Ergebnisse auf
Typen ab.

## Einstieg

Alles beginnt bei [Restic][io.github.ppuetsch.restic.Restic]: eine Instanz je Repository, erzeugt aus einer
[ResticConfiguration][io.github.ppuetsch.restic.ResticConfiguration]. Jeder restic-Befehl ist eine Methode; seine Flags stellt ein
Options-Builder zusammen, z.B. [BackupOptions][io.github.ppuetsch.restic.BackupOptions].

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

## Grundregeln

- **Passwörter** landen nie auf der Kommandozeile, sondern in Umgebungsvariablen, Dateien oder Kommandos.
- **Fehler** sind eine versiegelte Hierarchie unter [ResticException][io.github.ppuetsch.restic.ResticException]; der Exit-Code von
  restic bestimmt die Unterklasse, [ResticErrorKind][io.github.ppuetsch.restic.ResticErrorKind] grenzt die Ursache weiter ein.
- **Abbrechen** geht über ein [CancellationToken][io.github.ppuetsch.restic.CancellationToken], das restic samt Kindprozessen beendet.
- **Laufende Ausgabe** (Fortschritt, Einträge von `ls`, Änderungen von `diff`) kommt über Listener, ohne alles im
  Speicher zu sammeln.

## Beispiele

Alle Beispiele gehen von einer fertigen `restic`-Instanz aus und laufen so als Tests gegen ein echtes Repository
(`DocumentationExamplesTest`, `DocumentationExamplesKotlinTest`).

### Alle Sperren eines Repositorys ausgeben

[Restic.list][io.github.ppuetsch.restic.Restic.list] liefert die IDs der Sperrdateien,
[Restic.catLock][io.github.ppuetsch.restic.Restic.catLock] den Inhalt. Ein Repository ohne laufenden restic-Prozess
hat in der Regel keine Sperren; übrig gebliebene entfernt [Restic.unlock][io.github.ppuetsch.restic.Restic.unlock].

```java
for (String lockId : restic.list(ListObjectType.LOCKS)) {
    RepositoryLock lock = restic.catLock(lockId);
    System.out.println(lockId + ": pid " + lock.getPid() + " on " + lock.getHostname() + " since " + lock.getTime()
            + (lock.getExclusive() ? " (exclusive)" : ""));
}
```

```kotlin
restic.list(ListObjectType.LOCKS).forEach { lockId ->
    val lock = restic.catLock(lockId)
    println("$lockId: pid ${lock.pid} on ${lock.hostname} since ${lock.time}" + if (lock.exclusive) " (exclusive)" else "")
}
```

### Alle Snapshots durchlaufen

[Restic.snapshots][io.github.ppuetsch.restic.Restic.snapshots] liefert sie vom ältesten zum neuesten; mit
[SnapshotsOptions][io.github.ppuetsch.restic.SnapshotsOptions] lässt sich nach Host, Pfad oder Tag filtern.

```java
for (Snapshot snapshot : restic.snapshots()) {
    System.out.println(snapshot.getShortId() + " " + snapshot.getTime() + " " + snapshot.getHostname() + " " + snapshot.getPaths());
}
```

```kotlin
restic.snapshots().forEach { snapshot ->
    println("${snapshot.shortId} ${snapshot.time} ${snapshot.hostname} ${snapshot.paths}")
}
```

### Alle Snapshots, die eine bestimmte Datei enthalten

[Restic.find][io.github.ppuetsch.restic.Restic.find] sucht nach Namensmustern (`*` und `?` erlaubt) und nennt je
Snapshot die Treffer.

```java
Set<String> snapshotIds = restic.find(List.of("mySpecialFile.txt")).stream()
        .map(FindSnapshotMatches::getSnapshotId)
        .collect(Collectors.toSet());
List<Snapshot> snapshotsWithFile = restic.snapshots().stream()
        .filter(snapshot -> snapshotIds.contains(snapshot.getId()))
        .toList();
```

```kotlin
val snapshotIds = restic.find(listOf("mySpecialFile.txt")).map { it.snapshotId }.toSet()
val snapshotsWithFile = restic.snapshots().filter { it.id in snapshotIds }
```

### Eine Datei in den Speicher holen

[Restic.dump][io.github.ppuetsch.restic.Restic.dump] schreibt eine Datei aus dem Snapshot in einen beliebigen
`OutputStream`, hier in ein Byte-Array. Der Pfad ist der im Snapshot, unter Windows also z.B. `/C/Users/me/notes.txt`;
am einfachsten nimmt man ihn aus einem Treffer von `find`.

```java
FindSnapshotMatches hit = restic.find(List.of("mySpecialFile.txt")).get(0);
String pathInSnapshot = hit.getMatches().get(0).getPath();

ByteArrayOutputStream buffer = new ByteArrayOutputStream();
restic.dump(hit.getSnapshotId(), pathInSnapshot, buffer);
String content = buffer.toString(StandardCharsets.UTF_8);
```

```kotlin
val hit = restic.find(listOf("mySpecialFile.txt")).first()

val buffer = ByteArrayOutputStream()
restic.dump(hit.snapshotId, hit.matches.first().path, buffer)
val content = buffer.toString(Charsets.UTF_8)
```

### Ein Java-Objekt sichern und zurückholen

restic sichert Bytes; das Objekt wird also zuerst serialisiert, hier mit Jackson als JSON (jedes andere Format geht
genauso). [Restic.backupStandardInput][io.github.ppuetsch.restic.Restic.backupStandardInput] legt die Bytes als eine
Datei im Wurzelverzeichnis des Snapshots ab, ihr Name kommt aus `stdinFilename`.

```java
record Settings(String name, int retries) {}

JsonMapper mapper = JsonMapper.builder().build();
Settings settings = new Settings("nightly", 3);

byte[] json = mapper.writeValueAsBytes(settings);
BackupResult result = restic.backupStandardInput(
        new ByteArrayInputStream(json),
        BackupOptions.builder().stdinFilename("settings.json").tag("settings").build());

ByteArrayOutputStream buffer = new ByteArrayOutputStream();
restic.dump(result.getSummary().getSnapshotId(), "/settings.json", buffer);
Settings restored = mapper.readValue(buffer.toByteArray(), Settings.class);
```

# Package io.github.ppuetsch.restic

Die gesamte öffentliche API: die Fassade [Restic][io.github.ppuetsch.restic.Restic], die Konfiguration, je Befehl ein
Options-Builder und die Ergebnistypen.
