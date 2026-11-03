"""Interest tracking entities: timestamped events so counts AND time-ranged reports come from one source."""
from app import db
from app.entity.common import now, like

_FAV_SELECT = ("SELECT f.id AS favourite_id, f.created_at AS saved_at, i.*, c.name AS category_name "
               "FROM favourite f JOIN idp i ON i.id = f.idp_id JOIN category c ON c.id = i.category_id ")


class ViewEvent:
    @staticmethod
    def record(idp_id, customer_id, viewed_at=None):
        return db.execute("INSERT INTO view_event (idp_id, customer_id, viewed_at) VALUES (?,?,?)",
                          (idp_id, customer_id, viewed_at or now()))

    @staticmethod
    def count(idp_id, since=None):
        sql, params = "SELECT COUNT(*) FROM view_event WHERE idp_id = ?", [idp_id]
        if since:
            sql += " AND viewed_at >= ?"
            params.append(since)
        return db.scalar(sql, params)


class Favourite:
    @staticmethod
    def add(customer_id, idp_id, created_at=None):
        return db.execute("INSERT INTO favourite (customer_id, idp_id, created_at) VALUES (?,?,?)",
                          (customer_id, idp_id, created_at or now()))

    @staticmethod
    def exists(customer_id, idp_id):
        return db.query_one("SELECT 1 AS x FROM favourite WHERE customer_id=? AND idp_id=?",
                            (customer_id, idp_id)) is not None

    @staticmethod
    def remove(customer_id, idp_id):
        db.execute("DELETE FROM favourite WHERE customer_id=? AND idp_id=?", (customer_id, idp_id))

    @staticmethod
    def list_for_customer(customer_id, keyword=""):
        return db.query(_FAV_SELECT + "WHERE f.customer_id = ? AND (i.title LIKE ? OR i.description LIKE ?) "
                        "ORDER BY f.created_at DESC", (customer_id, like(keyword), like(keyword)))

    @staticmethod
    def count(idp_id, since=None):
        sql, params = "SELECT COUNT(*) FROM favourite WHERE idp_id = ?", [idp_id]
        if since:
            sql += " AND created_at >= ?"
            params.append(since)
        return db.scalar(sql, params)
