from flask_restx import Namespace, Resource, fields
from flask_jwt_extended import jwt_required, get_jwt_identity

from extensions import db
from models import Project, ProjectAccess, User, Group, now_ms, ROLE_RANK
from permissions import effective_role, has_at_least, user_group_ids

api = Namespace("projects", description="Projekte, Rollen und Sharing (User + Gruppen)")

project_model = api.model("ProjectRequest", {
    "title": fields.String(required=True),
    "description": fields.String(required=False),
    "icon": fields.String(required=False),
    "colorHex": fields.String(required=False),
})

grant_user_model = api.model("GrantUserAccessRequest", {
    "username": fields.String(required=True),
    "role": fields.String(required=False, default="EDITOR"),  # OWNER | EDITOR | VIEWER
})

grant_group_model = api.model("GrantGroupAccessRequest", {
    "groupId": fields.String(required=True),
    "role": fields.String(required=False, default="EDITOR"),
})


def _require_role(project_id: str, user_id: str, min_role: str):
    if not has_at_least(project_id, user_id, min_role):
        api.abort(404, "Projekt nicht gefunden oder keine ausreichende Berechtigung")


def _resolve_access_dict(grant: ProjectAccess) -> dict:
    if grant.principal_type == "USER":
        user = User.query.get(grant.principal_id)
        return grant.to_dict(
            resolved_name=user.name if user else None,
            resolved_username=user.username if user else None,
        )
    group = Group.query.get(grant.principal_id)
    return grant.to_dict(resolved_name=group.name if group else None)


@api.route("")
class ProjectList(Resource):
    @jwt_required()
    def get(self):
        """Alle Projekte, auf die der User (direkt oder über eine Gruppe) Zugriff hat."""
        user_id = get_jwt_identity()
        direct = ProjectAccess.query.filter_by(principal_type="USER", principal_id=user_id).all()

        group_ids = user_group_ids(user_id)
        via_group = (
            ProjectAccess.query.filter(
                ProjectAccess.principal_type == "GROUP", ProjectAccess.principal_id.in_(group_ids)
            ).all()
            if group_ids
            else []
        )

        project_ids = {g.project_id for g in direct + via_group}
        projects = Project.query.filter(Project.id.in_(project_ids)).all() if project_ids else []
        return [p.to_dict() for p in projects], 200

    @jwt_required()
    @api.expect(project_model)
    def post(self):
        """Neues Server-Projekt anlegen. Ersteller wird automatisch OWNER."""
        user_id = get_jwt_identity()
        data = api.payload

        project = Project(
            title=data["title"],
            description=data.get("description"),
            icon=data.get("icon"),
            color_hex=data.get("colorHex"),
            owner_id=user_id,
        )
        db.session.add(project)
        db.session.flush()

        db.session.add(
            ProjectAccess(project_id=project.id, principal_type="USER", principal_id=user_id, role="OWNER")
        )
        db.session.commit()

        return project.to_dict(), 201


@api.route("/<string:project_id>")
class ProjectDetail(Resource):
    @jwt_required()
    def get(self, project_id):
        user_id = get_jwt_identity()
        _require_role(project_id, user_id, "VIEWER")
        project = Project.query.get_or_404(project_id)
        return project.to_dict(), 200

    @jwt_required()
    @api.expect(project_model)
    def put(self, project_id):
        user_id = get_jwt_identity()
        _require_role(project_id, user_id, "EDITOR")
        project = Project.query.get_or_404(project_id)

        data = api.payload
        project.title = data["title"]
        project.description = data.get("description")
        project.icon = data.get("icon")
        project.color_hex = data.get("colorHex")
        project.updated_at = now_ms()
        db.session.commit()

        return project.to_dict(), 200

    @jwt_required()
    def delete(self, project_id):
        user_id = get_jwt_identity()
        _require_role(project_id, user_id, "OWNER")

        Project.query.filter_by(id=project_id).delete()
        db.session.commit()
        return "", 204


@api.route("/<string:project_id>/access")
class ProjectAccessList(Resource):
    @jwt_required()
    def get(self, project_id):
        """Alle Berechtigungseinträge (User- und Gruppen-basiert) für dieses Projekt."""
        user_id = get_jwt_identity()
        _require_role(project_id, user_id, "VIEWER")
        grants = ProjectAccess.query.filter_by(project_id=project_id).all()
        return [_resolve_access_dict(g) for g in grants], 200


@api.route("/<string:project_id>/access/user")
class ProjectAccessGrantUser(Resource):
    @jwt_required()
    @api.expect(grant_user_model)
    def post(self, project_id):
        """Einer einzelnen Person direkt eine Rolle auf dem Projekt geben."""
        user_id = get_jwt_identity()
        _require_role(project_id, user_id, "OWNER")

        data = api.payload
        target = User.query.filter_by(username=data["username"]).first()
        if not target:
            api.abort(404, "Kein registrierter Nutzer mit diesem Nutzernamen")

        role = data.get("role", "EDITOR")
        if role not in ROLE_RANK:
            api.abort(400, "Ungültige Rolle")

        existing = ProjectAccess.query.filter_by(
            project_id=project_id, principal_type="USER", principal_id=target.id
        ).first()
        if existing:
            existing.role = role
        else:
            db.session.add(
                ProjectAccess(project_id=project_id, principal_type="USER", principal_id=target.id, role=role)
            )
        db.session.commit()
        return {"ok": True}, 201


@api.route("/<string:project_id>/access/group")
class ProjectAccessGrantGroup(Resource):
    @jwt_required()
    @api.expect(grant_group_model)
    def post(self, project_id):
        """Einer ganzen Gruppe eine Rolle auf dem Projekt geben (z. B. 'Vorstand' -> EDITOR)."""
        user_id = get_jwt_identity()
        _require_role(project_id, user_id, "OWNER")

        data = api.payload
        group = Group.query.get(data["groupId"])
        if not group:
            api.abort(404, "Gruppe nicht gefunden")

        role = data.get("role", "EDITOR")
        if role not in ROLE_RANK:
            api.abort(400, "Ungültige Rolle")

        existing = ProjectAccess.query.filter_by(
            project_id=project_id, principal_type="GROUP", principal_id=group.id
        ).first()
        if existing:
            existing.role = role
        else:
            db.session.add(
                ProjectAccess(project_id=project_id, principal_type="GROUP", principal_id=group.id, role=role)
            )
        db.session.commit()
        return {"ok": True}, 201


@api.route("/<string:project_id>/access/<string:access_id>")
class ProjectAccessRevoke(Resource):
    @jwt_required()
    def delete(self, project_id, access_id):
        user_id = get_jwt_identity()
        _require_role(project_id, user_id, "OWNER")

        ProjectAccess.query.filter_by(id=access_id, project_id=project_id).delete()
        db.session.commit()
        return "", 204
