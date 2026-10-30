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
| Farben `tokens.json` (Nacht, Tag) | `ui/theme/Colors.kt`: `ColorScheme` und `CeviColors` (`MaterialTheme.cevi`) | umgesetzt |
| Schrift und Stile | `ui/theme/Typography.kt`, Schrift in `res/font/archivo.ttf` (Lizenz in `assets/licenses`) | umgesetzt |
| Abstände, Grössen, Ecken | `ui/theme/Dimensions.kt` (`Spacing`, `Sizes`, `CeviShapes`, `Pill`) | umgesetzt |
| Theme | `ui/theme/Theme.kt`: `CeviTheme` | umgesetzt |
| Strich-Icons (27, 24 dp, nur Strich, Strichstärke 1.8, Häkchen und Kreuz auch als `_selected` mit 2.6) | `res/drawable/ic_*.xml`, in Compose mit `Icon(painterResource(...), tint = ...)` einfärben | umgesetzt |
| Kopf (`Kopf`, `RundKnopf`) | `ui/Header.kt`: `Header`, `RoundButton` | umgesetzt (#38) |
| Footer-Dock, im Querformat und ab 600 dp senkrecht links | `ui/Dock.kt`: `MainScaffold`, `Destination` (Programme, Chat; Profil kommt mit dem Profil) | umgesetzt (#38) |
| Termin-Liste (Datumsspalte, Karte), TerminDetail (Kacheln, Fakten) | `programs/ProgramsScreens.kt`, `programs/Days.kt` | umgesetzt (#38), Kontakt mit #53 (Namen und «Anrufen» aus `/kontakte`) |
| Zusage (Ja/Nein-Pillen) | `programs/RegistrationSection.kt` (`RsvpRow`, `RsvpPill`) | umgesetzt (#38). Der angesagte Name beginnt mit dem sichtbaren Wort («Ja, Lena», nicht «Lena, Ja»): Checkliste Barrierefreiheit 1.3 geht dem Entwurf vor |
| StatusChip, Hinweis (Banner), Buttons, Fokusring | `ui/Chips.kt`, `ui/Notice.kt` (`NoticeBanner`), `ui/Buttons.kt` (`PrimaryButton`, `OutlinedPillButton`, `TextActionButton`), `ui/Focus.kt` (`focusRing`) | umgesetzt (#38) |
| Zustand-Screens (Laden-Skelett, Leer, Offline, Fehler, 404, Kein Zugriff, Sitzung) | `ui/States.kt`: `ErrorState`, `EmptyState`, `LoadingSkeleton` | umgesetzt (#38); «Anmelden» erst mit dem Login |
| ChatZeile (Karten, Abschnitte «Gruppen» und «Mit der Stufenleitung») | `chat/ChatScreens.kt`: `ChatListScreen`, `ChatCard`, `ChatAvatar` | umgesetzt (#39), Zähler «ungelesen» mit #52; ohne letzte Nachricht und Uhrzeit (API) |
| ChatBlase (eigene, andere, ausgeblendete, Tagestrenner), Aktionen per langem Drücken und `customActions` | `chat/ChatScreens.kt`: `MessageBubble`, `DayDividerText`; `chat/ConversationItems.kt` (`withDayDividers`, `dayLabel`); `ui/DashedBorder.kt` (`dashedBorder`) | umgesetzt (#39), ohne Rolle des Absenders |
| Avatar (32 dp bei fremden Blasen, Initiale auf `secondary`) | `ui/Avatar.kt`: `Avatar` | umgesetzt (#49), zeigt die Initiale; für ein Profilbild vorbereitet (`imageUrl`). Der Kreis wächst mit der Schrift (`scaledWithFont`), die Schrift hat kein Limit (Checkliste 5.3) |
| ChatEingabe | `chat/ChatScreens.kt`: `MessageInput` | umgesetzt (#39) |
| MitleseHinweis | `chat/ChatScreens.kt`: `ReadAlongNote` | umgesetzt (#39), Avatare einfarbig `secondary` (Farben ohne Token nicht übernommen) |
| Dialog (Auswahl, Bestätigung), Danger- und gestrichelter Knopf | `ui/Dialog.kt` (`CeviDialog`, `DialogText`, `initialFocus`), `ui/Buttons.kt` (`DangerButton`, `DashedButton`) | umgesetzt (#39) |
| Hinweis (Aktionen und Senden), Zustände (Laden, Fehler, Leer) im Chat | `ui/Notice.kt`, `ui/States.kt`, `chat/ChatScreens.kt` (`ActionNotice`) | umgesetzt (#39) |
| Zuordnung (Stufen und Sichtbarkeit), Chip «Nicht zugeordnet» | `programs/AssignmentScreen.kt`, `programs/Assignment.kt` (Regeln), `ui/Chips.kt` (`UNASSIGNED`) | umgesetzt (#15), **ohne Vorlage in den Bildschirmen**: aus Karten, Hinweis und Buttons zusammengesetzt; Knopf mit Text im Abschnitt «Für Leitende» (Stift-Icon fehlt im Design). Ergänzungen (Treffpunkt, Schluss, Mitnehmen, Info) bearbeitet die App nicht: Das geschieht zentral auf der Webseite |
| Textfeld (Label oben, 52 dp, Radius 12) | `ui/TextField.kt`: `LabeledTextField` | umgesetzt (#53), erste Verwendung bei der Telefonnummer; ohne Passwort-Auge (kommt mit dem Login) |
| Kontakt (Karte mit Name, Nummer und «Anrufen»), Dialog «Kontakt» im Gruppenchat, «Meine Telefonnummer» | `contacts/ContactViews.kt`, `contacts/PhoneScreen.kt`, `contacts/Contacts.kt` (Regeln) | umgesetzt (#53), **ohne Vorlage in den Bildschirmen**: aus Karten, Hinweis, Textfeld und Buttons zusammengesetzt; der Knopf zum Anrufen nutzt das Symbol `ic_phone` im Kopf des Gruppenchats; die Nummer steht in der Chatliste unter «Hilfe und Vertrauenspersonen», bis es ein Profil gibt |
| Login, Passwort, Willkommen, Profil, Datenschutz | – | mit den jeweiligen Issues (Login, Profil) |
| Anmeldungen und Anwesenheit (Leitende) | – | Epic «Leitende», nicht begonnen |

## Namen: Deutsch → Englisch

Bezeichner im Code sind Englisch, Texte (`strings.xml`) und Kommentare Deutsch; Feldnamen der API bleiben über `@SerialName` erhalten.

| Design-Baustein (Deutsch) | Compose-Name |
| --- | --- |
| Programm, Programme | `Program`, `ProgramList`, Paket `programs`, `ProgramsApi`, `ProgramsViewModel` |
| Stufe | `AgeGroup` (`ChatAgeGroup`, `ProgramAgeGroup`, `ProfileAgeGroup`) |
| Stufenleitung (SL) | `GroupLeader` (`groupLeaders`, `isGroupLeader`) |
| Leitung, Leitende | `Leader` |
| Kind, Eltern | `Child`, `Parent` |
| Anmeldung | `Registration` (`RegistrationSection`, `ownRegistration`) |
| Zusage, Antwort (Ja/Nein) | `Rsvp` (`RsvpRow`, `RsvpPill`, `RsvpState`); Server-Antworten heissen `Response` |
| Pille | `Pill` (Form `Pill`, Baustein `RsvpPill`) |
| Nachricht, Verlauf | `Message`, `Conversation` (`ChatConversationScreen`) |
| Chatliste, Einzelchat, Gruppenchat | `ChatList`, `ChatListScreen`, `openDirectChat`, `isGroup` |
| Hilfe, Vertrauenspersonen | `Help`, `trustedPersons` |
| Ausblenden, Mitlesen | `hide`, `chat_read_along` (Text) |
| Sitzung, Speicher, Herkunft | `Session`, `Storage`, `Origin` (`SessionStorage`, `SessionInterceptor`) |
| Fehler, Laden, Leer, Zustand | `Error` (`ApiError`, `ErrorPage`, `ApiResult.Failure`), `Loading`, `Empty`, `State` |
| Kopf, Dock | `Header`, `Dock` |
| Knöpfe | `PrimaryButton`, `OutlinedPillButton`, `TextActionButton`, `DashedButton`, `DangerButton`, `RoundButton`, `HeaderButton` |
| Hinweis | `Notice` (`NoticeBanner`) |
| Fokus, Fokusring | `Focus`, `focusRing` |
| Abstände, Grössen, Formen | `Spacing`, `Sizes`, `CeviShapes` |
| Farben, Nacht, Tag | `CeviColors`, `CeviNight`, `CeviDay` |
| Schrift | `CeviTypography` |
| Zeitleiste, Kachel, Fakten | `Timeline`, `Tile`, `Facts` |
| Aktion | `Action` (`ActionState`, `ActionMessage`) |

## Was der Entwurf zeigt, die API aber (noch) nicht liefert

Die API wird entsprechend erweitert (Entscheid 2026-10-30). Bis dahin nichts erfinden: Der Baustein bleibt weg, die Stelle ist mit dem Backend-Issue verknüpft.

- **Ungelesen-Zähler am Chat-Icon im Dock:** gibt es nicht. Die API zählt je Chat (`ungelesen`), die Chatliste zeigt es; die App fragt sie nur, solange sie sichtbar ist (alle 30 Sekunden).
- **Letzte Nachricht und Uhrzeit** in der Chatzeile: kein Feld in der Chatliste der API.
- **Rolle des Absenders** («Sarah · SL») über der Blase: kein Feld in der Nachricht; die App zeigt nur den Namen.
- **Status je Kind in der Programmliste** («Noah kommt», «Zusage für Lena offen»): Die Liste liefert keine Kinder, nur das Detail. Die Karte zeigt darum nur «abgesagt» und «wird geprüft».
- **Profilbild:** kein Feld in der API. Der Avatar zeigt bis dahin die Initiale; `Avatar` hat den Parameter `imageUrl` schon, lädt aber nichts. Das Bild kommt mit dem Feld der API.
- **Biometrisches Login, Anwesenheit:** nicht Teil der API-Beschreibung.
- Ebenfalls gewünscht: ein Flag je Nachricht, ob man sie ausblenden darf (heute leitet die App das aus dem Profil ab).

## Grössen und Werte: das Design System gilt, nicht die Mockups

Zwischen dem Design System (`tokens.json`, Brandbook) und den einzelnen Bildschirmen gibt es kleine Abweichungen. **Es gelten immer die Werte des Design Systems**, die Bildschirme zeigen nur Aufbau und Anordnung (Entscheid 2026-10-30). Bekannte Abweichungen in den Bildschirmen, die der Code nicht übernimmt:

- Titelgrösse: Token `headline` 32/38 gilt, nicht 30/36 aus den Bildschirmen.
- `outline`: Token `#8A8A90` (Nacht) gilt, nicht `#74746F`.
- Farben ohne Token (`#C2C3EC`, `#B4B6F5`, `#2C7C91`): nicht übernommen, es gelten die Tokens `accent`, `secondary-container` usw.
- MitleseHinweis im Tag-Theme: Token `secondary-container` `#ECECF8` gilt, nicht `#DFDFF3`.

## Ausrichtung

Die App läuft nur im Hochformat (`screenOrientation="portrait"`, Entscheid 2026-10-30). Das weicht von der Checkliste Barrierefreiheit 5.8 ab (Querformat bedienbar). Auf grossen Bildschirmen (ab 600 dp, Tablet, Faltgerät) ignoriert Android die Sperre, dort gelten das Dock links und die Tastaturregel im Chat. Wer das Telefon nur quer halten kann (zum Beispiel an einer Halterung), kann die App nicht bedienen: Das ist als offener Punkt im PR von #17 genannt.

## Prüfen

`ContrastTest` rechnet die Textpaare der beiden Themen nach (Text 4.5:1, `outline` und `focus` 3:1). Ändert sich ein Wert, läuft der Test mit.
