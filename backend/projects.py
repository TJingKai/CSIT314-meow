"""Project resources, publication controls, views and unique favourites."""

from flask import Blueprint, g, jsonify, request

from .database import get_db, new_id, now, record_event
from .errors import ApiError, body, boolean, number, text
from .security import require_user, require_verified
from .uploads import owned_upload

api = Blueprint("projects", __name__)
VISIBLE = "p.published=1 AND p.archived=0 AND p.takedown_reason='' AND u.status='active'"


def project_row(identifier):
    row = (
        get_db()
        .execute(
            "SELECT p.*,u.status AS owner_status FROM projects p JOIN users u ON u.id=p.owner_id "
            "WHERE p.id=?",
            (identifier,),
        )
        .fetchone()
    )
    if not row:
        raise ApiError("Project not found.", 404, "not_found")
    return row


def is_visible(row):
    return bool(
        row["published"]
        and not row["archived"]
        and not row["takedown_reason"]
        and row["owner_status"] == "active"
    )


def owned_project(identifier):
    row = project_row(identifier)
    if row["owner_id"] != g.user["id"]:
        raise ApiError("You do not own this project.", 403, "forbidden")
    return row


def serialise(row):
    db = get_db()
    value = dict(row)
    value["owner"] = value.pop("owner_id")
    value.pop("owner_status", None)
    value["published"], value["archived"] = (
        bool(value["published"]),
        bool(value["archived"]),
    )
    value["takenDown"] = value.pop("takedown_reason")
    value["created"] = value.pop("created_at")
    value["updated"] = value.pop("updated_at")
    photos = db.execute(
        "SELECT * FROM project_photos WHERE project_id=? ORDER BY position",
        (row["id"],),
    ).fetchall()
    value["photos"] = [
        photo["url"] or f"/api/v1/uploads/{photo['upload_id']}/content" for photo in photos
    ]
    value["photoIds"] = [photo["id"] for photo in photos]
    value["cover"] = next((i for i, photo in enumerate(photos) if photo["is_cover"]), 0)
    value["categories"] = [
        item[0]
        for item in db.execute(
            "SELECT category_id FROM project_categories WHERE project_id=?",
            (row["id"],),
        )
    ]
    company = db.execute(
        "SELECT id,name FROM companies WHERE owner_id=?", (row["owner_id"],)
    ).fetchone()
    value["company"] = dict(company) if company else None
    totals = {
        item["type"]: item["total"]
        for item in db.execute(
            "SELECT type,COUNT(*) AS total FROM events WHERE project_id=? GROUP BY type",
            (row["id"],),
        )
    }
    value["views"], value["shortlists"] = (
        totals.get("view", 0),
        totals.get("shortlist", 0),
    )
    value["saved"] = bool(
        db.execute(
            "SELECT 1 FROM favourites WHERE user_id=? AND project_id=?",
            (g.user["id"], row["id"]),
        ).fetchone()
    )
    return value


def project_values(data):
    value = {
        key: text(data, key, maximum=5000 if key == "description" else 150)
        for key in (
            "title",
            "description",
            "property",
            "location",
            "style",
            "stage",
            "duration",
        )
    }
    if value["stage"] not in ("Current", "Past"):
        raise ApiError("Project status must be Current or Past.")
    value["budget"] = number(data, "budget")
    value["size"] = number(data, "size", 0.1, 1_000_000)
    return value


def set_categories(identifier, categories):
    if not isinstance(categories, list) or not categories or len(categories) > 30:
        raise ApiError("Select at least one category, up to 30.")
    if not all(isinstance(category, str) for category in categories):
        raise ApiError("Category IDs must be strings.")
    categories = list(dict.fromkeys(categories))
    db = get_db()
    for category in categories:
        if not db.execute(
            "SELECT 1 FROM categories WHERE id=? AND active=1", (category,)
        ).fetchone():
            raise ApiError("Choose active categories only.")
    db.execute("DELETE FROM project_categories WHERE project_id=?", (identifier,))
    db.executemany(
        "INSERT INTO project_categories VALUES (?,?)",
        ((identifier, category) for category in categories),
    )


