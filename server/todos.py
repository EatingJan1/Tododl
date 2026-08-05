from flask_restx import Namespace, Resource, fields
from flask_jwt_extended import jwt_required, get_jwt_identity

from extensions import db
from models import TodoItem, Node, now_ms
from permissions import has_at_least

api = Namespace("todos", description="Todoliste-Einträge eines Panels")

item_model = api.model("TodoItemRequest", {
    "id": fields.String(required=True),
    "text": fields.String(required=True),
    "done": fields.Boolean(required=False, default=False),
    "dueDate": fields.Integer(required=False),
    "position": fields.Integer(required=False, default=0),
})


def _require_role_via_panel(panel_id: str, user_id: str, min_role: str) -> Node:
    node = Node.query.get_or_404(panel_id)
    if not has_at_least(node.project_id, user_id, min_role):
        api.abort(404, "Panel nicht gefunden oder keine ausreichende Berechtigung")
    return node


@api.route("/<string:panel_id>/todo-items")
class TodoItemList(Resource):
    @jwt_required()
    def get(self, panel_id):
        user_id = get_jwt_identity()
        _require_role_via_panel(panel_id, user_id, "VIEWER")
        items = TodoItem.query.filter_by(panel_id=panel_id).order_by(TodoItem.position).all()
        return [i.to_dict() for i in items], 200


@api.route("/<string:panel_id>/todo-items/<string:item_id>")
class TodoItemDetail(Resource):
    @jwt_required()
    @api.expect(item_model)
    def put(self, panel_id, item_id):
        user_id = get_jwt_identity()
        _require_role_via_panel(panel_id, user_id, "EDITOR")

        data = api.payload
        item = TodoItem.query.get(item_id)
        if item is None:
            item = TodoItem(id=item_id, panel_id=panel_id)
            db.session.add(item)

        item.text = data["text"]
        item.done = data.get("done", False)
        item.due_date = data.get("dueDate")
        item.position = data.get("position", 0)
        item.updated_at = now_ms()
        db.session.commit()

        return item.to_dict(), 200

    @jwt_required()
    def delete(self, panel_id, item_id):
        user_id = get_jwt_identity()
        _require_role_via_panel(panel_id, user_id, "EDITOR")

        TodoItem.query.filter_by(id=item_id, panel_id=panel_id).delete()
        db.session.commit()
        return "", 204
