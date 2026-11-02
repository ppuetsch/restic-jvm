"""Listet gesperrte Maven-Abhängigkeiten mit bekannten Schwachstellen laut OSV.

Scorecard prüft die Gradle-Lockfiles mit OSV; dieses Skript zeigt dieselben Funde lokal samt der ersten
Version, die sie behebt, damit sie per Constraint angehoben werden können.
"""

import io
import json
import sys
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

OSV_BATCH_URL = "https://api.osv.dev/v1/querybatch"
OSV_VULNERABILITY_URL = "https://api.osv.dev/v1/vulns/"
LOCKFILE_PATTERN = "*.lockfile"
OSV_BATCH_LIMIT = 1000

type Koordinate = str
type Version = str
type SchwachstellenId = str
type OsvAntwort = dict[str, Any]


@dataclass(frozen=True)
class GesperrteAbhaengigkeit:
    koordinate: Koordinate
    version: Version


@dataclass(frozen=True)
class Fund:
    abhaengigkeit: GesperrteAbhaengigkeit
    schwachstelle: SchwachstellenId
    behoben_in: list[Version]


def main() -> None:
    if isinstance(sys.stdout, io.TextIOWrapper):
        sys.stdout.reconfigure(encoding="utf-8")
    projekt = Path(__file__).resolve().parent.parent
    abhaengigkeiten = gesperrte_abhaengigkeiten(projekt)
    funde = schwachstellen_von(abhaengigkeiten)
    berichte(funde)
    sys.exit(1 if funde else 0)


def gesperrte_abhaengigkeiten(projekt: Path) -> list[GesperrteAbhaengigkeit]:
    eindeutige: set[GesperrteAbhaengigkeit] = set()
    for lockfile in projekt.glob(LOCKFILE_PATTERN):
        eindeutige.update(eintraege_aus(lockfile))
    return sorted(eindeutige, key=lambda abhaengigkeit: abhaengigkeit.koordinate)


def eintraege_aus(lockfile: Path) -> list[GesperrteAbhaengigkeit]:
    eintraege = []
    for zeile in lockfile.read_text(encoding="utf-8").splitlines():
        ist_eintrag = zeile and not zeile.startswith("#") and not zeile.startswith("empty=")
        if not ist_eintrag:
            continue
        gruppe, artefakt, version = zeile.split("=")[0].split(":")
        eintraege.append(GesperrteAbhaengigkeit(f"{gruppe}:{artefakt}", version))
    return eintraege


def schwachstellen_von(abhaengigkeiten: list[GesperrteAbhaengigkeit]) -> list[Fund]:
    funde = []
    for start in range(0, len(abhaengigkeiten), OSV_BATCH_LIMIT):
        abschnitt = abhaengigkeiten[start : start + OSV_BATCH_LIMIT]
        antworten = osv_anfrage(OSV_BATCH_URL, {"queries": [osv_abfrage(abhaengigkeit) for abhaengigkeit in abschnitt]})
        for abhaengigkeit, ergebnis in zip(abschnitt, antworten["results"]):
            for treffer in ergebnis.get("vulns", []):
                funde.append(Fund(abhaengigkeit, treffer["id"], behebende_versionen(treffer["id"], abhaengigkeit)))
    return funde


def osv_abfrage(abhaengigkeit: GesperrteAbhaengigkeit) -> OsvAntwort:
    return {"package": {"ecosystem": "Maven", "name": abhaengigkeit.koordinate}, "version": abhaengigkeit.version}


def behebende_versionen(schwachstelle: SchwachstellenId, abhaengigkeit: GesperrteAbhaengigkeit) -> list[Version]:
    details = osv_anfrage(OSV_VULNERABILITY_URL + schwachstelle, None)
    versionen = []
    for betroffen in details.get("affected", []):
        if betroffen.get("package", {}).get("name") != abhaengigkeit.koordinate:
            continue
        for bereich in betroffen.get("ranges", []):
            versionen += [ereignis["fixed"] for ereignis in bereich.get("events", []) if "fixed" in ereignis]
    return versionen


def osv_anfrage(url: str, nutzlast: OsvAntwort | None) -> OsvAntwort:
    daten = json.dumps(nutzlast).encode() if nutzlast is not None else None
    anfrage = urllib.request.Request(url, data=daten, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(anfrage) as antwort:
        ergebnis: OsvAntwort = json.load(antwort)
        return ergebnis


def berichte(funde: list[Fund]) -> None:
    for fund in funde:
        abhaengigkeit = fund.abhaengigkeit
        behoben = ", ".join(fund.behoben_in) or "kein Fix"
        print(f"{abhaengigkeit.koordinate}:{abhaengigkeit.version}  {fund.schwachstelle}  behoben in: {behoben}")
    print(f"{len(funde)} Funde")


if __name__ == "__main__":
    main()
