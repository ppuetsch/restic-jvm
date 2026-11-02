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

# Package io.github.ppuetsch.restic

Die gesamte öffentliche API: die Fassade [Restic][io.github.ppuetsch.restic.Restic], die Konfiguration, je Befehl ein
Options-Builder und die Ergebnistypen.
