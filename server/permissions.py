from models import ProjectAccess, GroupMembership, ROLE_RANK


def user_group_ids(user_id: str) -> list[str]:
    memberships = GroupMembership.query.filter_by(user_id=user_id).all()
    return [m.group_id for m in memberships]


def effective_role(project_id: str, user_id: str) -> str | None:
    """
    Höchste Rolle, die der User auf dem Projekt hat - entweder direkt
    zugewiesen oder über eine seiner Gruppen. None = kein Zugriff.
    """
    grants = ProjectAccess.query.filter_by(project_id=project_id, principal_type="USER", principal_id=user_id).all()

    group_ids = user_group_ids(user_id)
    if group_ids:
        grants += ProjectAccess.query.filter(
            ProjectAccess.project_id == project_id,
            ProjectAccess.principal_type == "GROUP",
            ProjectAccess.principal_id.in_(group_ids),
        ).all()

    if not grants:
        return None

    return max(grants, key=lambda g: ROLE_RANK.get(g.role, 0)).role


def has_at_least(project_id: str, user_id: str, min_role: str) -> bool:
    role = effective_role(project_id, user_id)
    if role is None:
        return False
    return ROLE_RANK.get(role, 0) >= ROLE_RANK.get(min_role, 0)


def is_group_admin(group_id: str, user_id: str) -> bool:
    membership = GroupMembership.query.filter_by(group_id=group_id, user_id=user_id).first()
    return membership is not None and membership.role == "ADMIN"
