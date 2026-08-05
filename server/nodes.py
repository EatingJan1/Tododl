from flask_restx import Namespace, Resource, fields
from flask_jwt_extended import jwt_required, get_jwt_identity

from extensions import db
from models import Node, now_ms
from permissions import has_at_least

api = Namespace("nodes", description="Nodes (Ordner/Panels) eines Projekts")

node_model = api.model("NodeRequest", {
    "id": fields.String(required=True),
    "parentId": fields.String(required=False),
    "type": fields.String(required=True),  # ORDNER | PANEL_TODOLIST | PANEL_MINDBOARD
    "title": fields.String(required=True),
    "icon": fields.String(required=False),
    "position": fields.Integer(required=False, default=0),
})

move_model = api.model("MoveNodeRequest", {
    "parentId": fields.String(required=False),
    "position": fields.Integer(required=True),
})


def _require_role(project_id: str, user_id: str, min_role: str):
    if not has_at_least(project_id, user_id, min_role):
        api.abort(404, "Projekt nicht gefunden oder keine ausreichende Berechtigung")


@api.route("/<string:project_id>/nodes")
class NodeList(Resource):
    @jwt_required()
    def get(self, project_id):
        """Kompletten Node-Baum als flache Liste (Client baut die Hierarchie über parentId)."""
        user_id = get_jwt_identity()
        _require_role(project_id, user_id, "VIEWER")
        nodes = Node.query.filter_by(project_id=project_id).order_by(Node.position).all()
        return [n.to_dict() for n in nodes], 200


@api.route("/<string:project_id>/nodes/<string:node_id>")
class NodeDetail(Resource):
    @jwt_required()
    @api.expect(node_model)
    def put(self, project_id, node_id):
        user_id = get_jwt_identity()
        _require_role(project_id, user_id, "EDITOR")

        data = api.payload
        node = Node.query.get(node_id)
        if node is None:
            node = Node(id=node_id, project_id=project_id)
            db.session.add(node)

        node.parent_id = data.get("parentId")
        node.type = data["type"]
        node.title = data["title"]
        node.icon = data.get("icon")
        node.position = data.get("position", 0)
        node.updated_at = now_ms()
        db.session.commit()

        return node.to_dict(), 200

    @jwt_required()
    @api.expect(move_model)
    def patch(self, project_id, node_id):
        """Nur verschieben (Drag&Drop), ohne alle Felder erneut zu senden."""
        user_id = get_jwt_identity()
        _require_role(project_id, user_id, "EDITOR")

        node = Node.query.get_or_404(node_id)
        data = api.payload
        node.parent_id = data.get("parentId")
        node.position = data["position"]
        node.updated_at = now_ms()
        db.session.commit()

        return node.to_dict(), 200

    @jwt_required()
    def delete(self, project_id, node_id):
        user_id = get_jwt_identity()
        _require_role(project_id, user_id, "EDITOR")

        Node.query.filter_by(id=node_id, project_id=project_id).delete()
        db.session.commit()
        return "", 204
