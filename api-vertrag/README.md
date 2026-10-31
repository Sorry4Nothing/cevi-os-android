# Vertrag des Backends

Diese Ordner enthält eine Kopie des Vertrags, an den sich die App hält. Die Quelle ist das Backend-Repo `Sorry4Nothing/cevi-os`, Ordner `docs/api/`. Dort ändert man den Vertrag, hier nie von Hand.

| Datei | Inhalt |
| --- | --- |
| `openapi.yaml` | Routen, Request- und Response-Typen, Pflichtfelder, Nullability, Fehlercodes |
| `endpunkte.md` | Pro Route: Zweck und welche Rolle welchen Status bekommt (2xx erlaubt, 401 nicht angemeldet, 403 kein Recht, 404 unsichtbar). Vom Backend aus der Rechte-Matrix erzeugt |

**Stand der Kopie:** 30. Oktober 2026, beide Dateien wie auf `develop` des Backends (nach PR #215, Stufenlogos; ohne die Vertrauenspersonen, Backend #209).

Beide Dateien enthalten nur Platzhalter (`https://api.example.org`, `…@benutzer.invalid`): keine echten Adressen, Namen oder Zugangsdaten. Die Adresse des Servers steht nie im Repo, sondern in `~/.gradle/gradle.properties` (`ceviApiBaseUrl`).

## Was der Test prüft

`ApiContractTest` liest alle Aufrufe der App aus den `*Api.kt` (Methode und Pfad) und prüft, dass jeder im Vertrag steht. Ein falscher Pfad oder eine falsche Methode lässt `testDebugUnitTest` scheitern, auch in der CI. Er prüft nicht die Felder der Antworten (das macht jeder Test der Schnittstelle mit seinem Beispiel-JSON) und nicht, welche Routen der Vertrag hat, die die App nicht nutzt: Das ist eine Entscheidung, kein Fehler.

## Aktualisieren

1. Die beiden Dateien aus dem Backend nach `api-vertrag/` kopieren.
2. Stand oben nachführen.
3. `./gradlew testDebugUnitTest`. Scheitert `ApiContractTest`, hat das Backend eine Route umbenannt oder entfernt, die die App nutzt: zuerst die App anpassen, dann mergen.
