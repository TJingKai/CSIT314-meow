"""Data persistence layer (SQLite). Entities use these helpers; nothing else touches SQL connections."""
import os
import sqlite3
from contextlib import closing

_DB_PATH = os.environ.get("APP_DB", "app.db")

PROFILES = [
    ("User Admin", "Manages user accounts and user profiles"),
    ("Interior Designer", "Manages company info and IDP portfolio"),
    ("Customer", "Searches, views and saves IDPs"),
    ("Platform Management", "Manages IDP categories and views reports"),
]

SCHEMA = """
CREATE TABLE IF NOT EXISTS profile (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL DEFAULT '',
    is_suspended INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS account (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    email TEXT NOT NULL,
    profile_id INTEGER NOT NULL REFERENCES profile(id),
    is_suspended INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS company (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    designer_id INTEGER NOT NULL UNIQUE REFERENCES account(id),
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    contact TEXT NOT NULL DEFAULT '',
    address TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS category (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS idp (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    designer_id INTEGER NOT NULL REFERENCES account(id),
    category_id INTEGER NOT NULL REFERENCES category(id),
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL CHECK (status IN ('current', 'past')),
    budget REAL NOT NULL DEFAULT 0,
    image_url TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS favourite (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER NOT NULL REFERENCES account(id),
    idp_id INTEGER NOT NULL REFERENCES idp(id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    UNIQUE (customer_id, idp_id)
);
CREATE TABLE IF NOT EXISTS view_event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    idp_id INTEGER NOT NULL REFERENCES idp(id) ON DELETE CASCADE,
    customer_id INTEGER NOT NULL REFERENCES account(id),
    viewed_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_idp_designer ON idp(designer_id);
CREATE INDEX IF NOT EXISTS ix_view_idp ON view_event(idp_id, viewed_at);
CREATE INDEX IF NOT EXISTS ix_fav_idp ON favourite(idp_id, created_at);
"""


def set_path(path):
    global _DB_PATH
    _DB_PATH = path


def connect():
    conn = sqlite3.connect(_DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def query(sql, params=()):
    with closing(connect()) as conn:
        return [dict(r) for r in conn.execute(sql, params).fetchall()]


def query_one(sql, params=()):
    rows = query(sql, params)
    return rows[0] if rows else None


def scalar(sql, params=()):
    with closing(connect()) as conn:
        return conn.execute(sql, params).fetchone()[0]


def execute(sql, params=()):
    """Run a write statement; returns lastrowid."""
    conn = connect()
    try:
        with conn:
            return conn.execute(sql, params).lastrowid
    finally:
        conn.close()


def init_db(reset=False):
    if reset and os.path.exists(_DB_PATH):
        os.remove(_DB_PATH)
    with closing(connect()) as conn:
        conn.executescript(SCHEMA)
        for name, desc in PROFILES:
            conn.execute("INSERT OR IGNORE INTO profile (name, description) VALUES (?, ?)", (name, desc))
        conn.commit()
