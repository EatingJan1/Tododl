# Tododl Projektserver

Flask-RESTX + SQLite. Läuft komplett lokal auf dem Mac, keine externe DB nötig.

## Setup (macOS)

```bash
cd server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
```

Läuft dann auf `http://127.0.0.1:5001`. Swagger-UI zum manuellen Testen:
`http://127.0.0.1:5001/docs`

Die SQLite-Datei `tododl_server.db` wird beim ersten Start automatisch im
`server/`-Ordner angelegt.

## Kurzer manueller Test

```bash
# Registrieren
curl -X POST http://127.0.0.1:5001/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"jan@example.com","password":"test1234","name":"Jan"}'

# Login (Token aus der Antwort kopieren)
curl -X POST http://127.0.0.1:5001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"jan@example.com","password":"test1234"}'

# Projekt anlegen (TOKEN ersetzen)
curl -X POST http://127.0.0.1:5001/projects \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Büro Umzug"}'
```

## Endpunkte im Überblick

| Methode | Pfad | Zweck |
|---|---|---|
| POST | `/auth/register` | Registrieren |
| POST | `/auth/login` | Login, liefert JWT |
| GET | `/projects` | Eigene Server-Projekte (direkt oder über Gruppe) |
| POST | `/projects` | Neues Server-Projekt (macht dich zum Owner) |
| PUT | `/projects/<id>` | Projekt umbenennen etc. (min. EDITOR) |
| DELETE | `/projects/<id>` | Löschen (nur OWNER) |
| GET | `/projects/<id>/access` | Alle Berechtigungen (User + Gruppen) ansehen |
| POST | `/projects/<id>/access/user` | Einzelperson per E-Mail eine Rolle geben (nur OWNER) |
| POST | `/projects/<id>/access/group` | Gruppe eine Rolle geben (nur OWNER) |
| DELETE | `/projects/<id>/access/<accessId>` | Berechtigung entziehen (nur OWNER) |
| GET | `/projects/<id>/nodes` | Kompletten Node-Baum abrufen (min. VIEWER) |
| PUT | `/projects/<id>/nodes/<nodeId>` | Ordner/Panel anlegen oder updaten (min. EDITOR) |
| PATCH | `/projects/<id>/nodes/<nodeId>` | Nur verschieben (min. EDITOR) |
| DELETE | `/projects/<id>/nodes/<nodeId>` | Löschen (min. EDITOR) |
| GET/PUT/DELETE | `/panels/<panelId>/todo-items[/<id>]` | Todo-Einträge |
| GET/PUT/DELETE | `/panels/<panelId>/mind-cards[/<id>]` | Mindboard-Karten |
| GET | `/groups` | Eigene Gruppen |
| POST | `/groups` | Neue Gruppe (Ersteller = ADMIN der Gruppe) |
| DELETE | `/groups/<id>` | Gruppe löschen (nur Gruppen-ADMIN) |
| GET | `/groups/<id>/members` | Gruppenmitglieder ansehen |
| POST | `/groups/<id>/members` | Mitglied hinzufügen (nur Gruppen-ADMIN) |
| DELETE | `/groups/<id>/members/<userId>` | Mitglied entfernen |

Auth: alle Endpunkte außer `/auth/*` brauchen den Header
`Authorization: Bearer <accessToken>`.

## Rollenmodell

Jedes Projekt hat Berechtigungseinträge (`ProjectAccess`), die entweder einer
**Person direkt** oder einer **ganzen Gruppe** eine Rolle geben:

- `VIEWER` – nur lesen
- `EDITOR` – lesen + Nodes/Todos/Mindcards bearbeiten
- `OWNER` – zusätzlich: Projekt löschen, Berechtigungen vergeben/entziehen

Die effektive Rolle eines Users auf einem Projekt ist das **Maximum** aus
allen zutreffenden Einträgen (direkt + über alle seine Gruppen). Ist jemand
z. B. `VIEWER` direkt, aber Mitglied einer Gruppe mit `EDITOR`-Zugriff, gilt
`EDITOR`.

Gruppen sind unabhängig von Projekten (serverweit) und können auf mehrere
Projekte gleichzeitig freigegeben werden - praktisch für z. B. "Vorstand"
oder "IT-Team", die auf mehrere Projekte Zugriff brauchen.

## Wichtig für Produktion (aktuell bewusst simpel gehalten)

- `JWT_SECRET_KEY` ist ein Dev-Default - für echten Einsatz per Umgebungsvariable
  `TODODL_JWT_SECRET` setzen.
- CORS ist komplett offen (`CORS(app)`), fürs lokale Testen praktisch, für
  einen öffentlich erreichbaren Server einschränken.
- Kein Rate-Limiting, kein Passwort-Reset, kein Refresh-Token.
- Benötigt Python 3.10+ (wegen `str | None`-Typannotationen in `permissions.py`/`models.py`).

