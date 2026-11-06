"""Contract and security tests of the actual HTTP API, using a real SQLite database."""

from io import BytesIO

from conftest import login

from backend.database import get_db, new_id

PREFIX = "/api/v1"
PNG = bytes.fromhex("89504e470d0a1a0a") + b"test sample image header"


def test_authentication_is_required(client):
    assert client.get(f"{PREFIX}/projects").status_code == 401
    assert client.get(f"{PREFIX}/health").json["data"]["status"] == "ok"


def test_sessions_are_httponly_and_logout_revokes(client):
    response = client.post(
        f"{PREFIX}/auth/session",
        json={"email": "customer@meow.demo", "password": "MEOWDemo!2026"},
    )
    assert "HttpOnly" in response.headers["Set-Cookie"]
    assert "SameSite=Strict" in response.headers["Set-Cookie"]
    token = client.get(f"{PREFIX}/auth/session").json["data"]["csrfToken"]
    assert (
        client.delete(f"{PREFIX}/auth/session", headers={"X-CSRF-Token": token}).status_code == 200
    )
    assert client.get(f"{PREFIX}/projects").status_code == 401


def test_csrf_and_cross_origin_requests_are_blocked(client):
    login(client)
    assert client.put(f"{PREFIX}/me/favourites/p0").status_code == 403
    assert (
        client.post(
            f"{PREFIX}/auth/session",
            headers={"Origin": "https://evil.example"},
            json={"email": "customer@meow.demo", "password": "MEOWDemo!2026"},
        ).status_code
        == 403
    )


def test_customer_cannot_manage_categories_or_impersonate(client):
    headers = login(client)
    assert (
        client.post(f"{PREFIX}/categories", json={"name": "Test"}, headers=headers).status_code
        == 403
    )
    assert (
        client.patch(
            f"{PREFIX}/me", json={"name": "Alex", "role": "admin"}, headers=headers
        ).status_code
        == 200
    )
    assert client.get(f"{PREFIX}/auth/session").json["data"]["user"]["role"] == "customer"
    assert client.get(f"{PREFIX}/users").status_code == 403


def test_search_and_invalid_pagination(client):
    login(client)
    assert client.get(f"{PREFIX}/projects?q=Slow").json["meta"]["total"] == 1
    assert client.get(f"{PREFIX}/projects?q=unlikely-no-match").json["data"] == []
    assert client.get(f"{PREFIX}/projects?maxBudget=60000").json["meta"]["total"] == 1
    assert client.get(f"{PREFIX}/projects?page=-1").status_code == 400
    assert client.get(f"{PREFIX}/projects?sort=DROP%20TABLE").status_code == 400


def test_get_details_has_no_side_effect_but_post_records_view(client):
    headers = login(client)
    for _ in range(2):
        assert client.get(f"{PREFIX}/projects/p0").json["data"]["views"] == 0
    assert client.post(f"{PREFIX}/projects/p0/views", headers=headers).status_code == 201
    assert client.get(f"{PREFIX}/projects/p0").json["data"]["views"] == 1


def test_idempotent_favourite_and_historical_shortlist(client):
    headers = login(client)
    first = client.put(f"{PREFIX}/me/favourites/p0", headers=headers)
    second = client.put(f"{PREFIX}/me/favourites/p0", headers=headers)
    assert first.status_code == 201
    assert second.status_code == 200 and second.json["data"]["added"] is False
    assert client.get(f"{PREFIX}/projects/p0").json["data"]["shortlists"] == 1
    assert client.delete(f"{PREFIX}/me/favourites/p0", headers=headers).status_code == 204
    assert client.get(f"{PREFIX}/projects?favourites=true").json["meta"]["total"] == 0
    assert client.get(f"{PREFIX}/projects/p0").json["data"]["shortlists"] == 1


