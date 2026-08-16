# 📝 Tododl – Mach deine To-Dos zum Dudeln! 🎶✨

> Die Open-Source-To-Do-App, die Chaos in Struktur verwandelt – lokal, intelligent und datenschutzfreundlich.

<p align="center">
  <img src="https://img.shields.io/badge/License-AGPL_3.0-yellow.svg" alt="License">
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20iOS%20%7C%20Android%20%7C%20Desktop-blue" alt="Platforms">
  <img src="https://img.shields.io/badge/Status-In%20Entwicklung-brightgreen" alt="Status">
</p>

## 🌟 Vision

Die meisten Aufgaben landen heute überall: Erinnerungen-Apps, Notizen, PDFs,
Screenshots, E-Mails, Messenger, Sprachmemos, Einkaufslisten, Kalender.
Tododl soll diese Informationen an einem Ort zusammenführen und strukturiert
darstellen - organisiert in **Bereiche** (Verein/Privat/Arbeit), darin
**Projekte**, darin beliebig verschachtelte **Ordner** und **Panels**
(Todoliste, Mindboard).

**Unsere Philosophie:** Klare Struktur statt Chaos, mit voller Kontrolle über
die eigenen Daten. Alles läuft standardmäßig lokal; Sync über einen eigenen
Projektserver ist optional und du entscheidest, welchem Server du vertraust
(Firma, Verein, privat - beliebig viele gleichzeitig).

> Hinweis zum aktuellen Stand: Eine frühere Version dieser Vision beschrieb
> einen Apple-only-Ansatz mit SwiftUI/CloudKit/Apple Intelligence. Die
> tatsächliche Umsetzung geht bewusst einen anderen Weg - siehe unten - um
> von Anfang an plattformübergreifend (macOS, später iOS/Android/Windows/Linux)
> und ohne Cloud-Zwang zu funktionieren.

---

## 🧩 Aktueller Stand (technisch)

Tododl ist eine TODO-App mit **Kotlin Multiplatform**. Struktur:
**Bereich** → **Projekt** → **Node** (Ordner, verschachtelbar, oder Panel:
Todoliste / Mindboard).

### Setup (macOS)

Voraussetzungen: JDK 17+ (z. B. `brew install openjdk@17`), Android Studio
(Koala+) oder IntelliJ IDEA mit Kotlin-Multiplatform-Plugin.

```bash
cd Tododl
chmod +x gradlew   # falls das Ausführungsrecht beim Zip fehlt
./gradlew :desktopApp:run
```

Das startet die Compose-Desktop-App direkt als natives macOS-Fenster – kein
Simulator, kein Android-Emulator nötig. So kannst du während der gesamten
Entwicklung auf dem Mac testen, bevor iOS/Android-Targets dazukommen.

Falls `gradlew` fehlt (manche Zip-Tools lassen ihn weg), einmalig erzeugen:

```bash
gradle wrapper --gradle-version 8.9
```

(Setzt eine lokale Gradle-Installation voraus, nur für diesen einen Schritt.)

### Projektstruktur

```
Tododl/
├── shared/                  # commonMain: Models, Repositories, SQLDelight-Schema
│   ├── commonMain/
│   │   ├── kotlin/de/tododl/shared/model/       # Bereich, Projekt, Node, TodoItem, MindCard, ServerConnection
│   │   ├── kotlin/de/tododl/shared/repository/  # Interfaces + lokale SQLDelight-Implementierung
│   │   ├── kotlin/de/tododl/shared/remote/      # API-Client, DTOs, SyncManager (Server-Sharing)
│   │   ├── kotlin/de/tododl/shared/db/          # expect DatabaseDriverFactory
│   │   └── sqldelight/de/tododl/shared/db/      # .sq Dateien = DB-Schema + Queries
│   └── desktopMain/                              # actual DatabaseDriverFactory (JDBC SQLite), HttpClientFactory (CIO)
├── desktopApp/               # Compose-Desktop-App (dein Test-Target auf macOS)
│   └── desktopMain/kotlin/de/tododl/desktop/
│       ├── Main.kt            # Einstiegspunkt, Koin-Start
│       ├── navigation/        # Screen-Definitionen + Backstack
│       ├── ui/                # Bereich-/Projekt-/Node-Screens, Panels, Server-/Gruppen-Verwaltung
│       └── ui/theme/          # Compose-Theme
└── server/                    # Flask-RESTX + SQLite Backend für Projekt-Sharing (optional)
```

### Datenmodell

```
Bereich (Verein / Privat / Arbeit)
 └── Projekt (Maibaum aufstellen 2026 / Urlaub Kroatien / ...)
      └── Node (rekursiv verschachtelbar)
           ├── Ordner        → enthält weitere Nodes
           └── Panel
                ├── TodoListPanel  → TodoItems
                └── MindboardPanel → MindCards (freie Positionierung)
```

