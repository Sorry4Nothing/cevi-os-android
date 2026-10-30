# Cevi OS Android

Native Android (Kotlin, Jetpack Compose)-App für Cevi OS: Programme aus dem Google-Kalender, Ja/Nein-Anmeldung pro Kind und Chat zwischen Eltern und Stufenleitung.

Das Backend liegt in [Sorry4Nothing/cevi-os](https://github.com/Sorry4Nothing/cevi-os). Die API-Beschreibung steht dort unter `docs/api/`.

## Stand

Der Aufbau beginnt. Das Gerüst (Compose, Material 3, `minSdk` 26) und die CI stehen. Die frühere Expo-App im Backend-Repo (`apps/mobile`) dient nur als Referenz für Verhalten und Texte.

## Design

Aussehen und Bildschirme der App kommen aus zwei Entwürfen. Sie sind die verbindliche Vorlage; weicht der Code ab, gilt der Entwurf (Änderungen zuerst dort):

- **Design System «Cevi App»** (Farben, Schrift, Bausteine): <https://claude.ai/artifact/9mAd5832pLn8DCnG8AAY8e>
- **Bildschirme «Cevi App Screens»** (Nacht und Tag, alle Zustände): <https://claude.ai/artifact/3590108e-92e4-4a95-8442-a12e98ac9287>

Wie der Entwurf in Compose umgesetzt ist, steht in [`docs/design.md`](docs/design.md).

## Bauen

Android Studio oder Gradle mit JDK 17 oder neuer:

```
./gradlew assembleDebug lintDebug testDebugUnitTest
```

Die Adresse des Servers kommt nicht aus dem Repo, sondern lokal aus `~/.gradle/gradle.properties` oder per `-PceviApiBaseUrl=https://…`. Ohne Angabe gilt der Platzhalter `https://api.example.org`.

## Demodaten (nur Debug)

Ohne Login zeigt die App sonst nur Fehlerseiten. Im Debug-Build antwortet deshalb der `DemoServer` anstelle des Netzwerks, mit erfundenen Daten in der Form der API: eine Elternperson mit zwei Kindern, sechs Programme, ein Gruppenchat mit 55 Nachrichten und ein Hilfetext.

- Automatisch an, wenn `ceviApiBaseUrl` fehlt. Mit echter Adresse aus. Im Release-Build nie (der Code liegt nur unter `app/src/debug`).
- Mit `-PdemoLeader` (zum Beispiel `./gradlew installDebug -PdemoLeader`) ist die Demo-Person Stufenleitung der Demo-Stufe: Sie darf Ergänzungen und Zuordnung ändern, «Geländespiel» ist keiner Stufe zugeordnet, eine zweite Stufe ist nicht änderbar.
- Alles liegt im Speicher und ist nach dem Neustart der App zurückgesetzt. Ändern lässt sich: Zu- und Absagen für die Kinder, Nachrichten senden, den Einzelchat öffnen, Nachrichten melden. Ausblenden und die Anmeldung als Leitung antworten mit 403.

## Push

Push-Nachrichten laufen über Firebase Cloud Messaging (FCM).

## Mitwirken

- Branches: `main` und `develop`. Änderungen gehen als Pull Request nach `develop`.
- Pro Issue ein Branch (`feature/<nr>-<kurzname>`, für Fehler `fix/<nr>-<kurzname>`) und ein Pull Request.
- Nie ins Repo, in Issues oder PRs: echte Namen, Kalender-ID, iCal-Adresse, Zugangsdaten, Serverzugang, `.env`-Inhalte.
