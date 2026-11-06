PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('customer','designer','admin','manager')),
    status TEXT NOT NULL CHECK(status IN ('active','pending','suspended','rejected')),
    contact TEXT NOT NULL DEFAULT '',
    preferences TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL,
    verified_at TEXT
);
CREATE TABLE IF NOT EXISTS companies (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    uen TEXT NOT NULL,
    year INTEGER NOT NULL,
    styles TEXT NOT NULL DEFAULT '',
    contact TEXT NOT NULL DEFAULT '',
    hours TEXT NOT NULL DEFAULT '',
    areas TEXT NOT NULL DEFAULT '',
    awards TEXT NOT NULL DEFAULT '',
    proof_id TEXT,
    logo_id TEXT,
    banner_id TEXT
);
CREATE TABLE IF NOT EXISTS categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE COLLATE NOCASE,
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1))
);
CREATE TABLE IF NOT EXISTS projects (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    property TEXT NOT NULL,
    location TEXT NOT NULL,
    style TEXT NOT NULL,
    budget REAL NOT NULL CHECK(budget >= 0),
    size REAL NOT NULL CHECK(size > 0),
    stage TEXT NOT NULL CHECK(stage IN ('Current','Past')),
    duration TEXT NOT NULL,
    published INTEGER NOT NULL DEFAULT 0,
    archived INTEGER NOT NULL DEFAULT 0,
    takedown_reason TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS project_categories (
    project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    category_id TEXT NOT NULL REFERENCES categories(id),
    PRIMARY KEY(project_id, category_id)
);
CREATE TABLE IF NOT EXISTS uploads (
    id TEXT PRIMARY KEY,
    owner_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename TEXT NOT NULL,
    storage_name TEXT NOT NULL UNIQUE,
    mime_type TEXT NOT NULL,
    kind TEXT NOT NULL CHECK(kind IN ('image','proof')),
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS project_photos (
    id TEXT PRIMARY KEY,
    project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    upload_id TEXT REFERENCES uploads(id),
    url TEXT,
    position INTEGER NOT NULL,
    is_cover INTEGER NOT NULL DEFAULT 0,
    CHECK((upload_id IS NOT NULL) != (url IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS favourites (
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    PRIMARY KEY(user_id, project_id)
);
CREATE TABLE IF NOT EXISTS events (
    id TEXT PRIMARY KEY,
    user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
    project_id TEXT REFERENCES projects(id) ON DELETE SET NULL,
    type TEXT NOT NULL CHECK(type IN ('view','shortlist','registration','project')),
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    csrf_token TEXT NOT NULL,
    expires_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS login_attempts (
    key TEXT PRIMARY KEY,
    count INTEGER NOT NULL,
    started_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_period ON events(created_at, type);
CREATE INDEX IF NOT EXISTS idx_events_project ON events(project_id, type);
CREATE INDEX IF NOT EXISTS idx_projects_owner ON projects(owner_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expiry ON sessions(expires_at);
