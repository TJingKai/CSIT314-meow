"""Company profiles and centrally managed categories."""

import sqlite3
from datetime import datetime

from flask import Blueprint, g, jsonify, request

from .database import get_db, new_id
from .errors import ApiError, body, boolean, number, text
from .security import require_user
from .uploads import owned_upload, store_file

api = Blueprint("catalog", __name__)


def company_json(row, private=False):
    value = dict(row)
    value["owner"] = value.pop("owner_id")
    proof = value.pop("proof_id")
    value["logoUrl"] = f"/api/v1/uploads/{value['logo_id']}/content" if value["logo_id"] else None
    value["bannerUrl"] = (
        f"/api/v1/uploads/{value['banner_id']}/content" if value["banner_id"] else None
    )
    if private:
        value["proofUrl"] = f"/api/v1/uploads/{proof}/content" if proof else None
    return value


@api.get("/companies")
@require_user()
def companies():
    rows = (
        get_db()
        .execute(
            "SELECT c.* FROM companies c JOIN users u ON u.id=c.owner_id "
            "WHERE u.status='active' ORDER BY c.name"
        )
        .fetchall()
    )
    return jsonify(data=[company_json(row) for row in rows])


@api.get("/companies/<identifier>")
@require_user()
def company(identifier):
    row = (
        get_db()
        .execute(
            "SELECT c.* FROM companies c JOIN users u ON u.id=c.owner_id "
            "WHERE c.id=? AND (u.status='active' OR c.owner_id=?)",
            (identifier, g.user["id"]),
        )
        .fetchone()
    )
    if not row:
        raise ApiError("Company not found.", 404, "not_found")
    return jsonify(data=company_json(row))


@api.get("/me/company")
@require_user("designer")
def my_company():
    row = get_db().execute("SELECT * FROM companies WHERE owner_id=?", (g.user["id"],)).fetchone()
    return jsonify(data=company_json(row, True) if row else None)


@api.put("/me/company")
@require_user("designer")
def save_company():
    data, db = body(), get_db()
    values = {
        key: text(
            data,
            key,
            required=key in ("name", "uen"),
            maximum=5000 if key == "description" else 300,
        )
        for key in (
            "name",
            "description",
            "uen",
            "styles",
            "contact",
            "hours",
            "areas",
            "awards",
        )
    }
    values["year"] = int(number(data, "year", 1800, datetime.now().year))
    existing = db.execute("SELECT * FROM companies WHERE owner_id=?", (g.user["id"],)).fetchone()
    for field in ("logo_id", "banner_id"):
        if field in data:
            if data[field] is not None:
                owned_upload(data[field])
            values[field] = data[field]
    with db:
        if existing:
            db.execute(
                "UPDATE companies SET "
                + ",".join(f"{key}=?" for key in values)
                + " WHERE owner_id=?",
                (*values.values(), g.user["id"]),
            )
        else:
            db.execute(
                "INSERT INTO companies (id,owner_id,"
                + ",".join(values)
                + ") VALUES ("
                + ",".join("?" for _ in range(len(values) + 2))
                + ")",
                (new_id(), g.user["id"], *values.values()),
            )
    return my_company()


@api.get("/categories")
@require_user()
def categories():
    sql = "SELECT * FROM categories"
    if g.user["role"] != "manager":
        sql += " WHERE active=1"
    return jsonify(
        data=[
            {**dict(row), "active": bool(row["active"])}
            for row in get_db().execute(sql + " ORDER BY name")
        ]
    )


@api.post("/categories")
@require_user("manager")
def create_category():
    data, db, identifier = body(), get_db(), new_id()
    name = text(data, "name", maximum=80)
    try:
        with db:
            db.execute("INSERT INTO categories VALUES (?,?,1)", (identifier, name))
    except sqlite3.IntegrityError:
        raise ApiError("Category already exists.", 409, "conflict") from None
    return jsonify(data={"id": identifier, "name": name, "active": True}), 201


@api.patch("/categories/<identifier>")
@require_user("manager")
def update_category(identifier):
    data, db = body(), get_db()
    if not db.execute("SELECT 1 FROM categories WHERE id=?", (identifier,)).fetchone():
        raise ApiError("Category not found.", 404, "not_found")
    changes = {}
    if "name" in data:
        changes["name"] = text(data, "name", maximum=80)
    if "active" in data:
        changes["active"] = boolean(data, "active")
    if not changes:
        raise ApiError("Provide name or active.")
    try:
        with db:
            db.execute(
                "UPDATE categories SET " + ",".join(f"{key}=?" for key in changes) + " WHERE id=?",
                (*changes.values(), identifier),
            )
    except sqlite3.IntegrityError:
        raise ApiError("Category already exists.", 409, "conflict") from None
    row = dict(db.execute("SELECT * FROM categories WHERE id=?", (identifier,)).fetchone())
    row["active"] = bool(row["active"])
    return jsonify(data=row)


@api.post("/me/company/proof")
@require_user("designer")
def replace_proof():
    db = get_db()
    company = db.execute("SELECT id FROM companies WHERE owner_id=?", (g.user["id"],)).fetchone()
    if not company:
        raise ApiError("Save your company profile before uploading business proof.")
    with db:
        identifier = store_file(request.files.get("file"), g.user["id"], "proof")
        db.execute(
            "UPDATE companies SET proof_id=? WHERE owner_id=?",
            (identifier, g.user["id"]),
        )
        db.execute(
            "UPDATE users SET status='pending',verified_at=NULL WHERE id=?",
            (g.user["id"],),
        )
    return jsonify(
        data={"status": "pending", "proofUrl": f"/api/v1/uploads/{identifier}/content"}
    ), 201
