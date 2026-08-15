"""
Legt den allerersten Admin-Nutzer an - einmalig nötig, weil es keine
öffentliche Registrierung mehr gibt. Danach kann sich der Admin unter
/admin/login einloggen und dort weitere Nutzer anlegen.

Verwendung:
    python create_admin.py <username> <name> <passwort>
"""
import sys

from werkzeug.security import generate_password_hash

from app import create_app
from extensions import db
from models import User, new_id


def main():
    if len(sys.argv) != 4:
        print("Verwendung: python create_admin.py <username> <name> <passwort>")
        sys.exit(1)

    username, name, password = sys.argv[1], sys.argv[2], sys.argv[3]

    app = create_app()
    with app.app_context():
        if User.query.filter_by(username=username).first():
            print(f"Nutzer '{username}' existiert bereits.")
            sys.exit(1)

        user = User(
            id=new_id(),
            username=username,
            name=name,
            password_hash=generate_password_hash(password),
            is_admin=True,
        )
        db.session.add(user)
        db.session.commit()
        print(f"Admin '{username}' angelegt. Login unter http://127.0.0.1:5001/admin/login")


if __name__ == "__main__":
    main()
