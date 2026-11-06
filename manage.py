"""Local management command for installations without demo accounts."""

import argparse
import sqlite3
from getpass import getpass

from werkzeug.security import generate_password_hash

from backend import create_app
from backend.database import get_db, new_id, now
from backend.errors import ApiError, email, password, text


def main():
    parser = argparse.ArgumentParser(description="Create a local MEOW administrator")
    parser.add_argument("command", choices=["create-admin"])
    parser.add_argument("--email", required=True)
    parser.add_argument("--name", required=True)
    args = parser.parse_args()
    secret = getpass("Password (10–128 characters): ")
    if secret != getpass("Confirm password: "):
        parser.error("Passwords do not match.")
    try:
        address = email({"email": args.email})
        name = text({"name": args.name}, "name", maximum=100)
        password({"password": secret})
        app = create_app({"SEED_DEMO": False})
        with app.app_context(), get_db() as db:
            db.execute(
                "INSERT INTO users (id,name,email,password_hash,role,status,created_at) "
                "VALUES (?,?,?,?, 'admin','active',?)",
                (new_id(), name, address, generate_password_hash(secret, method="scrypt"), now()),
            )
    except (ApiError, sqlite3.IntegrityError) as error:
        parser.error(
            "Email already exists." if isinstance(error, sqlite3.IntegrityError) else str(error)
        )
    print("Administrator created. Start the server and log in.")


if __name__ == "__main__":
    main()
