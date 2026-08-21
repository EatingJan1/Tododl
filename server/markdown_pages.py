from flask_restx import Namespace, Resource, fields
from flask_jwt_extended import jwt_required, get_jwt_identity

from extensions import db
from models import MarkdownPage, Node, now_ms
from permissions import has_at_least

api = Namespace("markdown", description="Markdown-Inhalt eines Panels")

markdown_model = api.model("MarkdownPageRequest", {
    "content": fields.String(required=True),
})


def _require_role_via_panel(panel_id: str, user_id: str, min_role: str) -> Node:
    node = Node.query.get_or_404(panel_id)
    if not has_at_least(node.project_id, user_id, min_role):
        api.abort(404, "Panel nicht gefunden oder keine ausreichende Berechtigung")
    return node


@api.route("/<string:panel_id>")
class MarkdownPageDetail(Resource):
    @jwt_required()
    def get(self, panel_id):
        user_id = get_jwt_identity()
        _require_role_via_panel(panel_id, user_id, "VIEWER")

        page = MarkdownPage.query.get(panel_id)
        if page is None:
            return {"panelId": panel_id, "content": "", "updatedAt": 0}, 200
        return page.to_dict(), 200

    @jwt_required()
    @api.expect(markdown_model)
    def put(self, panel_id):
        user_id = get_jwt_identity()
        _require_role_via_panel(panel_id, user_id, "EDITOR")

        data = api.payload
        page = MarkdownPage.query.get(panel_id)
        if page is None:
            page = MarkdownPage(panel_id=panel_id)
            db.session.add(page)

        page.content = data["content"]
        page.updated_at = now_ms()
        db.session.commit()

        return page.to_dict(), 200
