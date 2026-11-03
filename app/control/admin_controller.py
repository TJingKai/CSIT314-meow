import sqlite3

from app.control.errors import AppError, require_role, require_fields
from app.entity.account import Account
from app.entity.profile import Profile, ADMIN


class AccountController:
    @staticmethod
    def create(actor, data):
        require_role(actor, ADMIN)
        require_fields(data, "username", "password", "email", "profile_id")
        if not Profile.get(data["profile_id"]):
            raise AppError("Profile not found", 404)
        try:
            new_id = Account.create(data["username"], data["password"], data["email"], data["profile_id"])
        except sqlite3.IntegrityError:
            raise AppError("Username already exists", 409)
        return Account.get(new_id)

    @staticmethod
    def view(actor, account_id):
        require_role(actor, ADMIN)
        acc = Account.get(account_id)
        if not acc:
            raise AppError("Account not found", 404)
        return acc

    @staticmethod
    def update(actor, account_id, data):
        AccountController.view(actor, account_id)
        if data.get("profile_id") and not Profile.get(data["profile_id"]):
            raise AppError("Profile not found", 404)
        Account.update(account_id, data.get("email"), data.get("profile_id"), data.get("password"))
        return Account.get(account_id)

    @staticmethod
    def set_suspended(actor, account_id, flag):
        AccountController.view(actor, account_id)
        if account_id == actor["id"]:
            raise AppError("You cannot suspend your own account", 400)
        Account.set_suspended(account_id, flag)
        return Account.get(account_id)

    @staticmethod
    def search(actor, keyword="", profile_id=None):
        require_role(actor, ADMIN)
        return Account.search(keyword, profile_id)


class ProfileController:
    @staticmethod
    def create(actor, data):
        require_role(actor, ADMIN)
        require_fields(data, "name")
        try:
            return Profile.get(Profile.create(data["name"], data.get("description", "")))
        except sqlite3.IntegrityError:
            raise AppError("Profile name already exists", 409)

    @staticmethod
    def view(actor, profile_id):
        require_role(actor, ADMIN)
        p = Profile.get(profile_id)
        if not p:
            raise AppError("Profile not found", 404)
        return p

    @staticmethod
    def update(actor, profile_id, data):
        ProfileController.view(actor, profile_id)
        try:
            Profile.update(profile_id, data.get("name"), data.get("description"))
        except sqlite3.IntegrityError:
            raise AppError("Profile name already exists", 409)
        return Profile.get(profile_id)

    @staticmethod
    def set_suspended(actor, profile_id, flag):
        ProfileController.view(actor, profile_id)
        Profile.set_suspended(profile_id, flag)
        return Profile.get(profile_id)

    @staticmethod
    def search(actor, keyword=""):
        require_role(actor, ADMIN)
        return Profile.search(keyword)