def add_photos(identifier, upload_ids):
    if not isinstance(upload_ids, list) or not all(isinstance(item, str) for item in upload_ids):
        raise ApiError("photoUploadIds must be a list of upload IDs.")
    db = get_db()
    current = db.execute(
        "SELECT COUNT(*) FROM project_photos WHERE project_id=?", (identifier,)
    ).fetchone()[0]
    if current + len(upload_ids) > 20:
        raise ApiError("A project can have at most 20 photos.")
    for offset, upload_id in enumerate(dict.fromkeys(upload_ids)):
        owned_upload(upload_id)
        if db.execute(
            "SELECT 1 FROM project_photos WHERE project_id=? AND upload_id=?",
            (identifier, upload_id),
        ).fetchone():
            continue
        db.execute(
            "INSERT INTO project_photos VALUES (?,?,?,NULL,?,?)",
            (
                new_id(),
                identifier,
                upload_id,
                current + offset,
                int(current + offset == 0),
            ),
        )


@api.get("/projects")
@require_user()
def projects():
    db = get_db()
    conditions, values = [VISIBLE], []
    if request.args.get("mine") == "true":
        if g.user["role"] != "designer":
            raise ApiError("Designer access required.", 403, "forbidden")
        conditions, values = ["p.owner_id=?"], [g.user["id"]]
    if request.args.get("favourites") == "true":
        if g.user["role"] != "customer":
            raise ApiError("Customer access required.", 403, "forbidden")
        conditions.append(
            "EXISTS (SELECT 1 FROM favourites f WHERE f.project_id=p.id AND f.user_id=?)"
        )
        values.append(g.user["id"])
    keyword = request.args.get("q", "").strip()
    if keyword:
        # instr treats % and _ as ordinary characters, not wildcard operators.
        conditions.append(
            "instr(lower(p.title||' '||p.description||' '||p.location||' '||p.style||' '||COALESCE(c.name,'')),lower(?))>0"
        )
        values.append(keyword[:300])
    for field in ("style", "property", "location"):
        if request.args.get(field):
            conditions.append(f"p.{field}=?")
            values.append(request.args[field])
    if request.args.get("company"):
        conditions.append("c.id=?")
        values.append(request.args["company"])
    if request.args.get("category"):
        conditions.append(
            "EXISTS (SELECT 1 FROM project_categories pc WHERE pc.project_id=p.id AND pc.category_id=?)"
        )
        values.append(request.args["category"])
    if request.args.get("maxBudget"):
        conditions.append("p.budget<=?")
        values.append(number(request.args, "maxBudget"))
    try:
        page = int(request.args.get("page", 1))
        limit = int(request.args.get("limit", 24))
    except ValueError:
        raise ApiError("page and limit must be integers.") from None
    if page < 1 or not 1 <= limit <= 100:
        raise ApiError("page must be positive; limit must be 1–100.")
    ordering = {
        "newest": "p.created_at DESC,p.id",
        "views": "(SELECT COUNT(*) FROM events e WHERE e.project_id=p.id AND e.type='view') DESC,p.id",
        "saves": "(SELECT COUNT(*) FROM events e WHERE e.project_id=p.id AND e.type='shortlist') DESC,p.id",
    }.get(request.args.get("sort", "newest"))
    if not ordering:
        raise ApiError("sort must be newest, views or saves.")
    query = (
        " FROM projects p JOIN users u ON u.id=p.owner_id "
        "LEFT JOIN companies c ON c.owner_id=p.owner_id WHERE " + " AND ".join(conditions)
    )
    total = db.execute("SELECT COUNT(*)" + query, values).fetchone()[0]
    rows = db.execute(
        "SELECT p.*,u.status AS owner_status"
        + query
        + " ORDER BY "
        + ordering
        + " LIMIT ? OFFSET ?",
        (*values, limit, (page - 1) * limit),
    ).fetchall()
    return jsonify(
        data=[serialise(row) for row in rows],
        meta={"page": page, "limit": limit, "total": total},
    )


@api.get("/projects/<identifier>")
@require_user()
def detail(identifier):
    row = project_row(identifier)
    if not is_visible(row) and row["owner_id"] != g.user["id"] and g.user["role"] != "manager":
        raise ApiError("Project unavailable.", 404, "not_found")
    return jsonify(data=serialise(row))


