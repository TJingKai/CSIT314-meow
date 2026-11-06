"""Opaque database-backed sessions, CSRF validation and role checks."""

import secrets
from datetime import datetime, timedelta, timezone
from functools import wraps
from hashlib import sha256
from urllib.parse import urlsplit

from flask import current_app, g, request

from .database import get_db, now
from .errors import ApiError

COOKIE = "meow_session"


def token_digest(value):
    return sha256(value.encode()).hexdigest()


def load_identity():
    g.user = None
    g.csrf_token = None
    token = request.cookies.get(COOKIE)
    if token:
        row = (
            get_db()
            .execute(
                "SELECT u.*,s.csrf_token FROM sessions s JOIN users u ON u.id=s.user_id "
                "WHERE s.token_hash=? AND s.expires_at>?",
                (token_digest(token), now()),
            )
            .fetchone()
        )
        if row and row["status"] in ("active", "pending"):
            g.user = dict(row)
            g.csrf_token = row["csrf_token"]
    if request.method in ("POST", "PUT", "PATCH", "DELETE"):
        # Reject cross-origin browser requests, including login/registration.
        origin = request.headers.get("Origin")
        expected = urlsplit(request.host_url)
        if origin:
            actual = urlsplit(origin)
            if (actual.scheme, actual.netloc) != (expected.scheme, expected.netloc):
                raise ApiError("Cross-origin requests are not permitted.", 403, "csrf_failed")
        if g.user and not secrets.compare_digest(
            request.headers.get("X-CSRF-Token", ""), g.csrf_token
        ):
            raise ApiError("Missing or invalid CSRF token.", 403, "csrf_failed")


def require_user(*roles):
    def decorate(function):
        @wraps(function)
        def wrapped(*args, **kwargs):
            if not g.user:
                raise ApiError("Log in to continue.", 401, "unauthenticated")
            if roles and g.user["role"] not in roles:
                raise ApiError("This action is not allowed for your role.", 403, "forbidden")
            return function(*args, **kwargs)

        return wrapped

    return decorate


def require_verified():
    if g.user["status"] != "active" or not g.user["verified_at"]:
        raise ApiError("Designer verification is required.", 403, "verification_required")


def issue_session(response, user_id):
    token = secrets.token_urlsafe(48)
    csrf = secrets.token_urlsafe(32)
    expiry = (datetime.now(timezone.utc) + timedelta(hours=8)).isoformat()
    db = get_db()
    with db:
        db.execute("DELETE FROM sessions WHERE expires_at<=?", (now(),))
        existing = request.cookies.get(COOKIE)
        if existing:
            db.execute("DELETE FROM sessions WHERE token_hash=?", (token_digest(existing),))
        db.execute(
            "INSERT INTO sessions VALUES (?,?,?,?)",
            (token_digest(token), user_id, csrf, expiry),
        )
    response.set_cookie(
        COOKIE,
        token,
        max_age=28800,
        httponly=True,
        secure=current_app.config["COOKIE_SECURE"],
        samesite="Strict",
        path="/",
    )
    return csrf


def public_user(row):
    return {
        key: row[key] for key in ("id", "name", "email", "role", "status", "contact", "preferences")
    }
