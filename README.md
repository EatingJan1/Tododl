# Tododl

TODO-App mit Kotlin Multiplatform. Struktur: **Bereich** → **Projekt** → **Node**
(Ordner, verschachtelbar, oder Panel: Todoliste / Mindboard).

## Setup (macOS)

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

## Projektstruktur

```
Tododl/
├── shared/                  # commonMain: Models, Repositories, SQLDelight-Schema
│   ├── commonMain/
│   │   ├── kotlin/de/tododl/shared/model/       # Bereich, Projekt, Node, TodoItem, MindCard
│   │   ├── kotlin/de/tododl/shared/repository/  # Interfaces + lokale SQLDelight-Implementierung
│   │   ├── kotlin/de/tododl/shared/db/          # expect DatabaseDriverFactory
│   │   └── sqldelight/de/tododl/shared/db/      # .sq Dateien = DB-Schema + Queries
│   └── desktopMain/                              # actual DatabaseDriverFactory (JDBC SQLite)
└── desktopApp/               # Compose-Desktop-App (dein Test-Target auf macOS)
    └── desktopMain/kotlin/de/tododl/desktop/
        ├── Main.kt            # Einstiegspunkt, Koin-Start
        ├── navigation/        # Screen-Definitionen + Backstack
        ├── ui/                # Bereich-/Projekt-/Node-Screens, Panels
        └── ui/theme/          # Compose-Theme
```

## Datenmodell

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

## Server-Sharing (vorbereitet, noch nicht implementiert)

`Projekt.source` unterscheidet bereits zwischen `LOCAL` und `SERVER`
(mit `serverId`). Die App spricht ausschließlich über die
`ProjektRepository`/`NodeRepository`-Interfaces mit den Daten – sobald ein
Ktor-Backend existiert, kann eine `RemoteProjektRepository`-Implementierung
(mit lokalem Cache für Offline-Betrieb) ergänzt werden, ohne dass UI oder
Navigation angefasst werden müssen.

## Nächste sinnvolle Schritte

- [ ] `./gradlew :desktopApp:run` einmal ausführen, Grundfunktionen testen
- [ ] Drag & Drop / Umsortieren von Nodes (Reihenfolge ist schon in der DB vorbereitet: `position`)
- [ ] Ktor-Server-Modul für Projekt-Sharing
- [ ] iOS-Target in `shared/build.gradle.kts` aktivieren, sobald Xcode-Test ansteht
- [ ] Android-Target + `androidApp`-Modul aktivieren