def test_unpublished_project_is_hidden_and_records_no_views(app, client):
    headers = login(client, "designer")
    assert (
        client.put(
            f"{PREFIX}/projects/p0/publication",
            json={"published": False},
            headers=headers,
        ).status_code
        == 200
    )
    customer = app.test_client()
    customer_headers = login(customer)
    assert customer.get(f"{PREFIX}/projects").json["meta"]["total"] == 5
    assert customer.get(f"{PREFIX}/projects/p0").status_code == 404
    assert customer.post(f"{PREFIX}/projects/p0/views", headers=customer_headers).status_code == 409
    assert customer.put(f"{PREFIX}/me/favourites/p0", headers=customer_headers).status_code == 404


def test_registration_validation_and_email_uniqueness(client):
    data = {
        "name": "New Customer",
        "email": "new@example.com",
        "role": "customer",
        "password": "ExamplePassword1!",
        "confirmPassword": "wrong",
    }
    assert client.post(f"{PREFIX}/registrations", json=data).status_code == 400
    data["confirmPassword"] = data["password"]
    assert client.post(f"{PREFIX}/registrations", json=data).status_code == 201
    data["email"] = "NEW@example.com"
    assert client.post(f"{PREFIX}/registrations", json=data).status_code == 409
    data["email"], data["role"] = "other@example.com", "admin"
    assert client.post(f"{PREFIX}/registrations", json=data).status_code == 400


def register_designer(client):
    response = client.post(
        f"{PREFIX}/registrations",
        data={
            "name": "New Designer",
            "email": "newdesigner@example.com",
            "role": "designer",
            "password": "ExamplePassword1!",
            "confirmPassword": "ExamplePassword1!",
            "company": "Example Studio",
            "uen": "DEMO123",
            "year": "2020",
            "proof": (BytesIO(b"%PDF-1.4\nSample proof for testing"), "business.pdf"),
        },
    )
    assert response.status_code == 201, response.json
    return response.json["data"]["id"]


def project_payload(upload_id):
    return {
        "title": "Test home",
        "description": "A new interior.",
        "property": "HDB",
        "location": "Bishan",
        "style": "Japandi",
        "stage": "Current",
        "duration": "8 weeks",
        "budget": 50000,
        "size": 90,
        "categories": ["allhomes"],
        "photoUploadIds": [upload_id],
    }


def test_pending_designer_requires_approval_and_ownership(app, client):
    identifier = register_designer(client)
    designer = app.test_client()
    designer_headers = login(
        designer, address="newdesigner@example.com", password="ExamplePassword1!"
    )
    assert designer.post(f"{PREFIX}/projects", json={}, headers=designer_headers).status_code == 403
    admin_headers = login(client, "admin")
    details = client.get(f"{PREFIX}/users/{identifier}/verification").json["data"]
    proof_url = details["company"]["proofUrl"]
    assert client.get(proof_url).status_code == 200
    outsider = app.test_client()
    login(outsider)
    assert outsider.get(proof_url).status_code == 403
    assert (
        client.put(
            f"{PREFIX}/users/{identifier}/verification",
            json={"decision": "approve"},
            headers=admin_headers,
        ).status_code
        == 200
    )
    # Approval invalidates the pre-verification session.
    assert designer.get(f"{PREFIX}/me/company").status_code == 401
    designer_headers = login(
        designer, address="newdesigner@example.com", password="ExamplePassword1!"
    )
    assert (
        designer.patch(
            f"{PREFIX}/projects/p0", json={"title": "Stolen"}, headers=designer_headers
        ).status_code
        == 403
    )


