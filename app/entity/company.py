from app import db


class Company:
    @staticmethod
    def get_by_designer(designer_id):
        return db.query_one("SELECT * FROM company WHERE designer_id = ?", (designer_id,))

    @staticmethod
    def save(designer_id, name, description="", contact="", address=""):
        """Create or update the designer's single company record."""
        if Company.get_by_designer(designer_id):
            db.execute("UPDATE company SET name=?, description=?, contact=?, address=? WHERE designer_id=?",
                       (name, description, contact, address, designer_id))
        else:
            db.execute("INSERT INTO company (designer_id, name, description, contact, address) VALUES (?,?,?,?,?)",
                       (designer_id, name, description, contact, address))
        return Company.get_by_designer(designer_id)
