from flask_restx import Namespace, Resource, fields
from flask_jwt_extended import create_access_token
from werkzeug.security import check_password_hash

from models import User

api = Namespace("auth", description="Login (Nutzer werden vom Admin über /admin angelegt)")

login_model = api.model("LoginRequest", {
    "username": fields.String(required=True),
    "password": fields.String(required=True),
})


@api.route("/login")
class Login(Resource):
    @api.expect(login_model)
    def post(self):
        data = api.payload
        user = User.query.filter_by(username=data["username"]).first()
        if not user or not check_password_hash(user.password_hash, data["password"]):
            api.abort(401, "Nutzername oder Passwort falsch")

        token = create_access_token(identity=user.id)
        return {"accessToken": token, "user": user.to_dict()}, 200
