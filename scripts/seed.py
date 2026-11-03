"""Generate large random test data (>=100 records per data type).
Usage: python -m scripts.seed          (resets app.db)
All seeded users have password: password123
"""
import random
from datetime import datetime, timedelta, timezone

from app import db
from app.entity.account import Account

random.seed(314)
PW_HASH = Account.hash_password("password123")  # hash once and reuse: faster seeding

ROOMS = ["Living Room", "Bedroom", "Kitchen", "Bathroom", "Study", "Balcony", "Dining Room", "Kids Room",
         "Entire Home", "Office"]
STYLES = ["Modern", "Scandinavian", "Industrial", "Minimalist", "Japandi", "Contemporary", "Rustic",
          "Classic", "Tropical", "Vintage"]
FIRST = ["Alex", "Bryan", "Chloe", "Daniel", "Eva", "Farah", "Gavin", "Hana", "Ivan", "Jia", "Kai", "Lena"]
LAST = ["Tan", "Lim", "Ng", "Lee", "Wong", "Chua", "Goh", "Ong", "Koh", "Teo", "Chan", "Yeo"]
FIRMS = ["Studio", "Interiors", "Design Co", "Atelier", "Works", "Concepts", "Spaces", "Living"]


def ts(days_back_max=60):
    t = datetime.now(timezone.utc) - timedelta(days=random.random() * days_back_max)
    return t.strftime("%Y-%m-%d %H:%M:%S")


def main(n=100):
    db.init_db(reset=True)
    conn = db.connect()
    pid = {r["name"]: r["id"] for r in db.query("SELECT id, name FROM profile")}

    def add_accounts(prefix, profile, count):
        ids = []
        for i in range(1, count + 1):
            cur = conn.execute(
                "INSERT INTO account (username, password_hash, email, profile_id, created_at) VALUES (?,?,?,?,?)",
                (f"{prefix}{i:03d}", PW_HASH, f"{prefix}{i:03d}@example.com", pid[profile], ts()))
            ids.append(cur.lastrowid)
        return ids

    add_accounts("admin", "User Admin", 1)
    add_accounts("platform", "Platform Management", 1)
    designers = add_accounts("designer", "Interior Designer", n)
    customers = add_accounts("customer", "Customer", n)

    for d in designers:
        name = f"{random.choice(LAST)} {random.choice(FIRMS)}"
        conn.execute("INSERT INTO company (designer_id, name, description, contact, address) VALUES (?,?,?,?,?)",
                     (d, name, f"{name} is a Singapore interior design firm.",
                      f"+65 6{random.randint(1000000, 9999999)}", f"{random.randint(1, 99)} Orchard Road"))

    categories = []
    for room in ROOMS:
        for style in STYLES:
            cur = conn.execute("INSERT INTO category (name, description) VALUES (?, ?)",
                               (f"{style} {room}", f"{style} style for {room.lower()} projects"))
            categories.append(cur.lastrowid)

    idps = []
    for i in range(1, n * 3 + 1):
        style, room = random.choice(STYLES), random.choice(ROOMS)
        cur = conn.execute(
            "INSERT INTO idp (designer_id, category_id, title, description, status, budget, image_url, created_at) "
            "VALUES (?,?,?,?,?,?,?,?)",
            (random.choice(designers), random.choice(categories), f"{style} {room} #{i}",
             f"A {style.lower()} {room.lower()} renovation with custom carpentry and lighting.",
             random.choice(["current", "past"]), random.randrange(5, 150) * 1000,
             f"https://picsum.photos/seed/{i}/600/400", ts()))
        idps.append(cur.lastrowid)

    for _ in range(n * 10):
        conn.execute("INSERT INTO view_event (idp_id, customer_id, viewed_at) VALUES (?,?,?)",
                     (random.choice(idps), random.choice(customers), ts()))

    pairs = set()
    while len(pairs) < n * 5:
        pairs.add((random.choice(customers), random.choice(idps)))
    for c, i in pairs:
        conn.execute("INSERT INTO favourite (customer_id, idp_id, created_at) VALUES (?,?,?)", (c, i, ts()))

    conn.commit()
    conn.close()
    for t in ("profile", "account", "company", "category", "idp", "view_event", "favourite"):
        print(f"{t:12} {db.scalar(f'SELECT COUNT(*) FROM {t}')}")


if __name__ == "__main__":
    main()
