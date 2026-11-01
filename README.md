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

Eine signierte APK für den Pilot baut man nach `docs/release.md` (Schlüssel und Passwörter ebenfalls nur lokal).

## Push

Push-Nachrichten laufen über Firebase Cloud Messaging (FCM).

## Mitwirken

- Branches: `main` und `develop`. Änderungen gehen als Pull Request nach `develop`.
- Pro Issue ein Branch (`feature/<nr>-<kurzname>`, für Fehler `fix/<nr>-<kurzname>`) und ein Pull Request.
- Nie ins Repo, in Issues oder PRs: echte Namen, Kalender-ID, iCal-Adresse, Zugangsdaten, Serverzugang, `.env`-Inhalte.
