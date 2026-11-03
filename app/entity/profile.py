from app import db

ADMIN = "User Admin"
DESIGNER = "Interior Designer"
CUSTOMER = "Customer"
PLATFORM = "Platform Management"


class Profile:
    @staticmethod
    def create(name, description=""):
        return db.execute("INSERT INTO profile (name, description) VALUES (?, ?)", (name, description))

    @staticmethod
    def get(profile_id):
        return db.query_one("SELECT * FROM profile WHERE id = ?", (profile_id,))

    @staticmethod
    def find_by_name(name):
        return db.query_one("SELECT * FROM profile WHERE name = ?", (name,))

    @staticmethod
    def update(profile_id, name=None, description=None):
        p = Profile.get(profile_id)
        db.execute("UPDATE profile SET name = ?, description = ? WHERE id = ?",
                   (name or p["name"], p["description"] if description is None else description, profile_id))

    @staticmethod
    def set_suspended(profile_id, flag):
        db.execute("UPDATE profile SET is_suspended = ? WHERE id = ?", (int(flag), profile_id))

    @staticmethod
    def search(keyword=""):
        k = f"%{keyword}%"
        return db.query("SELECT * FROM profile WHERE name LIKE ? OR description LIKE ? ORDER BY name", (k, k))
