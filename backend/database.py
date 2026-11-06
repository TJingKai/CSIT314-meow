"""SQLite connection lifecycle and transactional seed data."""

import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from flask import current_app, g
from werkzeug.security import generate_password_hash


def now():
    return datetime.now(timezone.utc).isoformat()


def new_id():
    return str(uuid4())


def get_db():
    if "db" not in g:
        connection = sqlite3.connect(current_app.config["DATABASE"], timeout=10)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA journal_mode = WAL")
        g.db = connection
    return g.db


def close_db(_error=None):
    connection = g.pop("db", None)
    if connection is not None:
        connection.close()


def record_event(connection, kind, user_id=None, project_id=None):
    connection.execute(
        "INSERT INTO events VALUES (?, ?, ?, ?, ?)",
        (new_id(), user_id, project_id, kind, now()),
    )


def initialise(seed=False):
    db = get_db()
    db.executescript(Path(__file__).with_name("schema.sql").read_text())
    if not seed or db.execute("SELECT 1 FROM users LIMIT 1").fetchone():
        return
    source = json.loads(Path(__file__).with_name("seed.json").read_text())
    password = generate_password_hash(current_app.config["DEMO_PASSWORD"], method="scrypt")
    with db:
        for user in source["users"]:
            db.execute(
                "INSERT INTO users (id,name,email,password_hash,role,status,created_at) "
                "VALUES (?,?,?,?,?,?,?)",
                (
                    user["id"],
                    user["name"],
                    user["email"],
                    password,
                    user["role"],
                    user["status"],
                    now(),
                ),
            )
        db.execute(
            "UPDATE users SET verified_at=? WHERE role='designer' AND status='active'",
            (now(),),
        )
        for company in source["companies"]:
            keys = [
                "id",
                "owner",
                "name",
                "description",
                "uen",
                "year",
                "styles",
                "contact",
                "hours",
                "areas",
                "awards",
            ]
            db.execute(
                "INSERT INTO companies (id,owner_id,name,description,uen,year,styles,"
                "contact,hours,areas,awards) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                tuple(company.get(key, "") for key in keys),
            )
        for category in source["categories"]:
            db.execute(
                "INSERT INTO categories VALUES (?,?,?)",
                (category["id"], category["name"], int(category["active"])),
            )
        for project in source["projects"]:
            keys = [
                "id",
                "owner",
                "title",
                "description",
                "property",
                "location",
                "style",
                "budget",
                "size",
                "stage",
                "duration",
            ]
            db.execute(
                "INSERT INTO projects (id,owner_id,title,description,property,location,"
                "style,budget,size,stage,duration,published,created_at,updated_at) "
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,1,?,?)",
                (*[project[key] for key in keys], project["created"], now()),
            )
            for category_id in set(project["categories"]):
                db.execute(
                    "INSERT INTO project_categories VALUES (?,?)",
                    (project["id"], category_id),
                )
            for position, url in enumerate(project["photos"]):
                db.execute(
                    "INSERT INTO project_photos VALUES (?,?,NULL,?,?,?)",
                    (new_id(), project["id"], url, position, int(position == 0)),
                )
