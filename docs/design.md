# Design

Stil «Nachthimmel»: ruhiges, blau getöntes Dunkel, Cevi-Blau für Orientierung, Cevi-Rot für die eine Hauptaktion pro Screen, grosse fette Titel, runde Formen, Karten mit feinem Rand statt Schatten.

## Vorlage (verbindlich)

| Was | Link |
| --- | --- |
| Design System «Cevi App»: Farben, Schrift, Abstände, Bausteine, Brandbook | <https://claude.ai/artifact/9mAd5832pLn8DCnG8AAY8e> |
| Bildschirme «Cevi App Screens»: Login bis Zustände, Nacht (`B…`, `S…`) und Tag (`L…`, `T…`) | <https://claude.ai/artifact/3590108e-92e4-4a95-8442-a12e98ac9287> |

Beide Seiten gehören dem Projekt und sind privat: Zum Lesen braucht es Zugang. Die Werte, die der Code braucht, stehen deshalb auch hier und in den Kommentaren der Theme-Dateien. Weicht der Code vom Entwurf ab, ist das ein Fehler im Code, ausser der Entwurf wurde geändert: Dann zuerst den Entwurf anpassen, dann den Code, und dieses Dokument nachführen.

## Regeln aus dem Brandbook

- **Sprache:** nur Deutsch, Schweizer Schreibweise (ss, «so»), «du», kurze Sätze, Buttons sind Verben, keine Emojis.
- **Farbe:** zwei Themen, **Nacht** (Hauptrichtung) und **Tag**, nach Systemeinstellung, kein Dynamic Color. `secondary` (Blau) zeigt, wo man ist; `primary` (Rot) nur für die eine Hauptaktion und Zähler; `accent` für Links und Überzeilen. **Kein Zustand nur über Farbe**: immer Icon und Wort.
- **Schrift:** Archivo, mitgeliefert, alles in `sp`.
- **Mass:** jedes bedienbare Element mindestens 48 dp, Haupt-Buttons 56 dp, Felder 52 dp.
- **Barrierefreiheit:** TalkBack liest Karten als ganzen Satz, Titel sind Überschriften, Fokus nach Navigation auf den Titel, Statusänderungen als Live-Region, langes Drücken hat immer eine sichtbare Alternative.
- **Die App zeigt nur, was die API erlaubt.** Fehlt die Aktion in der Antwort, fehlt der Button, er wird nicht ausgegraut.

## Von Design zu Compose

| Design | Compose | Stand |
| --- | --- | --- |
| Farben `tokens.json` (Nacht, Tag) | `ui/theme/Farben.kt`: `ColorScheme` und `CeviFarben` (`MaterialTheme.cevi`) | umgesetzt |
| Schrift und Stile | `ui/theme/Schrift.kt`, Schrift in `res/font/archivo.ttf` (Lizenz in `assets/licenses`) | umgesetzt |
| Abstände, Grössen, Ecken | `ui/theme/Masse.kt` (`Abstand`, `Groesse`, `CeviFormen`, `Voll`) | umgesetzt |
| Theme | `ui/theme/Theme.kt`: `CeviTheme` | umgesetzt |
| Strich-Icons (27, 24 dp, nur Strich, Strichstärke 1.8, Häkchen und Kreuz auch als `_gewaehlt` mit 2.6) | `res/drawable/ic_*.xml`, in Compose mit `Icon(painterResource(...), tint = ...)` einfärben | umgesetzt |
| Kopf (`Kopf`, `RundKnopf`) | `ui/Kopf.kt` | umgesetzt (#38) |
| Footer-Dock, im Querformat und ab 600 dp senkrecht links | `ui/Dock.kt`: `HauptGeruest`, `Ziel` (Programme, Chat; Profil kommt mit dem Profil) | umgesetzt (#38) |
| Termin-Liste (Datumsspalte, Karte), TerminDetail (Kacheln, Fakten) | `programme/ProgrammeScreens.kt`, `programme/Tage.kt` | umgesetzt (#38), ohne Kontaktperson und ohne Status je Kind in der Liste (API) |
| Zusage (Ja/Nein-Pillen) | `programme/AnmeldungAbschnitt.kt` | umgesetzt (#38) |
| StatusChip, Hinweis (Banner), Buttons, Fokusring | `ui/Chips.kt`, `ui/Hinweis.kt`, `ui/Knoepfe.kt`, `ui/Fokus.kt` | umgesetzt (#38) |
| Zustand-Screens (Laden-Skelett, Leer, Offline, Fehler, 404, Kein Zugriff, Sitzung) | `ui/Zustaende.kt`: `FehlerZustand`, `LeerZustand`, `LadeSkelett` | umgesetzt (#38); «Anmelden» erst mit dem Login |
| ChatZeile, ChatBlase, ChatEingabe, MitleseHinweis, Dialog, Hinweis, Zustände | `chat/…`, `ui/…` | Issue #39 |
| Login, Passwort, Willkommen, Profil, Datenschutz | – | mit den jeweiligen Issues (Login, Profil) |
| Anmeldungen und Anwesenheit (Leitende) | – | Epic «Leitende», nicht begonnen |

## Was der Entwurf zeigt, die API aber (noch) nicht liefert

Die API wird entsprechend erweitert (Entscheid 2026-10-30). Bis dahin nichts erfinden: Der Baustein bleibt weg, die Stelle ist mit dem Backend-Issue verknüpft.

- **Ungelesen-Zähler** am Chat-Icon und in der Chatliste: kein Feld in der API.
- **Kontaktperson** im Programmdetail: kein Feld in der API.
- **Status je Kind in der Programmliste** («Noah kommt», «Zusage für Lena offen»): Die Liste liefert keine Kinder, nur das Detail. Die Karte zeigt darum nur «abgesagt» und «wird geprüft».
- **Profilbild, Biometrisches Login, Anwesenheit:** nicht Teil der API-Beschreibung.
- Ebenfalls gewünscht: ein Flag je Nachricht, ob man sie ausblenden darf (heute leitet die App das aus dem Profil ab).

## Grössen und Werte: das Design System gilt, nicht die Mockups

Zwischen dem Design System (`tokens.json`, Brandbook) und den einzelnen Bildschirmen gibt es kleine Abweichungen. **Es gelten immer die Werte des Design Systems**, die Bildschirme zeigen nur Aufbau und Anordnung (Entscheid 2026-10-30). Bekannte Abweichungen in den Bildschirmen, die der Code nicht übernimmt:

- Titelgrösse: Token `headline` 32/38 gilt, nicht 30/36 aus den Bildschirmen.
- `outline`: Token `#8A8A90` (Nacht) gilt, nicht `#74746F`.
- Farben ohne Token (`#C2C3EC`, `#B4B6F5`, `#2C7C91`): nicht übernommen, es gelten die Tokens `accent`, `secondary-container` usw.
- MitleseHinweis im Tag-Theme: Token `secondary-container` `#ECECF8` gilt, nicht `#DFDFF3`.

## Prüfen

`KontrastTest` rechnet die Textpaare der beiden Themen nach (Text 4.5:1, `outline` und `focus` 3:1). Ändert sich ein Wert, läuft der Test mit.
