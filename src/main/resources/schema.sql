CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(6000) NOT NULL,
    email VARCHAR_IGNORECASE(254) NOT NULL UNIQUE ,
    password_hash VARCHAR(6000) NOT NULL,
    role VARCHAR(6000) NOT NULL CHECK(role IN ('customer','designer','admin','manager')),
    status VARCHAR(6000) NOT NULL CHECK(status IN ('active','pending','suspended','rejected')),
    contact VARCHAR(6000) NOT NULL DEFAULT '',
    preferences VARCHAR(6000) NOT NULL DEFAULT '',
    created_at VARCHAR(40) NOT NULL,
    verified_at VARCHAR(40),
    last_login_at VARCHAR(40)
);
CREATE TABLE IF NOT EXISTS companies (
    id VARCHAR(50) PRIMARY KEY,
    owner_id VARCHAR(50) NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(6000) NOT NULL,
    description VARCHAR(6000) NOT NULL DEFAULT '',
    uen VARCHAR_IGNORECASE(50) NOT NULL,
    established_year INTEGER,
    styles VARCHAR(6000) NOT NULL DEFAULT '',
    contact VARCHAR(6000) NOT NULL DEFAULT '',
    hours VARCHAR(6000) NOT NULL DEFAULT '',
    areas VARCHAR(6000) NOT NULL DEFAULT '',
    awards VARCHAR(6000) NOT NULL DEFAULT '',
    proof_id VARCHAR(50),
    logo_id VARCHAR(50),
    banner_id VARCHAR(50)
);
CREATE TABLE IF NOT EXISTS categories (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR_IGNORECASE(80) NOT NULL UNIQUE ,
    description VARCHAR(5000) NOT NULL DEFAULT '',
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1))
);
CREATE TABLE IF NOT EXISTS projects (
    id VARCHAR(50) PRIMARY KEY,
    owner_id VARCHAR(50) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(6000) NOT NULL,
    description VARCHAR(6000) NOT NULL,
    property VARCHAR(6000),
    location VARCHAR(6000),
    style VARCHAR(6000),
    budget DOUBLE PRECISION CHECK(budget >= 0),
    size DOUBLE PRECISION CHECK(size > 0),
    stage VARCHAR(6000) NOT NULL CHECK(stage IN ('Current','Past')),
    duration VARCHAR(6000),
    published_at VARCHAR(40),
    project_details VARCHAR(6000) NOT NULL DEFAULT '',
    published INTEGER NOT NULL DEFAULT 0,
    archived INTEGER NOT NULL DEFAULT 0,
    takedown_reason VARCHAR(6000) NOT NULL DEFAULT '',
    created_at VARCHAR(40) NOT NULL,
    updated_at VARCHAR(40) NOT NULL
);
CREATE TABLE IF NOT EXISTS project_categories (
    project_id VARCHAR(50) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    category_id VARCHAR(50) NOT NULL REFERENCES categories(id),
    PRIMARY KEY(project_id, category_id)
);
CREATE TABLE IF NOT EXISTS uploads (
    id VARCHAR(50) PRIMARY KEY,
    owner_id VARCHAR(50) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename VARCHAR(6000) NOT NULL,
    storage_name VARCHAR_IGNORECASE(80) NOT NULL UNIQUE,
    mime_type VARCHAR(6000) NOT NULL,
    kind VARCHAR(6000) NOT NULL CHECK(kind IN ('image','proof')),
    created_at VARCHAR(40) NOT NULL
);
CREATE TABLE IF NOT EXISTS project_photos (
    id VARCHAR(50) PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    upload_id VARCHAR(50) REFERENCES uploads(id) ON DELETE CASCADE,
    url VARCHAR(6000),
    position INTEGER NOT NULL,
    is_cover INTEGER NOT NULL DEFAULT 0,
    CHECK((upload_id IS NOT NULL) != (url IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS favourites (
    user_id VARCHAR(50) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id VARCHAR(50) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    created_at VARCHAR(40) NOT NULL,
    PRIMARY KEY(user_id, project_id)
);
CREATE TABLE IF NOT EXISTS events (
    id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50) REFERENCES users(id) ON DELETE SET NULL,
    project_id VARCHAR(50) REFERENCES projects(id) ON DELETE SET NULL,
    type VARCHAR(6000) NOT NULL CHECK(type IN ('view','shortlist','registration','project')),
    created_at VARCHAR(40) NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    csrf_token VARCHAR(64) NOT NULL,
    expires_at VARCHAR(40) NOT NULL
);
CREATE TABLE IF NOT EXISTS login_attempts (
    attempt_key VARCHAR(64) PRIMARY KEY,
    count INTEGER NOT NULL,
    started_at VARCHAR(40) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_period ON events(created_at, type);
CREATE INDEX IF NOT EXISTS idx_events_project ON events(project_id, type);
CREATE INDEX IF NOT EXISTS idx_projects_owner ON projects(owner_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expiry ON sessions(expires_at);

CREATE UNIQUE INDEX IF NOT EXISTS company_uen_unique ON companies(uen);