def test_upload_validation_and_transactional_project_creation(client):
    headers = login(client, "designer")
    assert (
        client.post(
            f"{PREFIX}/uploads",
            data={"file": (BytesIO(b"<script>bad</script>"), "image.jpg")},
            headers=headers,
        ).status_code
        == 400
    )
    upload = client.post(
        f"{PREFIX}/uploads", data={"file": (BytesIO(PNG), "room.png")}, headers=headers
    )
    assert upload.status_code == 201
    payload = project_payload(upload.json["data"]["id"])
    payload["categories"] = []
    assert client.post(f"{PREFIX}/projects", json=payload, headers=headers).status_code == 400
    assert client.get(f"{PREFIX}/projects?mine=true").json["meta"]["total"] == 6
    payload["categories"] = ["allhomes"]
    result = client.post(f"{PREFIX}/projects", json=payload, headers=headers)
    assert result.status_code == 201, result.json
    project = result.json["data"]
    assert not project["published"] and len(project["photos"]) == 1
    assert (
        client.patch(
            f"{PREFIX}/projects/{project['id']}", json={"budget": -1}, headers=headers
        ).status_code
        == 400
    )
    assert (
        client.patch(
            f"{PREFIX}/projects/{project['id']}",
            json={"coverPhotoId": "other-photo"},
            headers=headers,
        ).status_code
        == 400
    )


def test_deactivated_category_rejected_for_new_assignments(client):
    headers = login(client, "manager")
    assert (
        client.patch(
            f"{PREFIX}/categories/living", json={"active": False}, headers=headers
        ).status_code
        == 200
    )
    headers = login(client, "designer")
    assert all(
        category["id"] != "living" for category in client.get(f"{PREFIX}/categories").json["data"]
    )
    assert (
        client.patch(
            f"{PREFIX}/projects/p0", json={"categories": ["living"]}, headers=headers
        ).status_code
        == 400
    )


def test_takedown_blocks_republication(app, client):
    headers = login(client, "manager")
    assert (
        client.put(
            f"{PREFIX}/projects/p0/moderation",
            json={"reason": "Test guideline violation"},
            headers=headers,
        ).status_code
        == 200
    )
    headers = login(client, "designer")
    assert (
        client.put(
            f"{PREFIX}/projects/p0/publication",
            json={"published": True},
            headers=headers,
        ).status_code
        == 409
    )


def test_suspension_revokes_existing_session(app, client):
    customer = app.test_client()
    login(customer)
    headers = login(client, "admin")
    assert (
        client.patch(
            f"{PREFIX}/users/c1", json={"status": "suspended"}, headers=headers
        ).status_code
        == 200
    )
    assert customer.get(f"{PREFIX}/projects").status_code == 401
    assert (
        client.patch(
            f"{PREFIX}/users/a1", json={"status": "suspended"}, headers=headers
        ).status_code
        == 409
    )


def test_manager_cannot_administer_users(client):
    login(client, "manager")
    assert client.get(f"{PREFIX}/users").status_code == 403


def test_singapore_report_boundaries_and_monday_week(app, client):
    with app.app_context(), get_db() as db:
        for timestamp in ("2026-11-05T15:59:59+00:00", "2026-11-05T16:00:00+00:00"):
            db.execute(
                "INSERT INTO events VALUES (?,?,?,?,?)",
                (new_id(), "c1", "p0", "view", timestamp),
            )
    login(client, "manager")
    result = client.get(f"{PREFIX}/reports/activity?period=daily&date=2026-11-06").json["data"]
    assert result["totals"]["view"] == 1
    result = client.get(f"{PREFIX}/reports/activity?period=weekly&date=2026-11-06").json["data"]
    assert result["from"] == "2026-11-02" and result["to"] == "2026-11-08"
    assert client.get(f"{PREFIX}/reports/activity?date=not-a-date").status_code == 400


def test_password_change_revokes_all_sessions(app, client):
    first = login(client)
    second = app.test_client()
    login(second)
    response = client.put(
        f"{PREFIX}/me/password",
        json={
            "currentPassword": "MEOWDemo!2026",
            "password": "ChangedPassword!123",
            "confirmPassword": "ChangedPassword!123",
        },
        headers=first,
    )
    assert response.status_code == 200
    assert second.get(f"{PREFIX}/projects").status_code == 401
    login(client, password="ChangedPassword!123")


