from flask_restx import Namespace, Resource, fields
from flask_jwt_extended import jwt_required, get_jwt_identity

from extensions import db
from models import Group, GroupMembership, User
from permissions import is_group_admin, user_group_ids

api = Namespace("groups", description="Gruppen (Team-Verwaltung) auf diesem Server")

group_model = api.model("GroupRequest", {"name": fields.String(required=True)})

add_member_model = api.model("AddGroupMemberRequest", {
    "username": fields.String(required=True),
    "role": fields.String(required=False, default="MEMBER"),  # MEMBER | ADMIN
})


@api.route("")
class GroupList(Resource):
    @jwt_required()
    def get(self):
        """Alle Gruppen, in denen der eingeloggte User Mitglied ist."""
        user_id = get_jwt_identity()
        ids = user_group_ids(user_id)
        groups = Group.query.filter(Group.id.in_(ids)).all() if ids else []
        return [g.to_dict() for g in groups], 200

    @jwt_required()
    @api.expect(group_model)
    def post(self):
        """Neue Gruppe anlegen. Ersteller wird automatisch ADMIN der Gruppe."""
        user_id = get_jwt_identity()
        data = api.payload

        group = Group(name=data["name"], created_by=user_id)
        db.session.add(group)
        db.session.flush()

        db.session.add(GroupMembership(group_id=group.id, user_id=user_id, role="ADMIN"))
        db.session.commit()

        return group.to_dict(), 201


@api.route("/<string:group_id>")
class GroupDetail(Resource):
    @jwt_required()
    def delete(self, group_id):
        user_id = get_jwt_identity()
        if not is_group_admin(group_id, user_id):
            api.abort(403, "Nur Gruppen-Admins dürfen die Gruppe löschen")
        Group.query.filter_by(id=group_id).delete()
        db.session.commit()
        return "", 204


@api.route("/<string:group_id>/members")
class GroupMembers(Resource):
    @jwt_required()
    def get(self, group_id):
        user_id = get_jwt_identity()
        if group_id not in user_group_ids(user_id):
            api.abort(404, "Gruppe nicht gefunden oder kein Zugriff")
        members = GroupMembership.query.filter_by(group_id=group_id).all()
        return [m.to_dict() for m in members], 200

    @jwt_required()
    @api.expect(add_member_model)
    def post(self, group_id):
        user_id = get_jwt_identity()
        if not is_group_admin(group_id, user_id):
            api.abort(403, "Nur Gruppen-Admins dürfen Mitglieder hinzufügen")

        data = api.payload
        target_user = User.query.filter_by(username=data["username"]).first()
        if not target_user:
            api.abort(404, "Kein registrierter Nutzer mit diesem Nutzernamen")

        if GroupMembership.query.filter_by(group_id=group_id, user_id=target_user.id).first():
            api.abort(409, "Person ist bereits Mitglied dieser Gruppe")

        membership = GroupMembership(
            group_id=group_id, user_id=target_user.id, role=data.get("role", "MEMBER")
        )
        db.session.add(membership)
        db.session.commit()

        return membership.to_dict(), 201


@api.route("/<string:group_id>/members/<string:member_user_id>")
class GroupMemberDetail(Resource):
    @jwt_required()
    def delete(self, group_id, member_user_id):
        user_id = get_jwt_identity()
        if not is_group_admin(group_id, user_id) and user_id != member_user_id:
            api.abort(403, "Nur Gruppen-Admins dürfen andere Mitglieder entfernen")

        GroupMembership.query.filter_by(group_id=group_id, user_id=member_user_id).delete()
        db.session.commit()
        return "", 204
