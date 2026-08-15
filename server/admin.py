import functools

from flask import Blueprint, request, session, redirect, url_for, render_template_string
from werkzeug.security import generate_password_hash, check_password_hash

from extensions import db
from models import User, new_id

admin_bp = Blueprint("admin", __name__, url_prefix="/admin")


@admin_bp.route("/")
def index():
    return redirect(url_for("admin.login"))


LAYOUT = """
<!doctype html>
<html lang="de">
<head>
  <meta charset="utf-8">
  <title>Tododl Admin</title>
  <style>
    body { font-family: -apple-system, sans-serif; max-width: 720px; margin: 40px auto; color: #222; }
    table { width: 100%; border-collapse: collapse; margin-top: 16px; }
    th, td { text-align: left; padding: 8px; border-bottom: 1px solid #ddd; }
    input { padding: 6px 8px; margin: 4px 0; width: 100%; box-sizing: border-box; }
    button { padding: 8px 16px; margin-top: 8px; cursor: pointer; }
    .error { color: #b00020; }
    .row { display: flex; gap: 8px; align-items: center; }
    form.inline { display: inline; }
    nav a { margin-right: 16px; }
  </style>
</head>
<body>
  {{ body|safe }}
</body>
</html>
"""

LOGIN_BODY = """
<h1>Tododl Admin-Login</h1>
{% if error %}<p class="error">{{ error }}</p>{% endif %}
<form method="post">
  <label>Nutzername</label>
  <input name="username" required>
  <label>Passwort</label>
  <input name="password" type="password" required>
  <button type="submit">Anmelden</button>
</form>
"""

SETUP_BODY = """
<h1>Willkommen bei Tododl</h1>
<p>Es existiert noch kein Admin-Nutzer auf diesem Server. Lege hier den
ersten Admin-Account an - danach kannst du unter <code>/admin/users</code>
weitere Nutzer anlegen.</p>
{% if error %}<p class="error">{{ error }}</p>{% endif %}
<form method="post">
  <label>Nutzername</label>
  <input name="username" required>
  <label>Name</label>
  <input name="name" required>
  <label>Passwort</label>
  <input name="password" type="password" required>
  <button type="submit">Admin anlegen</button>
</form>
"""

USERS_BODY = """
<nav><a href="{{ url_for('admin.logout') }}">Abmelden</a></nav>
<h1>Nutzerverwaltung</h1>
{% if error %}<p class="error">{{ error }}</p>{% endif %}

<h2>Neuen Nutzer anlegen</h2>
<form method="post" action="{{ url_for('admin.create_user') }}">
  <label>Nutzername</label>
  <input name="username" required>
  <label>Name</label>
  <input name="name" required>
  <label>Passwort</label>
  <input name="password" type="password" required>
  <label class="row"><input type="checkbox" name="is_admin" style="width:auto"> Admin-Rechte (darf sich hier einloggen)</label>
  <button type="submit">Anlegen</button>
</form>

<h2>Bestehende Nutzer</h2>
<table>
  <tr><th>Nutzername</th><th>Name</th><th>Admin</th><th></th></tr>
  {% for u in users %}
  <tr>
    <td>{{ u.username }}</td>
    <td>{{ u.name }}</td>
    <td>{{ "Ja" if u.is_admin else "Nein" }}</td>
    <td>
      <form class="inline" method="post" action="{{ url_for('admin.delete_user', user_id=u.id) }}"
            onsubmit="return confirm('Nutzer {{ u.username }} wirklich löschen?');">
        <button type="submit">Löschen</button>
      </form>
    </td>
  </tr>
  {% endfor %}
</table>
"""


def _render(body_template: str, **kwargs) -> str:
    body = render_template_string(body_template, **kwargs)
    return render_template_string(LAYOUT, body=body)


def require_admin(view):
    @functools.wraps(view)
    def wrapped(*args, **kwargs):
        user_id = session.get("admin_user_id")
        user = User.query.get(user_id) if user_id else None
        if not user or not user.is_admin:
            return redirect(url_for("admin.login"))
        return view(*args, **kwargs)
    return wrapped


@admin_bp.route("/login", methods=["GET", "POST"])
def login():
    # Noch kein einziger Nutzer auf diesem Server? -> Erstinstallation.
    if User.query.count() == 0:
        return redirect(url_for("admin.setup"))

    error = None
    if request.method == "POST":
        username = request.form.get("username", "")
        password = request.form.get("password", "")
        user = User.query.filter_by(username=username).first()

        if user and user.is_admin and check_password_hash(user.password_hash, password):
            session["admin_user_id"] = user.id
            return redirect(url_for("admin.users"))

        error = "Nutzername/Passwort falsch oder keine Admin-Rechte."

    return _render(LOGIN_BODY, error=error)


@admin_bp.route("/setup", methods=["GET", "POST"])
def setup():
    # Sobald irgendein Nutzer existiert, ist die Erstinstallation vorbei -
    # diese Route ist dann nicht mehr erreichbar (verhindert, dass sich
    # jemand nachträglich als "erster Admin" einschleicht).
    if User.query.count() > 0:
        return redirect(url_for("admin.login"))

    error = None
    if request.method == "POST":
        username = request.form.get("username", "").strip()
        name = request.form.get("name", "").strip()
        password = request.form.get("password", "")

        if not username or not name or not password:
            error = "Alle Felder sind Pflicht."
        else:
            user = User(
                id=new_id(),
                username=username,
                name=name,
                password_hash=generate_password_hash(password),
                is_admin=True,
            )
            db.session.add(user)
            db.session.commit()
            session["admin_user_id"] = user.id
            return redirect(url_for("admin.users"))

    return _render(SETUP_BODY, error=error)


@admin_bp.route("/logout")
def logout():
    session.pop("admin_user_id", None)
    return redirect(url_for("admin.login"))


@admin_bp.route("/users")
@require_admin
def users():
    all_users = User.query.order_by(User.username).all()
    return _render(USERS_BODY, users=all_users, error=None)


@admin_bp.route("/users", methods=["POST"])
@require_admin
def create_user():
    username = request.form.get("username", "").strip()
    name = request.form.get("name", "").strip()
    password = request.form.get("password", "")
    is_admin = request.form.get("is_admin") == "on"

    if not username or not name or not password:
        return _render(USERS_BODY, users=User.query.order_by(User.username).all(), error="Alle Felder sind Pflicht.")

    if User.query.filter_by(username=username).first():
        return _render(
            USERS_BODY, users=User.query.order_by(User.username).all(),
            error=f"Nutzername '{username}' existiert bereits."
        )

    user = User(
        id=new_id(),
        username=username,
        name=name,
        password_hash=generate_password_hash(password),
        is_admin=is_admin,
    )
    db.session.add(user)
    db.session.commit()

    return redirect(url_for("admin.users"))


@admin_bp.route("/users/<string:user_id>/delete", methods=["POST"])
@require_admin
def delete_user(user_id):
    # Sich selbst löschen wäre unpraktisch (man fliegt aus der eigenen Session raus,
    # ohne dass jemand anderes noch Admin-Zugriff hat, falls man der einzige war).
    if user_id == session.get("admin_user_id"):
        return _render(
            USERS_BODY, users=User.query.order_by(User.username).all(),
            error="Du kannst dich nicht selbst löschen."
        )

    User.query.filter_by(id=user_id).delete()
    db.session.commit()
    return redirect(url_for("admin.users"))