def test_login_throttle_and_http_error_format(client):
    for _ in range(10):
        assert (
            client.post(
                f"{PREFIX}/auth/session",
                json={"email": "no@example.com", "password": "wrong"},
            ).status_code
            == 401
        )
    assert (
        client.post(
            f"{PREFIX}/auth/session",
            json={"email": "no@example.com", "password": "wrong"},
        ).status_code
        == 429
    )
    assert client.get(f"{PREFIX}/no-such-resource").status_code == 404
    assert "error" in client.get(f"{PREFIX}/no-such-resource").json


def test_pending_designer_cannot_bypass_verification_through_suspension(client):
    headers = login(client, "admin")
    assert (
        client.patch(
            f"{PREFIX}/users/d2", json={"status": "suspended"}, headers=headers
        ).status_code
        == 200
    )
    assert (
        client.patch(f"{PREFIX}/users/d2", json={"status": "active"}, headers=headers).status_code
        == 409
    )


def test_admin_created_designer_can_submit_company_and_proof(app, client):
    headers = login(client, "admin")
    created = client.post(
        f"{PREFIX}/users",
        json={
            "name": "Admin-created designer",
            "email": "created@example.com",
            "role": "designer",
            "password": "ExamplePassword1!",
        },
        headers=headers,
    )
    assert created.status_code == 201
    identifier = created.json["data"]["id"]
    designer = app.test_client()
    designer_headers = login(designer, address="created@example.com", password="ExamplePassword1!")
    company = {"name": "Test studio", "uen": "DEMO123", "year": 2020}
    assert (
        designer.put(f"{PREFIX}/me/company", json=company, headers=designer_headers).status_code
        == 200
    )
    proof = designer.post(
        f"{PREFIX}/me/company/proof",
        data={"file": (BytesIO(b"%PDF-1.4 Sample test proof"), "proof.pdf")},
        headers=designer_headers,
    )
    assert proof.status_code == 201
    assert (
        client.put(
            f"{PREFIX}/users/{identifier}/verification",
            json={"decision": "approve"},
            headers=headers,
        ).status_code
        == 200
    )


def test_deleting_designer_cascades_portfolio_but_preserves_events(client, app):
    customer_headers = login(client)
    assert client.put(f"{PREFIX}/me/favourites/p0", headers=customer_headers).status_code == 201
    headers = login(client, "admin")
    assert client.delete(f"{PREFIX}/users/d1", headers=headers).status_code == 204
    with app.app_context():
        db = get_db()
        assert db.execute("SELECT COUNT(*) FROM projects").fetchone()[0] == 0
        assert db.execute("SELECT COUNT(*) FROM favourites").fetchone()[0] == 0
        assert db.execute("SELECT COUNT(*) FROM events WHERE type='shortlist'").fetchone()[0] == 1


def test_two_customers_have_separate_favourite_collections(app, client):
    headers = login(client)
    client.put(f"{PREFIX}/me/favourites/p0", headers=headers)
    other = app.test_client()
    response = other.post(
        f"{PREFIX}/registrations",
        json={
            "name": "Other",
            "email": "other@example.com",
            "role": "customer",
            "password": "ExamplePassword1!",
            "confirmPassword": "ExamplePassword1!",
        },
    )
    assert response.status_code == 201
    login(other, address="other@example.com", password="ExamplePassword1!")
    assert other.get(f"{PREFIX}/projects?favourites=true").json["meta"]["total"] == 0
    assert client.get(f"{PREFIX}/projects?favourites=true").json["meta"]["total"] == 1


def test_invalid_json_field_types_return_validation_errors(client):
    headers = login(client, "admin")
    assert client.post(f"{PREFIX}/users", json={"role": []}, headers=headers).status_code == 400
    headers = login(client, "designer")
    assert (
        client.patch(
            f"{PREFIX}/projects/p0", json={"coverPhotoId": {}}, headers=headers
        ).status_code
        == 400
    )
    assert (
        client.put(
            f"{PREFIX}/me/company",
            json={"name": "Test", "uen": "TEST", "year": 2020, "logo_id": {}},
            headers=headers,
        ).status_code
        == 400
    )
