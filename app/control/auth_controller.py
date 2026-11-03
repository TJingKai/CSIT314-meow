import secrets

from app.control.errors import AppError, require_fields
from app.entity.account import Account


class AuthController:
    _sessions = {}

    @classmethod
    def login(cls, username, password):
        require_fields({"username": username, "password": password}, "username", "password")
        acc = Account.find_by_username(username)
        if not acc or not Account.check_password(password, acc["password_hash"]):
            raise AppError("Invalid username or password", 401)
        if acc["is_suspended"] or acc["profile_suspended"]:
            raise AppError("Account or profile is suspended", 403)
        token = secrets.token_hex(16)
        cls._sessions[token] = {"id": acc["id"], "username": acc["username"], "role": acc["role"]}
        return token, cls._sessions[token]

    @classmethod
    def logout(cls, token):
        cls._sessions.pop(token, None)

    @classmethod
    def authenticate(cls, token):
        actor = cls._sessions.get(token)
        if not actor:
            raise AppError("Not logged in", 401)
        return actor