@api.post("/projects")
@require_user("designer")
def create():
    require_verified()
    data, db, identifier = body(), get_db(), new_id()
    values = project_values(data)
    if not data.get("photoUploadIds"):
        raise ApiError("Upload at least one photo.")
    if not db.execute("SELECT 1 FROM companies WHERE owner_id=?", (g.user["id"],)).fetchone():
        raise ApiError("Complete your company profile first.")
    with db:
        db.execute(
            "INSERT INTO projects (id,owner_id,"
            + ",".join(values)
            + ",created_at,updated_at) VALUES ("
            + ",".join("?" for _ in range(len(values) + 4))
            + ")",
            (identifier, g.user["id"], *values.values(), now(), now()),
        )
        set_categories(identifier, data.get("categories"))
        add_photos(identifier, data["photoUploadIds"])
        record_event(db, "project", g.user["id"], identifier)
    return jsonify(data=serialise(project_row(identifier))), 201


@api.patch("/projects/<identifier>")
@require_user("designer")
def update(identifier):
    require_verified()
    existing, data, db = owned_project(identifier), body(), get_db()
    merged = {**dict(existing), **data}
    values = project_values(merged)
    with db:
        db.execute(
            "UPDATE projects SET "
            + ",".join(f"{key}=?" for key in values)
            + ",updated_at=? WHERE id=?",
            (*values.values(), now(), identifier),
        )
        if "categories" in data:
            set_categories(identifier, data["categories"])
        if "photoUploadIds" in data:
            add_photos(identifier, data["photoUploadIds"])
        if "coverPhotoId" in data:
            if not isinstance(data["coverPhotoId"], str):
                raise ApiError("coverPhotoId must be a string.")
            photo = db.execute(
                "SELECT 1 FROM project_photos WHERE id=? AND project_id=?",
                (data["coverPhotoId"], identifier),
            ).fetchone()
            if not photo:
                raise ApiError("Choose a cover photo from this project.")
            db.execute(
                "UPDATE project_photos SET is_cover=(id=?) WHERE project_id=?",
                (data["coverPhotoId"], identifier),
            )
    return jsonify(data=serialise(project_row(identifier)))


@api.put("/projects/<identifier>/publication")
@require_user("designer")
def publish(identifier):
    require_verified()
    row, db, data = owned_project(identifier), get_db(), body()
    state = boolean(data, "published")
    if state and (row["archived"] or row["takedown_reason"]):
        raise ApiError("Archived or taken-down projects cannot be published.", 409, "conflict")
    with db:
        db.execute(
            "UPDATE projects SET published=?,updated_at=? WHERE id=?",
            (state, now(), identifier),
        )
    return jsonify(data=serialise(project_row(identifier)))


@api.put("/projects/<identifier>/archive")
@require_user("designer")
def archive(identifier):
    owned_project(identifier)
    data, db = body(), get_db()
    state = boolean(data, "archived")
    with db:
        db.execute(
            "UPDATE projects SET archived=?,published=CASE WHEN ? THEN 0 ELSE published END,"
            "updated_at=? WHERE id=?",
            (state, state, now(), identifier),
        )
    return jsonify(data=serialise(project_row(identifier)))


@api.delete("/projects/<identifier>")
@require_user("designer")
def delete(identifier):
    owned_project(identifier)
    with get_db() as db:
        db.execute("DELETE FROM projects WHERE id=?", (identifier,))
    return "", 204


@api.post("/projects/<identifier>/views")
@require_user()
def view(identifier):
    row, db = project_row(identifier), get_db()
    if not is_visible(row):
        raise ApiError("Only published projects record views.", 409, "project_hidden")
    with db:
        record_event(db, "view", g.user["id"], identifier)
    return jsonify(data={"views": serialise(row)["views"]}), 201


@api.put("/me/favourites/<identifier>")
@require_user("customer")
def save(identifier):
    row, db = project_row(identifier), get_db()
    if not is_visible(row):
        raise ApiError("Project unavailable.", 404, "not_found")
    with db:
        cursor = db.execute(
            "INSERT OR IGNORE INTO favourites VALUES (?,?,?)",
            (g.user["id"], identifier, now()),
        )
        added = cursor.rowcount == 1
        if added:
            record_event(db, "shortlist", g.user["id"], identifier)
    return jsonify(
        data={
            "saved": True,
            "added": added,
            "message": "Saved to favourites" if added else "Already in your favourites",
        }
    ), 201 if added else 200


@api.delete("/me/favourites/<identifier>")
@require_user("customer")
def remove(identifier):
    with get_db() as db:
        db.execute(
            "DELETE FROM favourites WHERE user_id=? AND project_id=?",
            (g.user["id"], identifier),
        )
    return "", 204
