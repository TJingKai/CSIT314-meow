import os
import tempfile
import unittest

from app import db
from app.boundary.api import create_app
from app.entity.account import Account
from app.entity.profile import Profile, ADMIN


class ApiSmokeTest(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        db.set_path(self.path)
        db.init_db(reset=True)
        Account.create("admin", "pw12345", "a@x.com", Profile.find_by_name(ADMIN)["id"])
        self.client = create_app().test_client()

    def tearDown(self):
        os.remove(self.path)

    def test_requires_login(self):
        self.assertEqual(self.client.get("/accounts").status_code, 401)

    def test_login_then_search_accounts(self):
        r = self.client.post("/login", json={"username": "admin", "password": "pw12345"})
        self.assertEqual(r.status_code, 200)
        headers = {"Authorization": "Bearer " + r.get_json()["token"]}
        r = self.client.get("/accounts?q=adm", headers=headers)
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.get_json()[0]["username"], "admin")

    def test_bad_login_401(self):
        r = self.client.post("/login", json={"username": "admin", "password": "nope"})
        self.assertEqual(r.status_code, 401)
