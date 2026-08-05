import uuid
import time

from extensions import db


def new_id() -> str:
    return str(uuid.uuid4())


def now_ms() -> int:
    return int(time.time() * 1000)


# Rollen-Rangfolge für Berechtigungsprüfungen (siehe permissions.py)
ROLE_RANK = {"VIEWER": 1, "EDITOR": 2, "OWNER": 3}


class User(db.Model):
    __tablename__ = "user"

    id = db.Column(db.String, primary_key=True, default=new_id)
    email = db.Column(db.String, unique=True, nullable=False, index=True)
    name = db.Column(db.String, nullable=False)
    password_hash = db.Column(db.String, nullable=False)
    created_at = db.Column(db.BigInteger, default=now_ms)

    def to_dict(self):
        return {"id": self.id, "email": self.email, "name": self.name}


class Group(db.Model):
    """Eine Gruppe innerhalb dieses Servers, z. B. 'Vorstand', 'IT-Team'."""
    __tablename__ = "group"

    id = db.Column(db.String, primary_key=True, default=new_id)
    name = db.Column(db.String, nullable=False)
    created_by = db.Column(db.String, db.ForeignKey("user.id"), nullable=False)
    created_at = db.Column(db.BigInteger, default=now_ms)

    memberships = db.relationship("GroupMembership", backref="group", cascade="all, delete-orphan")

    def to_dict(self):
        return {"id": self.id, "name": self.name, "createdBy": self.created_by}


class GroupMembership(db.Model):
    """Wer ist in welcher Gruppe. role=ADMIN darf die Gruppe verwalten (Mitglieder hinzufügen/entfernen)."""
    __tablename__ = "group_membership"

    id = db.Column(db.String, primary_key=True, default=new_id)
    group_id = db.Column(db.String, db.ForeignKey("group.id"), nullable=False, index=True)
    user_id = db.Column(db.String, db.ForeignKey("user.id"), nullable=False, index=True)
    role = db.Column(db.String, nullable=False, default="MEMBER")  # MEMBER | ADMIN

    user = db.relationship("User")

    __table_args__ = (db.UniqueConstraint("group_id", "user_id", name="uq_group_membership"),)

    def to_dict(self):
        return {
            "userId": self.user_id,
            "email": self.user.email if self.user else None,
            "name": self.user.name if self.user else None,
            "role": self.role,
        }


class Project(db.Model):
    __tablename__ = "project"

    id = db.Column(db.String, primary_key=True, default=new_id)
    title = db.Column(db.String, nullable=False)
    description = db.Column(db.String, nullable=True)
    icon = db.Column(db.String, nullable=True)
    color_hex = db.Column(db.String, nullable=True)
    owner_id = db.Column(db.String, db.ForeignKey("user.id"), nullable=False)
    created_at = db.Column(db.BigInteger, default=now_ms)
    updated_at = db.Column(db.BigInteger, default=now_ms)

    access_grants = db.relationship("ProjectAccess", backref="project", cascade="all, delete-orphan")
    nodes = db.relationship("Node", backref="project", cascade="all, delete-orphan")

    def to_dict(self):
        return {
            "id": self.id,
            "title": self.title,
            "description": self.description,
            "icon": self.icon,
            "colorHex": self.color_hex,
            "ownerId": self.owner_id,
            "updatedAt": self.updated_at,
        }


class ProjectAccess(db.Model):
    """
    Ein Berechtigungseintrag für ein Projekt - entweder direkt für einen User
    oder für eine ganze Gruppe (principal_type unterscheidet). Die effektive
    Rolle eines Users auf einem Projekt ist das Maximum aus allen zutreffenden
    Einträgen (direkt + über seine Gruppen), siehe permissions.py.
    """
    __tablename__ = "project_access"

    id = db.Column(db.String, primary_key=True, default=new_id)
    project_id = db.Column(db.String, db.ForeignKey("project.id"), nullable=False, index=True)
    principal_type = db.Column(db.String, nullable=False)  # USER | GROUP
    principal_id = db.Column(db.String, nullable=False, index=True)  # user.id oder group.id
    role = db.Column(db.String, nullable=False, default="EDITOR")  # OWNER | EDITOR | VIEWER

    __table_args__ = (
        db.UniqueConstraint("project_id", "principal_type", "principal_id", name="uq_project_access"),
    )

    def to_dict(self, resolved_name=None, resolved_email=None):
        return {
            "id": self.id,
            "projectId": self.project_id,
            "principalType": self.principal_type,
            "principalId": self.principal_id,
            "role": self.role,
            "name": resolved_name,
            "email": resolved_email,
        }


class Node(db.Model):
    __tablename__ = "node"

    id = db.Column(db.String, primary_key=True, default=new_id)
    project_id = db.Column(db.String, db.ForeignKey("project.id"), nullable=False, index=True)
    parent_id = db.Column(db.String, db.ForeignKey("node.id"), nullable=True, index=True)
    type = db.Column(db.String, nullable=False)
    title = db.Column(db.String, nullable=False)
    icon = db.Column(db.String, nullable=True)
    position = db.Column(db.Integer, nullable=False, default=0)
    updated_at = db.Column(db.BigInteger, default=now_ms)

    todo_items = db.relationship("TodoItem", backref="panel", cascade="all, delete-orphan")
    mind_cards = db.relationship("MindCard", backref="panel", cascade="all, delete-orphan")

    def to_dict(self):
        return {
            "id": self.id,
            "projectId": self.project_id,
            "parentId": self.parent_id,
            "type": self.type,
            "title": self.title,
            "icon": self.icon,
            "position": self.position,
            "updatedAt": self.updated_at,
        }


class TodoItem(db.Model):
    __tablename__ = "todo_item"

    id = db.Column(db.String, primary_key=True, default=new_id)
    panel_id = db.Column(db.String, db.ForeignKey("node.id"), nullable=False, index=True)
    text = db.Column(db.String, nullable=False)
    done = db.Column(db.Boolean, nullable=False, default=False)
    due_date = db.Column(db.BigInteger, nullable=True)
    position = db.Column(db.Integer, nullable=False, default=0)
    updated_at = db.Column(db.BigInteger, default=now_ms)

    def to_dict(self):
        return {
            "id": self.id,
            "panelId": self.panel_id,
            "text": self.text,
            "done": self.done,
            "dueDate": self.due_date,
            "position": self.position,
            "updatedAt": self.updated_at,
        }


class MindCard(db.Model):
    __tablename__ = "mind_card"

    id = db.Column(db.String, primary_key=True, default=new_id)
    panel_id = db.Column(db.String, db.ForeignKey("node.id"), nullable=False, index=True)
    text = db.Column(db.String, nullable=False)
    color_hex = db.Column(db.String, nullable=True)
    pos_x = db.Column(db.Float, nullable=False, default=0)
    pos_y = db.Column(db.Float, nullable=False, default=0)
    updated_at = db.Column(db.BigInteger, default=now_ms)

    def to_dict(self):
        return {
            "id": self.id,
            "panelId": self.panel_id,
            "text": self.text,
            "colorHex": self.color_hex,
            "posX": self.pos_x,
            "posY": self.pos_y,
            "updatedAt": self.updated_at,
        }
