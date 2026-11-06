"""Bounded uploads with content signatures, random filenames and access checks."""

from pathlib import Path

from flask import Blueprint, current_app, g, jsonify, request, send_file
from werkzeug.utils import secure_filename

from .database import get_db, new_id, now
from .errors import ApiError
from .security import require_user

api = Blueprint("uploads", __name__)


def store_file(file, owner_id, kind="image"):
    if not file or not file.filename:
        raise ApiError("Select a file.")
    content = file.read(2 * 1024 * 1024 + 1)
    if len(content) > 2 * 1024 * 1024:
        raise ApiError("Files must be at most 2MB.", 413, "file_too_large")
    if content.startswith(b"\x89PNG\r\n\x1a\n"):
        extension, mime = ".png", "image/png"
    elif content.startswith(b"\xff\xd8\xff"):
        extension, mime = ".jpg", "image/jpeg"
    elif content.startswith(b"RIFF") and content[8:12] == b"WEBP":
        extension, mime = ".webp", "image/webp"
    elif kind == "proof" and content.startswith(b"%PDF-"):
        extension, mime = ".pdf", "application/pdf"
    else:
        raise ApiError("Use PNG, JPEG or WebP images; business proof also accepts PDF.")
    identifier = new_id()
    storage_name = identifier + extension
    path = Path(current_app.config["UPLOAD_DIRECTORY"]) / storage_name
    path.write_bytes(content)
    get_db().execute(
        "INSERT INTO uploads VALUES (?,?,?,?,?,?,?)",
        (
            identifier,
            owner_id,
            secure_filename(file.filename)[:150] or storage_name,
            storage_name,
            mime,
            kind,
            now(),
        ),
    )
    return identifier


def owned_upload(identifier, kind="image"):
    if not isinstance(identifier, str):
        raise ApiError("Upload ID must be a string.")
    upload = (
        get_db()
        .execute(
            "SELECT * FROM uploads WHERE id=? AND owner_id=? AND kind=?",
            (identifier, g.user["id"], kind),
        )
        .fetchone()
    )
    if not upload:
        raise ApiError("Upload does not exist or does not belong to you.", 400)
    return upload


@api.post("/uploads")
@require_user("designer")
def upload():
    db = get_db()
    with db:
        identifier = store_file(request.files.get("file"), g.user["id"])
    return jsonify(data={"id": identifier, "url": f"/api/v1/uploads/{identifier}/content"}), 201


@api.get("/uploads/<identifier>/content")
@require_user()
def content(identifier):
    db = get_db()
    item = db.execute("SELECT * FROM uploads WHERE id=?", (identifier,)).fetchone()
    if not item:
        raise ApiError("File not found.", 404, "not_found")
    allowed = item["owner_id"] == g.user["id"] or (
        item["kind"] == "proof" and g.user["role"] == "admin"
    )
    if not allowed and item["kind"] == "image":
        allowed = bool(
            db.execute(
                "SELECT 1 FROM project_photos ph JOIN projects p ON p.id=ph.project_id "
                "JOIN users u ON u.id=p.owner_id WHERE ph.upload_id=? AND p.published=1 "
                "AND p.archived=0 AND p.takedown_reason='' AND u.status='active'",
                (identifier,),
            ).fetchone()
        ) or bool(
            db.execute(
                "SELECT 1 FROM companies c JOIN users u ON u.id=c.owner_id "
                "WHERE (c.logo_id=? OR c.banner_id=?) AND u.status='active'",
                (identifier, identifier),
            ).fetchone()
        )
    if not allowed:
        raise ApiError("You do not have access to this file.", 403, "forbidden")
    path = Path(current_app.config["UPLOAD_DIRECTORY"]) / item["storage_name"]
    if not path.is_file():
        raise ApiError("File not found.", 404, "not_found")
    return send_file(
        path,
        mimetype=item["mime_type"],
        as_attachment=item["kind"] == "proof",
        download_name=item["filename"],
        conditional=True,
    )
