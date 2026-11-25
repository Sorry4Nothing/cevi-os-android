# Cevi OS Android

Native Android (Kotlin, Jetpack Compose)-App für Cevi OS: Termine aus dem persönlichen Kalender, Ja/Nein-Anmeldung pro Kind, Chat zwischen Eltern und Stufenleitung, das Lager für Leitende und Widgets.

Das Backend liegt in [Sorry4Nothing/cevi-os](https://github.com/Sorry4Nothing/cevi-os). Die API-Beschreibung steht dort unter `docs/api/`.

## Stand

Pilot-Version 0.9.0 (Vorabversion) als signierte APK im [GitHub-Release](https://github.com/Sorry4Nothing/cevi-os-android/releases). Die Veröffentlichung im Play Store folgt (Epic #7). Die frühere Expo-App im Backend-Repo (`apps/mobile`) diente nur als Referenz für Verhalten und Texte.

## Design

Aussehen und Bildschirme der App kommen aus zwei Entwürfen des Auftraggebers. Sie sind die verbindliche Vorlage; weicht der Code ab, gilt der Entwurf (Änderungen zuerst dort):

- **Design System «Cevi OS Android 2»** (Farbwelt Cevi-Website: Farben, Schrift, Bausteine)
- **Bildschirme «Cevi Lager Android»** (Tag und Nacht, alle Zustände)

Die Entwürfe sind nicht öffentlich; Zugang gibt der Auftraggeber.

Wie der Entwurf in Compose umgesetzt ist, steht in [`docs/design.md`](docs/design.md).

## Bereiche

- **Programme und Termine:** «Mein Kalender» mit den Terminen, die die Person sieht (`GET /api/v1/termine/mein`), Ja/Nein pro Kind, «Zum Kalender hinzufügen», Anlass-Anmeldung, Termin vorschlagen und der persönliche Kalenderlink im Profil. Termine ohne Gruppe erscheinen nicht (die Zuordnung läuft über Tags in Outlook). **Räume:** «Raum anfragen» und «Meine Raumanfragen» im Profil.
- **Lager:** ein eigener Bereich, den die Pille im Kopf wechselt (nur für Personen mit Lagerrolle). Aufgaben, Höcks und Protokolle, Pinnwand, Eingang, Einteilung (Zimmer, Fahrten, Gruppen, Schichtplan, Diensttausch, Abzählen), Heute, Anwesenheit, Einkauf, Packliste, Notfall, Infos, Zeitplan, Dokumente, Adressbuch, Suche und Ausdrucke. Die Knöpfe folgen den Rechten aus `GET /api/v1/lager/{id}` (`rechte`); fehlt eine Aktion in der Antwort, fehlt der Knopf, der Server prüft trotzdem jede Anfrage. Übungslager stehen für sich. Aufgaben, der Haken «anwesend» im Höck-Modus und «Ruhiges Ressort» gehen durch eine Warteschlange (`lager/OfflineQueue.kt`), alles Übrige direkt an den Server.
- **Widgets:** sechs Stück (Nächstes Programm, Mein Kalender, Lager heute, Meine Aufgaben, Notfall, Chat) als RemoteViews im Paket `widget`. Keine Kindernamen, keine Chat-Inhalte, keine Telefonnummern.
- **Kein dauerhafter lokaler Speicher für Daten:** Warteschlange, Stand der Lagerbereiche und Widget-Stand liegen nur im Arbeitsspeicher und gehen mit der Sitzung. Auf dem Gerät bleibt nur die Wahl der Stufe oder des Lagers je Widget (Kennung, kein Name). Der Entscheid zum Speicher steht aus.
- Nur Hochformat. Der Zustand der einzelnen Bildschirme und die Abweichungen vom Entwurf stehen in [`docs/design.md`](docs/design.md).

## Bauen

Android Studio oder Gradle mit JDK 17 oder neuer:

```
./gradlew assembleDebug lintDebug testDebugUnitTest
```

Die Adresse des Servers kommt nicht aus dem Repo, sondern lokal aus `~/.gradle/gradle.properties` oder per `-PceviApiBaseUrl=https://…`. Ohne Angabe gilt der Platzhalter `https://api.example.org`.

**App-Nachweis (Play Integrity):** Bei der Anmeldung belegt die App mit Google Play Integrity, dass sie die echte App auf einem echten Gerät ist (Ablauf: `docs/app-nachweis.md` im Backend). Dafür braucht der Build die Projektnummer des Google-Cloud-Projekts, ebenfalls nur lokal: `ceviPlayCloudProject=123456789012` in `~/.gradle/gradle.properties` oder `-PceviPlayCloudProject=…`. Ohne Angabe bleibt der Nachweis aus; die App meldet sich dann ohne Ticket an, was der Server nur im Modus `aus` oder `pruefen` zulässt. Der Nachweis ist abwärtskompatibel: Schaltet der Server ihn aus, antwortet die Herausforderung 404 und die App meldet sich wie bisher an. Die Anfrage geht an die Play-Dienste des Geräts; ohne sie lässt sich eine App mit Nachweis nicht anmelden.

Eine signierte APK für den Pilot baut man nach `docs/release.md` (Schlüssel und Passwörter ebenfalls nur lokal).

## Push

Push-Nachrichten laufen über Firebase Cloud Messaging (FCM).

## Mitwirken

- Branches: `main` und `develop`. Änderungen gehen als Pull Request nach `develop`.
- Pro Issue ein Branch (`feature/<nr>-<kurzname>`, für Fehler `fix/<nr>-<kurzname>`) und ein Pull Request.
- Nie ins Repo, in Issues oder PRs: echte Namen, Kalender-ID, iCal-Adresse, Zugangsdaten, Serverzugang, `.env`-Inhalte.
