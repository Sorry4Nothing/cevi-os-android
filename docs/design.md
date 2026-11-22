# Design

Farbwelt der Cevi-Website: Tag auf kühlem Blaugrau (Hauptrichtung), Nacht neutral dunkel, Cevi-Blau für Orientierung, Cevi-Rot für die eine Hauptaktion pro Screen, grosse fette Titel, runde Formen, Karten mit feinem Rand statt Schatten.

## Vorlage (verbindlich)

| Was | Wo |
| --- | --- |
| Design System «Cevi OS Android 2» (Farbwelt Cevi-Website): Farben, Schrift, Abstände, Bausteine | Entwurf des Auftraggebers |
| Bildschirme «Cevi Lager Android»: Anmelden bis Zustände, Termine, Lager, Widgets, je in Tag und Nacht | Entwurf des Auftraggebers |

Beide Entwürfe gehören dem Projekt und sind nicht öffentlich: Zum Lesen braucht es Zugang. Die Werte, die der Code braucht, stehen deshalb auch hier und in den Kommentaren der Theme-Dateien. Weicht der Code vom Entwurf ab, ist das ein Fehler im Code, ausser der Entwurf wurde geändert: Dann zuerst den Entwurf anpassen, dann den Code, und dieses Dokument nachführen.

## Regeln aus dem Brandbook

- **Sprache:** nur Deutsch, Schweizer Schreibweise (ss, «so»), «du», kurze Sätze, Buttons sind Verben, keine Emojis.
- **Farbe:** zwei Themen, **Tag** (Hauptrichtung) und **Nacht**, nach Systemeinstellung, kein Dynamic Color, kein Violett. `accent` mit `onAccent` zeigt, wo man ist (aktiver Dock-Reiter, gewählte Pille, Schalter und Häkchen an) und färbt Links; `secondary` (Cevi-Blau) ist die Fläche für eigene Chat-Blasen und Avatare; `primary` (Rot) nur für die eine Hauptaktion und Zähler. **Kein Zustand nur über Farbe**: immer Icon und Wort.
- **Schrift:** Archivo, mitgeliefert, alles in `sp`.
- **Mass:** jedes bedienbare Element mindestens 48 dp, Haupt-Buttons 56 dp, Felder 52 dp.
- **Barrierefreiheit:** TalkBack liest Karten als ganzen Satz, Titel sind Überschriften, Fokus nach Navigation auf den Titel, Statusänderungen als Live-Region, langes Drücken hat immer eine sichtbare Alternative.
- **Die App zeigt nur, was die API erlaubt.** Fehlt die Aktion in der Antwort, fehlt der Button, er wird nicht ausgegraut.

## Von Design zu Compose

