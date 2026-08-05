from flask_restx import Namespace, Resource, fields
from flask_jwt_extended import jwt_required, get_jwt_identity

from extensions import db
from models import MindCard, Node, now_ms
from permissions import has_at_least

api = Namespace("mindcards", description="Mindboard-Karten eines Panels")

card_model = api.model("MindCardRequest", {
    "id": fields.String(required=True),
    "text": fields.String(required=True),
    "colorHex": fields.String(required=False),
    "posX": fields.Float(required=False, default=0),
    "posY": fields.Float(required=False, default=0),
})


def _require_role_via_panel(panel_id: str, user_id: str, min_role: str) -> Node:
    node = Node.query.get_or_404(panel_id)
    if not has_at_least(node.project_id, user_id, min_role):
        api.abort(404, "Panel nicht gefunden oder keine ausreichende Berechtigung")
    return node


@api.route("/<string:panel_id>/mind-cards")
class MindCardList(Resource):
    @jwt_required()
    def get(self, panel_id):
        user_id = get_jwt_identity()
        _require_role_via_panel(panel_id, user_id, "VIEWER")
        cards = MindCard.query.filter_by(panel_id=panel_id).all()
        return [c.to_dict() for c in cards], 200


@api.route("/<string:panel_id>/mind-cards/<string:card_id>")
class MindCardDetail(Resource):
    @jwt_required()
    @api.expect(card_model)
    def put(self, panel_id, card_id):
        user_id = get_jwt_identity()
        _require_role_via_panel(panel_id, user_id, "EDITOR")

        data = api.payload
        card = MindCard.query.get(card_id)
        if card is None:
            card = MindCard(id=card_id, panel_id=panel_id)
            db.session.add(card)

        card.text = data["text"]
        card.color_hex = data.get("colorHex")
        card.pos_x = data.get("posX", 0)
        card.pos_y = data.get("posY", 0)
        card.updated_at = now_ms()
        db.session.commit()

        return card.to_dict(), 200

    @jwt_required()
    def delete(self, panel_id, card_id):
        user_id = get_jwt_identity()
        _require_role_via_panel(panel_id, user_id, "EDITOR")

        MindCard.query.filter_by(id=card_id, panel_id=panel_id).delete()
        db.session.commit()
        return "", 204
