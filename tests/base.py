import os
import tempfile
import unittest

from app import db
from app.control.auth_controller import AuthController
from app.entity.account import Account
from app.entity.category import Category
from app.entity.profile import Profile, ADMIN, DESIGNER, CUSTOMER, PLATFORM


class BaseTest(unittest.TestCase):
    """Fresh database per test; helper users for every role."""

    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        db.set_path(self.path)
        db.init_db(reset=True)
        AuthController._sessions.clear()
        self.admin = self.make_user("admin", ADMIN)
        self.designer = self.make_user("designer", DESIGNER)
        self.designer2 = self.make_user("designer2", DESIGNER)
        self.customer = self.make_user("customer", CUSTOMER)
        self.platform = self.make_user("platform", PLATFORM)
        self.cat_id = Category.create("Modern Kitchen")

    def tearDown(self):
        os.remove(self.path)

    def make_user(self, username, role):
        pid = Profile.find_by_name(role)["id"]
        uid = Account.create(username, "pw12345", f"{username}@x.com", pid)
        return {"id": uid, "username": username, "role": role}
