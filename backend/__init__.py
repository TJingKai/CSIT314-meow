"""Application factory; configuration, API registration and static frontend."""

import os
import sqlite3
from pathlib import Path

from flask import Flask, jsonify, request, send_from_directory
from werkzeug.exceptions import HTTPException

from .database import close_db, initialise
from .errors import ApiError, error_response
from .security import load_identity


def create_app(config=None):
    root = Path(__file__).resolve().parent.parent
    app = Flask(__name__, static_folder=None, instance_path=str(root / "instance"))
    app.config.update(
        DATABASE=str(root / "instance" / "meow.sqlite3"),
        UPLOAD_DIRECTORY=str(root / "instance" / "uploads"),
        COOKIE_SECURE=os.getenv("MEOW_COOKIE_SECURE", "false").lower() == "true",
        SEED_DEMO=os.getenv("MEOW_SEED_DEMO", "false").lower() == "true",
        DEMO_PASSWORD="MEOWDemo!2026",
        MAX_CONTENT_LENGTH=3 * 1024 * 1024,
    )
    if config:
        app.config.update(config)
    Path(app.config["DATABASE"]).parent.mkdir(parents=True, exist_ok=True)
    Path(app.config["UPLOAD_DIRECTORY"]).mkdir(parents=True, exist_ok=True)
    app.teardown_appcontext(close_db)
    app.register_error_handler(ApiError, error_response)

    @app.errorhandler(HTTPException)
    def http_error(error):
        return jsonify(
            error={
                "code": error.name.lower().replace(" ", "_"),
                "message": error.description,
            }
        ), error.code

    @app.errorhandler(sqlite3.IntegrityError)
    def integrity_error(_error):
        return jsonify(
            error={
                "code": "conflict",
                "message": "The operation conflicts with existing records.",
            }
        ), 409

    @app.errorhandler(Exception)
    def unexpected_error(error):
        app.logger.exception("Unhandled request failure")
        return jsonify(
            error={
                "code": "internal_error",
                "message": "An unexpected server error occurred.",
            }
        ), 500

    @app.before_request
    def protect_api():
        if request.path.startswith("/api/"):
            load_identity()

    @app.after_request
    def response_headers(response):
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "same-origin"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            "img-src 'self' https://images.unsplash.com data:; font-src 'self'; "
            "connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
        )
        if request.path.startswith("/api/"):
            response.headers["Cache-Control"] = "no-store"
        return response

    from . import admin, analytics, auth, catalog, projects, uploads

    for module in (auth, admin, catalog, projects, analytics, uploads):
        app.register_blueprint(module.api, url_prefix="/api/v1")

    @app.get("/api/v1/health")
    def health():
        return jsonify(data={"status": "ok"})

    @app.get("/")
    def index():
        return send_from_directory(root / "frontend", "index.html")

    @app.get("/<path:filename>")
    def frontend(filename):
        if filename.startswith(("css/", "js/", "assets/")):
            return send_from_directory(root / "frontend", filename)
        if filename == "favicon.ico":
            return "", 204
        raise ApiError("Resource not found.", 404, "not_found")

    with app.app_context():
        initialise(seed=app.config["SEED_DEMO"])
    return app
