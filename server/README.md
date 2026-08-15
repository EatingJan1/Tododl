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

Läuft dann auf `http://127.0.0.1:5001`. Beim allerersten Öffnen von
`http://127.0.0.1:5001/admin` (noch kein Nutzer vorhanden) erscheint
automatisch eine Einrichtungsseite, auf der du den ersten Admin-Account
anlegst - kein Terminal-Kommando nötig. Danach normaler Login unter
`/admin/login`, weitere Nutzer über `/admin/users`.

Swagger-UI zum manuellen Testen der API: `http://127.0.0.1:5001/docs`.

Die SQLite-Datei `tododl_server.db` wird beim ersten Start automatisch im
`server/`-Ordner angelegt.

## Nutzerverwaltung

Es gibt **keine öffentliche Registrierung** mehr. Der erste Nutzer (Admin)
wird einmalig über die Einrichtungsseite unter `/admin` angelegt, sobald der
Server zum ersten Mal läuft und noch keine Nutzer in der Datenbank sind.
Alle weiteren Nutzer legt dieser Admin über `/admin/users` an (Nutzername,
Name, Passwort, optional Admin-Rechte).

Alternativ geht das Anlegen des ersten Admins auch weiterhin per Skript
(z. B. für automatisiertes Deployment): `python create_admin.py <username> <name> <passwort>`.
Das Skript funktioniert nur, solange noch kein Nutzer existiert - danach ist
`/admin/setup` gesperrt und nur noch `/admin/users` (mit Login) nutzbar.

Das Login-Feld heißt `username` (nicht E-Mail) - sowohl im API-Login
(`POST /auth/login`) als auch überall dort, wo eine Person zum
Einladen/Freigeben angegeben wird (`username` statt `email` im Request-Body).

## Kurzer manueller Test

```bash
# Login (Token aus der Antwort kopieren) - Nutzer vorher per create_admin.py oder /admin/users anlegen
curl -X POST http://127.0.0.1:5001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"jan","password":"mein-passwort"}'

# Projekt anlegen (TOKEN ersetzen)
curl -X POST http://127.0.0.1:5001/projects \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Büro Umzug"}'
```

## Endpunkte im Überblick

| Methode | Pfad | Zweck |
|---|---|---|
| POST | `/auth/login` | Login, liefert JWT |
| GET | `/admin/login`, `/admin/users` | Admin-Weboberfläche (Session-basiert, kein JWT) |
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

