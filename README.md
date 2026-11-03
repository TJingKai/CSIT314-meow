# Interior Designer Matching Platform (CSIT314)

Structure follows b-c-e: `app/entity` (data + persistence), `app/control` (business logic, one controller per use-case group),
`app/boundary` (Flask REST API).

    pip install -r requirements.txt
    python -m scripts.seed          # 100+ records per datatype; all passwords: password123
    python run.py                   # http://127.0.0.1:5000
    python -m unittest discover -s tests -v

Seed logins: admin001, platform001, designer001..100, customer001..100.

Example:
    curl -X POST localhost:5000/login -H "Content-Type: application/json" -d '{"username":"customer001","password":"password123"}'
    curl localhost:5000/idps?q=kitchen -H "Authorization: Bearer <token>"
