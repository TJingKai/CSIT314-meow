"""Registration, authentication and personal profile resources."""

import sqlite3
from datetime import datetime, timedelta, timezone

from flask import Blueprint, g, jsonify, request
from werkzeug.security import check_password_hash, generate_password_hash

from .database import get_db, new_id, now, record_event
from .errors import ApiError, body, email, number, password, text
from .security import COOKIE, issue_session, public_user, require_user, token_digest
from .uploads import store_file

api = Blueprint("auth", __name__)


@api.get("/auth/session")
def session():
    return jsonify(
        data={
            "user": public_user(g.user) if g.user else None,
            "csrfToken": g.csrf_token,
        }
    )


@api.post("/registrations")
def register():
    data = request.form.to_dict() if request.mimetype == "multipart/form-data" else body()
    address, secret, name = email(data), password(data), text(data, "name", maximum=100)
    if data.get("confirmPassword") != secret:
        raise ApiError("Passwords do not match.")
    role = data.get("role", "customer")
    if role not in ("customer", "designer"):
        raise ApiError("Choose Customer or Interior Designer.")
    identifier, db = new_id(), get_db()
    if db.execute("SELECT 1 FROM users WHERE email=?", (address,)).fetchone():
        raise ApiError("Email already registered.", 409, "conflict")
    try:
        with db:
            db.execute(
                "INSERT INTO users (id,name,email,password_hash,role,status,created_at) "
                "VALUES (?,?,?,?,?,?,?)",
                (
                    identifier,
                    name,
                    address,
                    generate_password_hash(secret, method="scrypt"),
                    role,
                    "pending" if role == "designer" else "active",
                    now(),
                ),
            )
            if role == "designer":
                company_name = text(data, "company", maximum=120)
                uen = text(data, "uen", maximum=30)
                year = int(number(data, "year", 1800, datetime.now().year))
                proof_id = store_file(request.files.get("proof"), identifier, "proof")
                db.execute(
                    "INSERT INTO companies (id,owner_id,name,uen,year,proof_id) "
                    "VALUES (?,?,?,?,?,?)",
                    (new_id(), identifier, company_name, uen, year, proof_id),
                )
            record_event(db, "registration", identifier)
    except sqlite3.IntegrityError:
        raise ApiError("Email already registered.", 409, "conflict") from None
    return jsonify(
        data={"id": identifier, "status": "pending" if role == "designer" else "active"}
    ), 201


@api.post("/auth/session")
def login():
    data, db = body(), get_db()
    address = email(data)
    # A database throttle shared by all workers. Trust no client-supplied forwarding header.
    key = token_digest((request.remote_addr or "") + "|" + address)
    attempt = db.execute("SELECT * FROM login_attempts WHERE key=?", (key,)).fetchone()
    cutoff = (datetime.now(timezone.utc) - timedelta(minutes=15)).isoformat()
    if attempt and attempt["started_at"] >= cutoff and attempt["count"] >= 10:
        raise ApiError("Too many login attempts. Try again in 15 minutes.", 429, "rate_limited")
    if not attempt or attempt["started_at"] < cutoff:
        with db:
            db.execute("INSERT OR REPLACE INTO login_attempts VALUES (?,0,?)", (key, now()))
    with db:
        db.execute("UPDATE login_attempts SET count=count+1 WHERE key=?", (key,))
    user = db.execute("SELECT * FROM users WHERE email=?", (address,)).fetchone()
    secret = data.get("password", "")
    if (
        not isinstance(secret, str)
        or len(secret) > 128
        or not user
        or not check_password_hash(user["password_hash"], secret)
    ):
        raise ApiError("Email or password is incorrect.", 401, "invalid_credentials")
    if user["status"] not in ("active", "pending"):
        raise ApiError("This account does not have active access.", 403, "account_inactive")
    with db:
        db.execute("DELETE FROM login_attempts WHERE key=?", (key,))
    response = jsonify(data={"user": public_user(user)})
    issue_session(response, user["id"])
    return response


@api.delete("/auth/session")
@require_user()
def logout():
    with get_db() as db:
        db.execute(
            "DELETE FROM sessions WHERE token_hash=?",
            (token_digest(request.cookies.get(COOKIE, "")),),
        )
    response = jsonify(data={"message": "Logged out."})
    response.delete_cookie(COOKIE, path="/", samesite="Strict")
    return response


@api.patch("/me")
@require_user()
def profile():
    data, db = body(), get_db()
    updates = {
        key: text(data, key, required=key == "name", maximum=200)
        for key in ("name", "contact", "preferences")
        if key in data
    }
    if not updates:
        raise ApiError("Provide at least one profile field.")
    with db:
        db.execute(
            "UPDATE users SET " + ",".join(f"{key}=?" for key in updates) + " WHERE id=?",
            (*updates.values(), g.user["id"]),
        )
    return jsonify(
        data=public_user(db.execute("SELECT * FROM users WHERE id=?", (g.user["id"],)).fetchone())
    )


@api.put("/me/password")
@require_user()
def change_password():
    data, db = body(), get_db()
    old = data.get("currentPassword", "")
    if not isinstance(old, str) or not check_password_hash(g.user["password_hash"], old):
        raise ApiError("Current password is incorrect.", 403)
    new_password = password(data)
    if data.get("confirmPassword") != new_password:
        raise ApiError("Passwords do not match.")
    with db:
        db.execute(
            "UPDATE users SET password_hash=? WHERE id=?",
            (generate_password_hash(new_password, method="scrypt"), g.user["id"]),
        )
        db.execute("DELETE FROM sessions WHERE user_id=?", (g.user["id"],))
    response = jsonify(data={"message": "Password changed. Log in again."})
    response.delete_cookie(COOKIE, path="/", samesite="Strict")
    return response
