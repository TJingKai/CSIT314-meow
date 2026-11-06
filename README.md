# MEOW Interior Design Platform

MEOW is the name of our CSIT314 project group. Its cat logo appears in the header and browser tab.

MEOW is a downloadable full-stack implementation of the CSIT314 interior design platform. It retains the original website's visual direction and replaces browser-local accounts and data with a REST API and SQLite persistence. Customer, Interior Designer, User Admin and Platform Manager workflows are enforced by the server.

## Start on Windows

1. Install **Python 3.10 or newer**, including the Windows Python launcher. Tested on Python 3.12.
2. Extract this entire ZIP to a normal folder. Do not run inside the ZIP viewer.
3. Double-click **start-windows.bat**. The first run creates a virtual environment and installs dependencies; internet is required for that step.
4. Open **http://127.0.0.1:8000** in your browser. Keep the command window open while using the site. Press Ctrl+C in it to stop.

The launcher seeds fictional sample accounts only when the database is empty. Restarting does not reset your changes.

| Role | Email | Password |
| --- | --- | --- |
| Customer | customer@meow.demo | MEOWDemo!2026 |
| Interior Designer | designer@meow.demo | MEOWDemo!2026 |
| User Admin | admin@meow.demo | MEOWDemo!2026 |
| Platform Manager | manager@meow.demo | MEOWDemo!2026 |

The login screen's demo buttons fill these credentials. They do not bypass authentication or change roles. The seeded pending designer, sam@meow.demo, has no business proof yet; register a new designer or submit proof through its company profile to demonstrate approval. Its password is also MEOWDemo!2026.

## Manual setup

Windows PowerShell, from the extracted folder:

```powershell
py -3 -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe run.py --demo
```

macOS / Linux:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python run.py --demo
```

Alternatively, run `sh start.sh` on macOS/Linux. If port 8000 is occupied, use `run.py --demo --port 8001` and open the matching address. There is no Node.js requirement for running the website. The optional npm commands are for frontend formatting and development only.

## Project layout

| Path | Purpose |
| --- | --- |
| backend/__init__.py | Flask application factory, route registration, error handling and response headers |
| backend/security.py | Database-backed sessions, CSRF and role guards |
| backend/auth.py | Registration, session lifecycle, profile and password updates |
| backend/projects.py | Projects, publication, ownership, photos, views and favourites |
| backend/catalog.py | Company profiles and category management |
| backend/admin.py | User administration and designer verification |
| backend/analytics.py | Designer analytics, activity reports and moderation |
| backend/uploads.py | Upload validation, storage and access-controlled file delivery |
| backend/database.py / schema.sql | Database lifecycle, sample data and relational schema |
| frontend/js/api.js | Central REST client with cookies and CSRF headers |
| frontend/js/app.js | Navigation, UI event delegation and API mutations |
| frontend/js/pages.js / management.js | Customer, designer and management views |
| frontend/js/ui.js / css/styles.css | Shared presentation components and responsive styling |
| tests/ | HTTP contract, permissions and persistence tests |
| docs/API.md | Endpoints, request examples and response conventions |
| docs/ARCHITECTURE.md | BCE/sequence mapping, data model and implementation decisions |
| docs/TRACEABILITY.md | Report requirements mapped to routes and implementation |

## Features

- Customer and designer registration with validation and unique email addresses.
- Designer company details and business proof; pending accounts require administrator approval before project creation/publication.
- Real login/logout, password changes, customer profile editing and role-specific screens.
- Administrator account search, creation, suspension/reactivation, role changes, deletion and designer review.
- Company profiles, logos/banners, styles, contact information and certifications.
- Current/past project portfolios, draft creation, multiple photos, cover selection, editing, category assignment, publishing/unpublishing and archiving.
- Published-only project discovery with keyword search, category/style/budget/property/location filters, sorting, pagination, galleries and company links.
- Unique per-customer favourites and historical view/shortlist events.
- Designer date-range analytics and per-project totals.
- Platform category creation/editing/deactivation, daily/weekly/monthly reports, top project tables and moderation take-downs.

## Demonstrate the connected flows

1. Register a new Interior Designer with fictional company details and a sample PDF/image proof. Log in and confirm that project creation is blocked while pending.
2. Log out, log in as User Admin, open Designer verification, review the application and approve it. Approval revokes existing designer sessions.
3. Log in as the new designer; create a project with one or more images and an active category. It starts as a draft. Choose a cover through Edit, then Publish.
4. Log in as Customer. Search for the project, open its gallery and save it twice. The second save does not create another favourite or shortlist event.
5. Log in as the designer to see the view and shortlist totals. Unpublish the project; it disappears from customer results and favourites displays.
6. Log in as Platform Manager to inspect an activity report or deactivate a category. Deactivated categories cannot be newly assigned to projects.

## Persistence and backups

The application creates `instance/meow.sqlite3` and `instance/uploads/` on your computer. Multiple browsers that connect to the same running server share this database. Authentication is per browser session; records do not live in localStorage. Stop the application before copying the **whole instance folder** for a simple consistent backup. Keep this folder private.

To start a new local demo, first stop the application, back up `instance`, and rename it. The next `--demo` run creates a fresh database. The supplied ZIP contains no existing database, uploaded documents, credentials or virtual environment.

## Empty installation without demo users

Install dependencies, then run:

```bash
python manage.py create-admin --email you@example.com --name "Your Name"
python run.py
```

Use the virtual environment's Python executable in place of `python` if it is not activated. The management command prompts for a password without echoing it. Without `--demo`, no sample accounts or sample projects are created. Use User Admin to create a Platform Manager, then have that manager create categories. Designers register through the normal form.

## Tests and formatting

```bash
python -m pip install -r requirements-dev.txt
python -m pytest
python -m ruff check backend tests run.py manage.py
python -m ruff format --check backend tests run.py manage.py
```

Use the virtual environment Python. Optional Node.js tooling:

```bash
npm install
npm run check:frontend
npm run format:frontend
```

Python is formatted with Ruff; HTML, CSS and JavaScript are formatted with Prettier. There is no frontend build step.

## Security and deployment boundaries

Passwords use Werkzeug scrypt with per-password salts. Login uses random opaque session tokens, stores token digests in SQLite and sends HttpOnly, SameSite=Strict cookies. Mutations require CSRF tokens for authenticated sessions. Role and project ownership checks happen on the server. Password changes revoke all sessions; account suspension and role changes revoke that user's sessions. Business-proof files are accessible only to their owner and User Admin. SQL uses bound parameters; categories, numbers, file types and request sizes are validated.

Waitress serves the app with debug mode disabled and binds to the local computer by default. This is an assessment/local-use implementation, not a claim of production certification. Before internet deployment, remove demo accounts, use HTTPS with `MEOW_COOKIE_SECURE=true`, set up backups and operational monitoring, and review data handling and abuse controls. Do not use real personal business documents in a shared demo.

Uploads allow PNG/JPEG/WebP (PDF also for business proof), check file signatures, use generated filenames and cap files at 2MB. This is not an antivirus or full document-validation service. Maximum request size is 3MB; the UI sends photo files in separate requests. Projects support up to 20 photos. Seed project photos load from Unsplash and need internet; uploaded photos work locally.

Email-based password recovery from story #253 is not implemented because no mail service is configured. Logged-in password change (#254) is implemented. SQLite is suitable for local use and modest concurrency; larger deployments need a migration/operations plan. This package is separate from the earlier hosted browser-only prototype; it does not replace that hosted deployment.
