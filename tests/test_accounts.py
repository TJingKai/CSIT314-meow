from app.control.admin_controller import AccountController, ProfileController
from app.control.auth_controller import AuthController
from app.control.errors import AppError
from app.entity.profile import Profile, CUSTOMER
from tests.base import BaseTest


class AuthTests(BaseTest):
    def test_login_success_returns_role(self):
        token, user = AuthController.login("designer", "pw12345")
        self.assertEqual(user["role"], "Interior Designer")
        self.assertEqual(AuthController.authenticate(token)["id"], self.designer["id"])

    def test_login_wrong_password_rejected(self):
        with self.assertRaises(AppError) as cm:
            AuthController.login("designer", "wrong")
        self.assertEqual(cm.exception.status, 401)

    def test_suspended_account_cannot_login(self):
        AccountController.set_suspended(self.admin, self.customer["id"], True)
        with self.assertRaises(AppError) as cm:
            AuthController.login("customer", "pw12345")
        self.assertEqual(cm.exception.status, 403)

    def test_suspended_profile_blocks_login_for_its_users(self):
        ProfileController.set_suspended(self.admin, Profile.find_by_name(CUSTOMER)["id"], True)
        with self.assertRaises(AppError):
            AuthController.login("customer", "pw12345")

    def test_logout_invalidates_token(self):
        token, _ = AuthController.login("customer", "pw12345")
        AuthController.logout(token)
        with self.assertRaises(AppError):
            AuthController.authenticate(token)


class AccountAdminTests(BaseTest):
    def test_admin_creates_and_searches_account(self):
        pid = Profile.find_by_name(CUSTOMER)["id"]
        acc = AccountController.create(self.admin, {"username": "newbie", "password": "abc12345",
                                                    "email": "n@x.com", "profile_id": pid})
        self.assertEqual(acc["role"], CUSTOMER)
        self.assertNotIn("password_hash", acc)
        self.assertEqual(len(AccountController.search(self.admin, "newb")), 1)

    def test_duplicate_username_rejected(self):
        pid = Profile.find_by_name(CUSTOMER)["id"]
        with self.assertRaises(AppError) as cm:
            AccountController.create(self.admin, {"username": "customer", "password": "x",
                                                  "email": "e@x.com", "profile_id": pid})
        self.assertEqual(cm.exception.status, 409)

    def test_non_admin_cannot_manage_accounts(self):
        with self.assertRaises(AppError) as cm:
            AccountController.search(self.customer)
        self.assertEqual(cm.exception.status, 403)

    def test_admin_cannot_suspend_self(self):
        with self.assertRaises(AppError):
            AccountController.set_suspended(self.admin, self.admin["id"], True)

    def test_update_account_email(self):
        updated = AccountController.update(self.admin, self.customer["id"], {"email": "new@x.com"})
        self.assertEqual(updated["email"], "new@x.com")

    def test_profile_crud_and_unique_name(self):
        p = ProfileController.create(self.admin, {"name": "Auditor", "description": "read only"})
        self.assertEqual(ProfileController.update(self.admin, p["id"], {"description": "x"})["description"], "x")
        with self.assertRaises(AppError):
            ProfileController.create(self.admin, {"name": "Auditor"})
