from flask_restx import Namespace, Resource, reqparse
from flask_jwt_extended import jwt_required

from models import User

api = Namespace("users", description="Nutzersuche (z. B. für @-Mentions)")

search_parser = reqparse.RequestParser()
search_parser.add_argument("q", type=str, required=False, default="", location="args")


@api.route("/search")
class UserSearch(Resource):
    @jwt_required()
    @api.expect(search_parser)
    def get(self):
        """
        Sucht Nutzer nach (Teil-)Nutzernamen, für die @-Mention-Autovervollständigung
        in Markdown-Seiten. Bewusst ohne weitere Einschränkung (jeder eingeloggte
        Nutzer darf andere Nutzernamen dieses Servers sehen) - für ein privates/
        Firmen-/Vereins-Setup ist das normalerweise unproblematisch.
        """
        args = search_parser.parse_args()
        query = (args.get("q") or "").strip()

        q = User.query
        if query:
            q = q.filter(User.username.ilike(f"%{query}%"))

        users = q.order_by(User.username).limit(20).all()
        return [u.to_dict() for u in users], 200
