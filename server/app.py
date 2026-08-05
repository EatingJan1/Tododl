import os

from flask import Flask
from flask_cors import CORS
from flask_restx import Api

from extensions import db, jwt
from auth import api as auth_ns
from projects import api as projects_ns
from nodes import api as nodes_ns
from todos import api as todos_ns
from mindcards import api as mindcards_ns
from groups import api as groups_ns


def create_app():
    app = Flask(__name__)

    db_path = os.path.join(os.path.dirname(__file__), "tododl_server.db")
    app.config["SQLALCHEMY_DATABASE_URI"] = f"sqlite:///{db_path}"
    app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False
    app.config["JWT_SECRET_KEY"] = os.environ.get("TODODL_JWT_SECRET", "dev-secret-change-me")

    CORS(app)  # für lokale Tests von überall erlaubt; für Produktion einschränken
    db.init_app(app)
    jwt.init_app(app)

    api = Api(
        app,
        version="1.0",
        title="Tododl Projektserver",
        description="Backend zum Teilen von Tododl-Projekten mit anderen Personen",
        doc="/docs",  # Swagger-UI zum manuellen Testen: http://127.0.0.1:5001/docs
    )

    api.add_namespace(auth_ns, path="/auth")
    api.add_namespace(projects_ns, path="/projects")
    api.add_namespace(nodes_ns, path="/projects")
    api.add_namespace(todos_ns, path="/panels")
    api.add_namespace(mindcards_ns, path="/panels")
    api.add_namespace(groups_ns, path="/groups")

    with app.app_context():
        db.create_all()

    return app


if __name__ == "__main__":
    app = create_app()
    app.run(host="127.0.0.1", port=5001, debug=True)
