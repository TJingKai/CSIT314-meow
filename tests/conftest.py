"""Isolated temporary SQLite database and upload directory for every test."""

import pytest

from backend import create_app


@pytest.fixture()
def app(tmp_path):
    return create_app(
        {
            "TESTING": True,
            "DATABASE": str(tmp_path / "test.sqlite3"),
            "UPLOAD_DIRECTORY": str(tmp_path / "uploads"),
            "SEED_DEMO": True,
            "COOKIE_SECURE": False,
        }
    )


@pytest.fixture()
def client(app):
    return app.test_client()


def login(client, role="customer", address=None, password="MEOWDemo!2026"):
    token = client.get("/api/v1/auth/session").json["data"]["csrfToken"]
    response = client.post(
        "/api/v1/auth/session",
        headers={"X-CSRF-Token": token} if token else {},
        json={
            "email": address or f"{role}@meow.demo",
            "password": password,
        },
    )
    assert response.status_code == 200, response.json
    return {"X-CSRF-Token": client.get("/api/v1/auth/session").json["data"]["csrfToken"]}
