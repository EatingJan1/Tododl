from flask_restx import Namespace, Resource, fields
from flask_jwt_extended import create_access_token
from werkzeug.security import generate_password_hash, check_password_hash

from extensions import db
from models import User

api = Namespace("auth", description="Registrierung und Login")

register_model = api.model("RegisterRequest", {
    "email": fields.String(required=True),
    "password": fields.String(required=True),
    "name": fields.String(required=True),
})

login_model = api.model("LoginRequest", {
    "email": fields.String(required=True),
    "password": fields.String(required=True),
})


@api.route("/register")
class Register(Resource):
    @api.expect(register_model)
    def post(self):
        data = api.payload
        if User.query.filter_by(email=data["email"]).first():
            api.abort(409, "E-Mail bereits registriert")

        user = User(
            email=data["email"],
            name=data["name"],
            password_hash=generate_password_hash(data["password"]),
        )
        db.session.add(user)
        db.session.commit()

        token = create_access_token(identity=user.id)
        return {"accessToken": token, "user": user.to_dict()}, 201


@api.route("/login")
class Login(Resource):
    @api.expect(login_model)
    def post(self):
        data = api.payload
        user = User.query.filter_by(email=data["email"]).first()
        if not user or not check_password_hash(user.password_hash, data["password"]):
            api.abort(401, "E-Mail oder Passwort falsch")

        token = create_access_token(identity=user.id)
        return {"accessToken": token, "user": user.to_dict()}, 200