Die Datenbank liegt lokal unter `~/.tododl/tododl.db` (SQLite).

### Server-Sharing (Firma/Privat/Verein gleichzeitig, mit Gruppen & Rollen)

`Projekt.source` unterscheidet `LOCAL`/`SERVER`, `Projekt.serverConnectionId`
verweist auf die konkrete Server-Verbindung (siehe `ServerConnection`-Tabelle) -
so kannst du gleichzeitig mit einem Firmen-, Privat- und Vereinsserver
arbeiten, ohne dass sich Projekte in die Quere kommen. Jede Verbindung hat
ihren eigenen Login (Nutzername/Passwort, eigener Server).

Auf jedem Server gibt es zusätzlich **Gruppen** (z. B. "Vorstand",
"IT-Team") - serverweit, unabhängig von einzelnen Projekten. Ein Projekt
kann sowohl einzelnen Personen als auch ganzen Gruppen eine Rolle geben
(`VIEWER`/`EDITOR`/`OWNER`); die stärkste zutreffende Rolle gewinnt. Nutzer
werden ausschließlich vom Admin angelegt (kein öffentliches Self-Signup) -
beim allerersten Start eines Servers führt `/admin` automatisch durch die
Einrichtung des ersten Admin-Accounts. Details zum Rollenmodell und Setup:
`server/README.md`.

**Client-seitig:**
- "Meine Server" (Cloud-Icon auf der Bereich-Liste) verwaltet die Liste der
  verbundenen Server und führt zur Gruppen-Verwaltung je Server.
- Beim Projekt-Anlegen wählst du den Speicherort (lokal oder einer der
  verbundenen Server).
- Über den Teilen-Button auf der Projekt-Karte gibst du das Projekt an eine
  Person oder eine Gruppe frei, mit wählbarer Rolle.

---

## 🧠 Ideen für später (aus der ursprünglichen Vision, noch nicht umgesetzt)

Diese Punkte stammen aus der ersten Konzeptphase und sind bewusst noch nicht
Teil der aktuellen Architektur - als Inspiration für spätere Ausbaustufen,
plattformübergreifend statt Apple-exklusiv gedacht:

- **Universal Inbox**: PDF, Screenshot, Bild, Text, Audio oder Link
  reinwerfen - automatische Erkennung von Aufgaben, Terminen, Projekten
- **PDF/Screenshot → Aufgaben**: automatische Aufgabenerkennung aus
  Dokumenten und Chat-Screenshots
- **Sprachaufnahme → Aufgaben**: gesprochene Notizen in Todo-Listen umwandeln
- **Meeting Assistant**: Entscheidungen, Aufgaben, Verantwortliche und
  Deadlines aus Audioaufnahmen extrahieren
- **Smart Priorities / Smart Project Detection**: lokale Auswertung, was
  gerade wichtig ist bzw. wie Aufgaben/Notizen/Termine zusammenhängen
- Integrationen mit externen Kalendern/Dateiablagen (Format offen, nicht auf
  ein Ökosystem festgelegt)

Wichtig: Sollte KI-Auswertung ergänzt werden, bleibt die Philosophie
bestehen: lokal, kein Zwang zur Cloud, keine Übernahme von Entscheidungen -
KI soll Chaos in Struktur bringen, nicht für einen denken.

## 🛣️ Nächste sinnvolle Schritte

- [ ] `./gradlew :desktopApp:run` einmal ausführen, Grundfunktionen testen
- [ ] Backend starten (`server/README.md`) und einen Server in "Meine Server" hinzufügen
- [ ] Drag & Drop / Umsortieren von Nodes (Reihenfolge ist schon in der DB vorbereitet: `position`)
- [ ] Automatischer Hintergrund-Sync statt manuellem Push/Pull (z. B. WebSocket)
- [ ] iOS-Target in `shared/build.gradle.kts` aktivieren, sobald Xcode-Test ansteht
- [ ] Android-Target + `androidApp`-Modul aktivieren
- [ ] Universal Inbox / KI-Grundlagen (siehe oben) als eigenes Modul angehen

## 🤝 Mitmachen

Beiträge sind willkommen - gesucht werden Kotlin-/Compose-Entwickler,
UI/UX-Feedback, Tester und Ideenlieferanten.

## 📜 Lizenz

Dieses Projekt wird unter der AGPLv3 veröffentlicht. Weitere Informationen
befinden sich in der Datei `LICENSE`.

---

> Produktivität muss nicht langweilig sein. Tododl soll der Ort werden, an
> dem Gedanken, Aufgaben und Projekte zusammenfinden - egal ob privat, im
> Verein oder in der Firma, und egal auf welchem Server.
