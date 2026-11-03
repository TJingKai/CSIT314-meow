from app import db
from app.entity.common import like


class Category:
    @staticmethod
    def create(name, description=""):
        return db.execute("INSERT INTO category (name, description) VALUES (?, ?)", (name, description))

    @staticmethod
    def get(category_id):
        return db.query_one("SELECT * FROM category WHERE id = ?", (category_id,))

    @staticmethod
    def update(category_id, name=None, description=None):
        c = Category.get(category_id)
        db.execute("UPDATE category SET name = ?, description = ? WHERE id = ?",
                   (name or c["name"], c["description"] if description is None else description, category_id))

    @staticmethod
    def delete(category_id):
        db.execute("DELETE FROM category WHERE id = ?", (category_id,))

    @staticmethod
    def in_use(category_id):
        return db.scalar("SELECT COUNT(*) FROM idp WHERE category_id = ?", (category_id,)) > 0

    @staticmethod
    def search(keyword=""):
        return db.query("SELECT * FROM category WHERE name LIKE ? OR description LIKE ? ORDER BY name",
                        (like(keyword), like(keyword)))
