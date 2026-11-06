"""Administrator-only account management and business verification."""

import sqlite3
from pathlib import Path

from flask import Blueprint, current_app, g, jsonify, request
from werkzeug.security import generate_password_hash

from .catalog import company_json
from .database import get_db, new_id, now, record_event
from .errors import ApiError, body, email, password, text
from .security import public_user, require_user

api = Blueprint("admin", __name__)
ROLES = {"customer", "designer", "admin", "manager"}


def user_row(identifier):
    row = get_db().execute("SELECT * FROM users WHERE id=?", (identifier,)).fetchone()
    if not row:
        raise ApiError("User not found.", 404, "not_found")
    return row


@api.get("/users")
@require_user("admin")
def users():
    q = request.args.get("q", "")[:300]
    status = request.args.get("status", "")
    sql = "SELECT * FROM users WHERE instr(lower(name||' '||email||' '||role),lower(?))>0"
    args = [q]
    if status:
        sql += " AND status=?"
        args.append(status)
    return jsonify(
        data=[public_user(row) for row in get_db().execute(sql + " ORDER BY created_at", args)]
    )


@api.post("/users")
@require_user("admin")
def create():
    data, db = body(), get_db()
    role = data.get("role")
    if not isinstance(role, str) or role not in ROLES:
        raise ApiError("Choose a valid role.")
    identifier = new_id()
    try:
        with db:
            db.execute(
                "INSERT INTO users (id,name,email,password_hash,role,status,created_at) "
                "VALUES (?,?,?,?,?,?,?)",
                (
                    identifier,
                    text(data, "name", maximum=100),
                    email(data),
                    generate_password_hash(password(data), method="scrypt"),
                    role,
                    "pending" if role == "designer" else "active",
                    now(),
                ),
            )
            record_event(db, "registration", identifier)
    except sqlite3.IntegrityError:
        raise ApiError("Email already registered.", 409, "conflict") from None
    return jsonify(data=public_user(user_row(identifier))), 201


@api.patch("/users/<identifier>")
@require_user("admin")
def update(identifier):
    data, db, user = body(), get_db(), user_row(identifier)
    if identifier == g.user["id"]:
        raise ApiError("You cannot change your own role or status.", 409, "conflict")
    changes = {}
    if "role" in data:
        if not isinstance(data["role"], str) or data["role"] not in ROLES:
            raise ApiError("Choose a valid role.")
        changes["role"] = data["role"]
        if data["role"] == "designer" and user["role"] != "designer":
            changes["status"] = "pending"
            changes["verified_at"] = None
    if "status" in data:
        if data["status"] not in ("active", "suspended"):
            raise ApiError("Status must be active or suspended.")
        if user["status"] in ("pending", "rejected") and data["status"] == "active":
            raise ApiError("Use designer verification to approve this account.", 409, "conflict")
        if changes.get("role") == "designer" and user["role"] != "designer":
            raise ApiError("New designers must be verified before activation.", 409, "conflict")
        if data["status"] == "active" and user["role"] == "designer":
            if not user["verified_at"]:
                raise ApiError(
                    "Business verification is required before activation.",
                    409,
                    "conflict",
                )
        changes["status"] = data["status"]
    if not changes:
        raise ApiError("Provide role or status.")
    with db:
        db.execute(
            "UPDATE users SET " + ",".join(f"{key}=?" for key in changes) + " WHERE id=?",
            (*changes.values(), identifier),
        )
        db.execute("DELETE FROM sessions WHERE user_id=?", (identifier,))
        if changes.get("role") and changes["role"] != "designer":
            db.execute("UPDATE projects SET published=0 WHERE owner_id=?", (identifier,))
    return jsonify(data=public_user(user_row(identifier)))


@api.delete("/users/<identifier>")
@require_user("admin")
def delete(identifier):
    user_row(identifier)
    if identifier == g.user["id"]:
        raise ApiError("You cannot delete your own account.", 409, "conflict")
    db = get_db()
    files = [
        row[0]
        for row in db.execute("SELECT storage_name FROM uploads WHERE owner_id=?", (identifier,))
    ]
    with db:
        db.execute("DELETE FROM users WHERE id=?", (identifier,))
    for filename in files:
        (Path(current_app.config["UPLOAD_DIRECTORY"]) / filename).unlink(missing_ok=True)
    return "", 204


@api.get("/users/<identifier>/verification")
@require_user("admin")
def verification_details(identifier):
    user = user_row(identifier)
    company = get_db().execute("SELECT * FROM companies WHERE owner_id=?", (identifier,)).fetchone()
    return jsonify(
        data={
            "user": public_user(user),
            "company": company_json(company, True) if company else None,
        }
    )


@api.put("/users/<identifier>/verification")
@require_user("admin")
def verify(identifier):
    data, db, user = body(), get_db(), user_row(identifier)
    if user["role"] != "designer" or user["status"] not in ("pending", "rejected"):
        raise ApiError("Only pending or rejected designers can be verified.", 409, "conflict")
    decision = data.get("decision")
    if decision not in ("approve", "reject"):
        raise ApiError("decision must be approve or reject.")
    company = db.execute("SELECT * FROM companies WHERE owner_id=?", (identifier,)).fetchone()
    if decision == "approve" and (not company or not company["proof_id"]):
        raise ApiError(
            "Company details and business proof are required before approval.",
            409,
            "conflict",
        )
    with db:
        db.execute(
            "UPDATE users SET status=?,verified_at=? WHERE id=?",
            (
                "active" if decision == "approve" else "rejected",
                now() if decision == "approve" else None,
                identifier,
            ),
        )
        db.execute("DELETE FROM sessions WHERE user_id=?", (identifier,))
    return jsonify(data=public_user(user_row(identifier)))
