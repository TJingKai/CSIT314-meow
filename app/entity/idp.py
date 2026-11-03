from app import db
from app.entity.common import now, like

_SELECT = ("SELECT i.*, c.name AS category_name, a.username AS designer_name, co.name AS company_name "
           "FROM idp i JOIN category c ON c.id = i.category_id JOIN account a ON a.id = i.designer_id "
           "LEFT JOIN company co ON co.designer_id = i.designer_id ")


class IDP:
    @staticmethod
    def create(designer_id, category_id, title, description="", status="current", budget=0, image_url="",
               created_at=None):
        return db.execute(
            "INSERT INTO idp (designer_id, category_id, title, description, status, budget, image_url, created_at) "
            "VALUES (?,?,?,?,?,?,?,?)",
            (designer_id, category_id, title, description, status, budget, image_url, created_at or now()))

    @staticmethod
    def get(idp_id):
        return db.query_one(_SELECT + "WHERE i.id = ?", (idp_id,))

    @staticmethod
    def update(idp_id, **fields):
        fields_ok = ("category_id", "title", "description", "status", "budget", "image_url")
        current = db.query_one("SELECT * FROM idp WHERE id = ?", (idp_id,))
        merged = {k: (fields[k] if fields.get(k) is not None else current[k]) for k in fields_ok}
        db.execute("UPDATE idp SET category_id=?, title=?, description=?, status=?, budget=?, image_url=? WHERE id=?",
                   (merged["category_id"], merged["title"], merged["description"], merged["status"],
                    merged["budget"], merged["image_url"], idp_id))

    @staticmethod
    def delete(idp_id):
        db.execute("DELETE FROM idp WHERE id = ?", (idp_id,))

    @staticmethod
    def list_by_designer(designer_id, keyword="", status=None):
        sql = _SELECT + "WHERE i.designer_id = ? AND (i.title LIKE ? OR i.description LIKE ?)"
        params = [designer_id, like(keyword), like(keyword)]
        if status:
            sql += " AND i.status = ?"
            params.append(status)
        return db.query(sql + " ORDER BY i.created_at DESC", params)

    @staticmethod
    def search_public(keyword="", category_id=None, status=None, max_budget=None, limit=50):
        sql = (_SELECT + "WHERE a.is_suspended = 0 AND (i.title LIKE ? OR i.description LIKE ? "
               "OR co.name LIKE ?)")
        params = [like(keyword)] * 3
        if category_id:
            sql += " AND i.category_id = ?"
            params.append(category_id)
        if status:
            sql += " AND i.status = ?"
            params.append(status)
        if max_budget is not None:
            sql += " AND i.budget <= ?"
            params.append(max_budget)
        return db.query(sql + " ORDER BY i.created_at DESC LIMIT ?", params + [limit])