| Design | Compose | Stand |
| --- | --- | --- |
| Farben `tokens.json` (Tag, Nacht) | `ui/theme/Colors.kt`: `ColorScheme` und `CeviColors` (`MaterialTheme.cevi`) | umgesetzt |
| Schrift und Stile | `ui/theme/Typography.kt`, Schrift in `res/font/archivo.ttf` (Lizenz in `assets/licenses`) | umgesetzt |
| Abstände, Grössen, Ecken | `ui/theme/Dimensions.kt` (`Spacing`, `Sizes`, `CeviShapes`, `Pill`) | umgesetzt |
| Theme | `ui/theme/Theme.kt`: `CeviTheme` | umgesetzt |
| Strich-Icons (27, 24 dp, nur Strich, Strichstärke 1.8, Häkchen und Kreuz auch als `_selected` mit 2.6) | `res/drawable/ic_*.xml`, in Compose mit `Icon(painterResource(...), tint = ...)` einfärben | umgesetzt |
| Kopf (`Kopf`, `RundKnopf`) | `ui/Header.kt`: `Header`, `RoundButton` | umgesetzt (#38) |
| Footer-Dock, im Querformat und ab 600 dp senkrecht links | `ui/Dock.kt`: `MainScaffold`, `Destination` (Programme, Chat, Profil) | umgesetzt (#38), Profil mit #68. **Übergänge** (#105, `ui/Motion.kt`): Wechsel der Reiter und der Anmeldestufen überblenden; tiefer (Detail, Zuordnung, Anmeldungen) schiebt ein Sechstel der Breite von rechts herein und blendet über, zurück von links; 260 ms. Ohne Animationen im System (`rememberMotionEnabled`) gibt es keinen Übergang. Beim Abmelden wechselt der Bildschirm ohne Übergang, weil mit der Sitzung auch ihr Speicher weg ist. Das Design legt das nicht fest |
| Termin-Liste (Datumsspalte, Karte), TerminDetail (Kacheln, Fakten; ein offenes Programm liest Stand und Kontakte alle 15 s still neu, #130) | `programs/ProgramsScreens.kt`, `programs/Days.kt` | umgesetzt (#38), Kontakt mit #53 (Namen und «Anrufen» aus `/kontakte`), Stand je Kind auf der Karte (#62) |
| Zusage (Ja/Nein-Pillen) | `programs/RegistrationSection.kt` (`RsvpRow`, `RsvpPill`) | umgesetzt (#38). Der angesagte Name beginnt mit dem sichtbaren Wort («Ja, Vorname», nicht «Vorname, Ja»): Checkliste Barrierefreiheit 1.3 geht dem Entwurf vor |
| StatusChip, Hinweis (Banner), Buttons, Fokusring | `ui/Chips.kt`, `ui/Notice.kt` (`NoticeBanner`), `ui/Buttons.kt` (`PrimaryButton`, `OutlinedPillButton`, `TextActionButton`), `ui/Focus.kt` (`focusRing`), `ui/Loading.kt` (`Spinner`, `SlowHint`) | umgesetzt (#38). **Jede Aktion antwortet** (#71): Die Knöpfe haben `loading` (Ladekreis statt Symbol, Farbe und Text bleiben, kein zweiter Tipp, Screenreader hört «Wird gespeichert» o. ä.); nach 4 Sekunden sagt `SlowHint`, dass es länger dauert. **Ladezustand der ganzen App** (#103): Läuft eine Anfrage, die eine Person ausgelöst hat (`ApiClient.pending`), liegt nach 250 ms `ui/BusyOverlay.kt` über allem (abgedunkelt, Ladekreis, fängt alle Berührungen ab, Fokus weg, Screenreader hört «Einen Moment …»). Hintergrundanfragen (Nachladen von Chat und Liste, «gelesen», Bilder, Stufenlogos) laufen mit `launch(Background())` und sperren nicht. Das Design legt das nicht fest |
| Zustand-Screens (Laden-Skelett, Leer, Offline, Fehler, 404, Kein Zugriff, Sitzung) | `ui/States.kt`: `ErrorState`, `EmptyState`, `LoadingSkeleton` | umgesetzt (#38); «Sitzung abgelaufen» mit Knopf «Anmelden» (`onSignIn`, #68) |
| Bereichswechsel im Kopf (`cv-mode`, B1 bis B4), Menü «Bereich wechseln», Lager-Dock (Start, Chat, Mehr), Blatt «Mehr» (L37) | `ui/Header.kt` (`ModeSwitch`, `LocalModeSwitch`: nur auf den Hauptbildschirmen ohne Zurück-Knopf), `ui/Dock.kt` (`Destination.PROGRAM`, `Destination.CAMP`), `lager/CampScreens.kt` (`ModeMenu`, `MoreSheet`) | umgesetzt (#195). **Abweichung vom Auftrag:** Das Lager ist kein viertes Dock-Ziel, sondern ein eigener Bereich, den der Entwurf über den Kopf wechselt; ohne Lager (Eltern) fehlt die Pille. Das Dock des Lagers zeigt nur Ziele, die es gibt (Start, Aufgaben, Chat, Mehr; Aufgaben seit #197, die Pinnwand kommt mit A2). Der Punkt an der Pille meldet ungelesene Benachrichtigungen eines Lagers, wenn man in den Programmen ist; im Lager hat die API nichts über Neues bei den Programmen. Das Menü ist ein Dialog unter dem Kopf, das Blatt ein `ModalBottomSheet` (beides zeigt der Entwurf) |
| Lagerwahl (L1), Start des Lagers (A0-Fassung ohne Countdown und Aufgaben), Zustände U1 bis U4 und U8 | `lager/CampScreens.kt` (`CampListScreen`, `CampStartScreen`), `ui/States.kt` (`ErrorState` mit `noAccessText`, `homeLabel`; `CardSkeleton`; Seite «Wartung» und «Zweiter Faktor fehlt»), `ui/Notice.kt` | umgesetzt (#195). Der Start zeigt bis A1 nur Zeitraum, Ort und Rolle. Ein Lager ohne eigene Markierung als Übungslager gibt es in der Liste nicht: Die API liefert dort kein Feld dafür |
| Start der Lagerleitung (L2) und des Teams (L3): Countdown oder «Tag X von Y», Fortschritt, Wetterwarnung, eigene Aufgaben, nächster Höck, «Heute im Lager», «Neu für dich» | `lager/StartScreens.kt` (`LeadershipStart`, `TeamStart`, `StartFab`), `lager/CampScreens.kt` (`CampStartScreen` mit `body` und `fab`) | umgesetzt (#197). Die Rolle entscheidet über den Inhalt (`TaskRights.seeLeadershipStart`); «Heute im Lager» zeigt nur Lagertag, Tageschef und Zahl der Dienste (L4 «Heute» gehört zu A4) |
| Aufgaben: Liste mit Abschnitten, Meine (Sortierung, eigene Reihenfolge mit Pfeilen), Personen, Ressort, Kanban (T6), Mehrfachauswahl (L9), Rückgängig, Filter | `lager/TaskScreens.kt`, `lager/TaskUi.kt` (`TaskCard`, `TaskActions`), `lager/TaskLogic.kt` (`TaskRules`), `lager/TasksViewModel.kt`, `ui/PickPill.kt`, `ui/Notice.kt` (`ActionBanner`) | umgesetzt (#197). Kanban ab 720 dp Breite mit Spalten, am Handy Reiter pro Status; verschoben wird über das Menü («Verschieben nach …», Pfeile), nie nur durch Ziehen. Änderungen laufen durch die Warteschlange (`TaskChanges.kt`) und sind sofort sichtbar |
| Aufgabe (L7), Schnelleingabe (L8, L10), Ressort-Seite (L35) | `lager/TaskDetailScreen.kt`, `lager/QuickCaptureSheet.kt`, `lager/TaskDialogs.kt`, `lager/RessortScreens.kt` | umgesetzt (#197). **Fehlt:** «Frühere Fassungen» (der Server speichert keine Inhalte früherer Stände), «Link kopieren» (der Vertrag kennt keine Adresse einer Aufgabe), Feedback und Dokumente im Ressort (A2, A5). Das Team sieht keine Checkliste (die Details-Route ist für das Team 404) |
| Warteschlange (U6), Konflikt (U7), Banner «Ohne Netz · n Änderungen warten» | `lager/OfflineQueue.kt`, `lager/QueueScreens.kt` (`QueueScreen`, `ConflictScreen`), `lager/CampScreens.kt` (`QueueBanner`), `ui/Chips.kt` (`ChipKind.WAITING`, `SENT`) | umgesetzt (#195). Knöpfe wie im Entwurf: «Meine behalten», «Aktuelle übernehmen» (der Auftrag nennt «Meine übernehmen», «Aktuelle behalten»); «Beide ansehen» entfällt, weil beide Fassungen schon nebeneinander stehen. «Zusammenführen» gibt es nicht: Es braucht Feldregeln je Modul. Die Warteschlange lebt nur im Arbeitsspeicher (siehe `QueueStore`): Ein Entscheid des Nutzers zum lokalen Speicher steht aus |
| Splash und Start-Animation (der Entwurf zeigt nur den Startbildschirm «Start») | `res/drawable/ic_splash_logo.xml` (Logo; das kleine Symbol der Push-Mitteilungen ist die weisse Silhouette des Cevi-Logos, `res/drawable-*/ic_stat_logo.png`, aus der Monochrom-Ebene des App-Symbols, #138; das App-Symbol ist ein eigenes, vom Auftraggeber geliefertes Symbol in `res/mipmap-*`, #100), `ic_splash_animated.xml` mit `res/animator/splash_fade_*` (Band für Band, ab Android 12), `splash_window.xml` (Android 8–11), Theme `Theme.CeviOS.Starting`, `ui/Motion.kt` (`rememberMotionEnabled`); das Logo gleitet auf `StartScreen` herein | umgesetzt (#70). Mit ausgeschalteten System-Animationen steht das Logo ohne Bewegung da (Checkliste 5.9). Die Animation hält das Laden nicht auf |
| ChatZeile (Karten, Abschnitte «Gruppen» und «Mit der Stufenleitung») | `chat/ChatScreens.kt`: `ChatListScreen`, `ChatCard`, `ChatAvatar` | umgesetzt (#39), Zähler «ungelesen» (#52), Vorschau der letzten Nachricht mit Zeit (#62), Stufenlogo statt Gruppen-Icon im Gruppenchat (#94), ebenso vor dem Untertitel im Kopf des Gruppenchats, in den Stufen-Etiketten (`GroupChip`) der Programmkarte und des Programmdetails und in der Stufenwahl der Zuordnung: `ui/StageLogo.kt`, Pfade aus `GET /api/v1/stufen` (`images/StageLogos.kt`), ohne Hintergrund und Rahmen eingepasst (#104), ohne Logo das Cevi-Logo (`ic_stage_fallback`, im Nachtmodus mit hellerem Blau). **Ausnahme Etikett:** Es zeigt das Logo nur bei einer Stufe mit eigenem Logo (sonst bliebe es wie bisher; das Cevi-Logo wäre auf jedem Etikett Wiederholung), nie bei «Alle Stufen» (cevi-os#213). Grössen: 56 dp in der Chatliste, 24 dp im Kopf, 20 dp im Etikett, 40 dp in der Zuordnung. Alle Logos sind dekorativ, der Stufenname steht daneben. Ohne Logo: Einzelchat (Avatar «SL»), Knopf «Einzelchat starten», Widget und Push (nur Text, kein Bild ladbar), Hinweis auf feste Stufen in der Zuordnung. Kontakte, Anmeldungen und Profil nennen keine Stufe und zeigen kein Logo |
| ChatBlase (eigene, andere, Tagestrenner), Aktionen per langem Drücken und `customActions` | `chat/ChatScreens.kt`: `MessageBubble`, `DayDividerText`; `chat/ConversationItems.kt` (`withDayDividers`, `dayLabel`); `ui/DashedBorder.kt` (`dashedBorder`) | umgesetzt (#39), Rolle «· SL» am Namen (#62); das Ausblenden und die Darstellung ausgeblendeter Nachrichten gibt es nicht mehr (#64, #66) |
| Avatar (32 dp bei fremden Blasen, Initiale auf `secondary`) | `ui/Avatar.kt`: `Avatar` | umgesetzt (#49); mit #80 zeigt er das Profilbild (`imageUrl`, geladen über `images/ProfileImages.kt`, nur im Arbeitsspeicher der Sitzung), sonst die Initiale. Der Kreis wächst mit der Schrift (`scaledWithFont`), die Schrift hat kein Limit (Checkliste 5.3) |
| ChatEingabe | `chat/ChatScreens.kt`: `MessageInput` | umgesetzt (#39) |
| MitleseHinweis | `chat/ChatScreens.kt`: `ReadAlongNote` | umgesetzt (#39), Avatare einfarbig `secondary` (Farben ohne Token nicht übernommen) |
| Dialog (Auswahl), gestrichelter Knopf | `ui/Dialog.kt` (`CeviDialog`, `DialogText`), `ui/Buttons.kt` (`DashedButton`) | umgesetzt (#39); Bestätigungsdialog und Danger-Knopf entfielen mit dem Ausblenden (#64) |
| Hinweis (Aktionen und Senden), Zustände (Laden, Fehler, Leer) im Chat | `ui/Notice.kt`, `ui/States.kt`, `chat/ChatScreens.kt` (`ActionNotice`) | umgesetzt (#39) |
| Zuordnung (Stufen und Sichtbarkeit); ein Programm ohne Stufe gilt für «Alle Stufen» (leere `stufen`), einen Chip «Nicht zugeordnet» gibt es nicht mehr | `programs/AssignmentScreen.kt`, `programs/Assignment.kt` (Regeln), `programs/Program.kt` (`allAgeGroups`) | umgesetzt (#15, #173), **ohne Vorlage in den Bildschirmen**: aus Karten, Hinweis und Buttons zusammengesetzt; Knopf mit Text im Abschnitt «Für Leitende» (Stift-Icon fehlt im Design). Ergänzungen (Treffpunkt, Schluss, Mitnehmen, Info) bearbeitet die App nicht: Das geschieht zentral auf der Webseite |
| Textfeld (Label oben, 52 dp, Radius 12) | `ui/TextField.kt`: `LabeledTextField` | umgesetzt (#53), erste Verwendung bei der Telefonnummer; mit dem Login (#68) dazu: Passwort (`masked`, Auge als `trailing`), `autofill` für den Passwort-Manager, `imeAction` |
| Kontakt (Karte mit Cevi-Name als Titel, darunter dem vollen Namen, der Nummer und «Anrufen»; ohne Cevi-Namen nur der volle Name, #120), «Meine Telefonnummer» | `contacts/ContactViews.kt`, `contacts/PhoneScreen.kt`, `contacts/Contacts.kt` (Regeln) | umgesetzt (#53), **ohne Vorlage in den Bildschirmen**: aus Karten, Hinweis, Textfeld und Buttons zusammengesetzt; die Nummer ist antippbar (Akzentfarbe, so hoch wie die Schrift; der grosse Treffer bleibt «Anrufen») und öffnet die Telefon-App mit der Nummer vorgewählt, der Knopf «Anrufen» bleibt (#124); der Gruppenchat hat keinen Kontakt-Knopf mehr (#114, die Kontakte der Stufenleitung stehen im Programm); die Nummer steht in der Chatliste unter «Meine Telefonnummer» |
| Start, Login, Passwort ändern, Willkommen | `auth/AuthScreens.kt`: `StartScreen`, `LoginScreen`, `PasswordScreen`, `WelcomeScreen`; Zustand in `auth/AuthViewModel.kt` | umgesetzt (#68). Abweichungen vom Entwurf: **kein Feld «Passwort wiederholen»** (Checkliste 9.7 geht vor, das Auge ersetzt es); dafür das Feld «Bisheriges Passwort» im freiwilligen Wechsel (der Server verlangt es), jedes Passwortfeld mit eigenem Auge. Im Pflichtwechsel nach dem Login entfällt das Feld: Die App behält das Passwort des Logins nur im Arbeitsspeicher, bis das neue gespeichert ist (#102); als Regeln nur, was der Server prüft (12 Zeichen, anders als das bisherige); «Passwort vergessen?» ist ein Hinweis, kein Link (es gibt keinen Reset); «Kenntnis genommen» statt «einverstanden» (die API hält eine Kenntnisnahme fest, keine Einwilligung); das Logo schrumpft oder entfällt bei offener Tastatur und grosser Schrift |
| Profil | `auth/ProfileScreen.kt` | umgesetzt (#68): Name, Rolle, Kinder, Benutzername, Passwort ändern, Datenschutz (öffnet `/datenschutz` im Browser, kein eigener Text in der App), Abmelden. Kein «Konto löschen»; Profilbild mit Kamera-Knopf am Bild (Auswahl über die Systemauswahl, die App dreht, lässt den quadratischen Ausschnitt verschieben und zoomen, verkleinert und sendet als JPEG ohne Metadaten) und «Profilbild entfernen» (#80), **Entsperren mit Fingerabdruck oder Gesicht** (#106, ohne Vorlage in den Bildschirmen): Schalter «Mit Fingerabdruck oder Gesicht entsperren» (`SwitchRow`) im Profil und, nur direkt nach dem eigenen Passwort, im Willkommen; nur sichtbar, wenn das Gerät eine starke Biometrie eingerichtet hat. Eingeschaltet zeigt die App nach dem Start den Sperrbildschirm (`auth/AuthScreens.kt`: `LockedScreen`, Zustand `AuthState.Locked`), fragt mit `BiometricPrompt` (`security/BiometricPrompter.kt`) und fragt den Server erst nach dem Ausweis; «Mit Passwort anmelden» beendet die Sitzung. Der Ausweis ist ein Keystore-Schlüssel (`security/AppLock.kt`, nur nach starker Biometrie, bei jeder Benutzung neu, ungültig bei neuer Biometrie); das ist eine Sperre der App, die Sitzung selbst bleibt wie bisher im Keystore verschlüsselt. Die App sperrt sich beim Start des Prozesses und, wenn sie mindestens 30 Sekunden im Hintergrund war, beim Zurückkehren (#112; die Daten der Sitzung verlassen dabei den Arbeitsspeicher, die Sitzung bleibt). Eingeschaltet sind Bildschirmfotos und die Vorschau unter «Zuletzt benutzt» gesperrt (`FLAG_SECURE`). Der Schalter hat nur seinen Titel, keinen Hinweistext. Das Entsperren bleibt nach dem Abmelden an (#116); ohne Sitzung fragt die App nicht, und wer sich mit dem Passwort anmeldet, gilt als entsperrt. Es endet mit dem Schalter oder wenn sich die Biometrie des Geräts ändert; «Benachrichtigungen» (Schalter für Chat, Programme, Anmeldungen; #88, #97). **Bei offener App zeigt die App den Push selbst** (`push/PushService.kt`, #142), weil Firebase es nur im Hintergrund tut; Ausnahme: Der betroffene Chat ist offen. Das kleine Symbol ist das Cevi-Logo (#138). «Meine Telefonnummer» steht weiter in der Chatliste |
| Datenschutz (eigener Bildschirm im Entwurf) | – | nicht umgesetzt: Die Erklärung steht auf dem Backend und öffnet im Browser |
| Anmeldungen (SL): Balken mit Ja/Nein/offen, Reiter Kinder und Leitende, Liste mit Stand | `programs/Registrations.kt` (Modelle, `RegistrationsRules`), `programs/RegistrationsScreen.kt`; Einstieg im Abschnitt «Für Leitende» des Programms | umgesetzt (#82), nur zum Ansehen: Leitende ändern keine Antworten (Entscheid des Auftraggebers). Der Entwurf zeigt Chips «Ja», «Nein», «Offen»; die Zahlen darüber sagen dasselbe Wort. Die Dock-Beschriftung schrumpft bei grösster Schrift (`autoSize`), weil drei Reiter die Breite teilen |

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
| Mitlesen | `chat_read_along` (Text) |
| Sitzung, Speicher, Herkunft | `Session`, `Storage`, `Origin` (`SessionStorage`, `SessionInterceptor`) |
| Fehler, Laden, Leer, Zustand | `Error` (`ApiError`, `ErrorPage`, `ApiResult.Failure`), `Loading`, `Empty`, `State` |
| Lager, Lagerwahl, Änderung in der Warteschlange | `Camp` (`CampApi`, `CampViewModel`, `CampRole`), Paket `lager`, `QueuedChange` (`OfflineQueue`, `QueueState`) |
| Kopf, Dock | `Header`, `Dock` |
| Knöpfe | `PrimaryButton`, `OutlinedPillButton`, `TextActionButton`, `DashedButton`, `RoundButton`, `HeaderButton` |
| Hinweis | `Notice` (`NoticeBanner`) |
| Fokus, Fokusring | `Focus`, `focusRing` |
| Abstände, Grössen, Formen | `Spacing`, `Sizes`, `CeviShapes` |
| Farben, Nacht, Tag | `CeviColors`, `CeviNight`, `CeviDay` |
| Schrift | `CeviTypography` |
| Zeitleiste, Kachel, Fakten | `Timeline`, `Tile`, `Facts` |
| Aktion | `Action` (`ActionState`, `ActionMessage`) |

## Was der Entwurf zeigt, die API aber (noch) nicht liefert

Die API wird entsprechend erweitert (Entscheid 2026-10-30). Bis dahin nichts erfinden: Der Baustein bleibt weg, die Stelle ist mit dem Backend-Issue verknüpft.

- **Ungelesen-Zähler am Chat-Icon im Dock (#163):** die Summe der `ungelesen` aus der Chatliste. Es gibt keinen eigenen Endpunkt dafür: Die App liest die Liste einmal still neu, wenn der Reiter wechselt oder ein Chat zugeht; im Takt (alle 30 Sekunden) fragt sie weiter nur, solange die Chatliste sichtbar ist. Auf den anderen Reitern kann der Zähler deshalb bis zum nächsten Wechsel veraltet sein.
- **Cevi-Name (#120):** Im Chat steht überall der Cevi-Name (`ChatPerson.displayName`), ohne ihn der volle Name; so bei Autoren, Vorschau, Titel des Einzelchats, «liest mit» und im Aktionsmenü. Nur die Kontaktkarte zeigt beides. Das Profil zeigt den vollen Namen und darunter «Cevi-Name: …», wenn einer gesetzt ist. Anmeldungen (Leitende) zeigen das Profilbild (`bildUrl`, #136; Kinder haben keins und zeigen die Initiale) und den Cevi-Namen als Titel und darunter «voller Name · Rolle» (#122); Kinder haben keinen Cevi-Namen.
- **Profilbild (#80):** Bilder erscheinen im Chat bei Autoren, bei Kontakten und im Profil. In der Chatliste bleiben die Initialen: Der Vertrag liefert dort kein `bildUrl`. Die App baut keine Adressen selbst (`personen/{id}/bild`), sie nimmt nur die `bildUrl` der API und nur, wenn sie ein Profilbild nennt.
- **Biometrisches Login:** nicht Teil der API-Beschreibung.

## Grössen und Werte: das Design System gilt, nicht die Mockups

Zwischen dem Design System (`tokens.json`, Brandbook) und den einzelnen Bildschirmen gibt es kleine Abweichungen. **Es gelten immer die Werte des Design Systems**, die Bildschirme zeigen nur Aufbau und Anordnung (Entscheid 2026-10-30). Bekannte Abweichungen in den Bildschirmen, die der Code nicht übernimmt:

- Titelgrösse: Token `headline` 32/38 gilt, nicht 30/36 aus den Bildschirmen.

## Ausrichtung

Die App läuft nur im Hochformat (`screenOrientation="portrait"`, Entscheid 2026-10-30). Das weicht von der Checkliste Barrierefreiheit 5.8 ab (Querformat bedienbar). Auf grossen Bildschirmen (ab 600 dp, Tablet, Faltgerät) ignoriert Android die Sperre, dort gelten das Dock links und die Tastaturregel im Chat. Wer das Telefon nur quer halten kann (zum Beispiel an einer Halterung), kann die App nicht bedienen: Das ist als offener Punkt im PR von #17 genannt.

## Prüfen

`ContrastTest` rechnet die Textpaare der beiden Themen nach (Text 4.5:1, `outline` und `focus` 3:1). Ändert sich ein Wert, läuft der Test mit.
