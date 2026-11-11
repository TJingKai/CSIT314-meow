# MEOW — Java implementation of Sprints 1–3

This edition implements the **36 completed sprint stories** in the supplied CSIT314 Final Report. Each use case has the boundary, controller and entity method names from its diagrams. It is a separate Java application; it does not edit or replace the Python project.

## Run on Windows

1. Extract the entire meow.zip into a new folder.
2. Install Java **17 or newer** and make sure `java -version` works.
3. Double-click `start-windows.bat`.
4. Open **http://127.0.0.1:8000**. Keep the terminal open while using MEOW.

The prebuilt `meow.jar` includes its Java dependencies. Python, Node and a separate database server are unnecessary to run it. HTML, CSS and a small browser JavaScript client render the website; all application workflows and persistence run in Java.

On macOS/Linux: `sh start.sh`. To rebuild and test on Windows: `build-windows.bat`; elsewhere: `sh build.sh`. Maven Wrapper downloads require internet access during a source build. Editing Java source requires rebuilding the JAR.

## Demo accounts

All four use password **MEOWDemo!2026**:

| Role | Email |
| --- | --- |
| Customer | customer@meow.demo |
| Interior designer | designer@meow.demo |
| User admin | admin@meow.demo |
| Platform manager | manager@meow.demo |

The launchers explicitly enable demo seeding. A fresh database is seeded once; existing data is preserved. Demo interiors use remote images, so those images need internet access. New uploads remain local.

## Sprint scope

- Sprint 1: #220–232 and #256 (14 stories).
- Sprint 2: #233–240, #242, #263, #266 and #273 (12 stories).
- Sprint 3: #241, #243–251 (10 stories).

IDP creation, photo upload/cover selection, category assignment and publication are separate actions. Saving a favourite and recording its shortlist are separate workflows. Reports count first publication as a new IDP, use Singapore calendar dates, and produce daily rows plus a Total row.

## Review the diagram alignment

Start with [docs/DIAGRAM_ALIGNMENT.md](docs/DIAGRAM_ALIGNMENT.md), which maps all 36 stories to source and records inconsistencies in the report. Reference diagrams are in `docs/diagrams/`. `docs/sprint-contracts.json` contains the manually transcribed normal call orders. Tests compare those orders with method-entry traces from real HTTP requests and also assert database outcomes.

The report has contradictory or omitted details; these are explicitly documented. Therefore the tests establish conformity to the selected sequence paths, not proof that every conflicting diagram can simultaneously be satisfied.

## Project structure and coding practices

- `api/`: versioned REST transport, input conversion and role restrictions.
- `boundary/`: named page/presenter methods from the report.
- `control/`: named use-case controllers and transaction boundaries.
- `entity/`: named entities and parameterized persistence operations.
- `workflow/`: request state, result adaptation and report calculations.
- `src/main/resources/static/`: MEOW browser UI and cat logo.
- `src/test/`: sequence contracts and integration tests.

Spring Boot 3.5.16, Java 17, JDBC and file-backed H2 are used. Authentication uses PBKDF2 password hashing, HttpOnly opaque session cookies, CSRF tokens and throttled login attempts. Protected operations check role, account status and ownership on the server. Private registration proof is not included in public company responses. Database changes use transactions, foreign keys and unique constraints. Google Java Format is checked by Maven `verify`.

The default data directory is `instance/` beside the application. Keep it to preserve your accounts, uploads and projects. This is a local coursework prototype. If adapting it for public hosting, configure HTTPS, secure cookies, credentials, backups and hosting access controls first.

## Configuration

The defaults bind to `127.0.0.1:8000`. Environment variables: `MEOW_PORT`, `MEOW_HOST`, `MEOW_DATABASE_URL`, `MEOW_DATABASE_PASSWORD`, `MEOW_UPLOAD_DIRECTORY`, `MEOW_COOKIE_SECURE`, `MEOW_SEED_DEMO`. The launch scripts enable demo accounts explicitly.

For a fresh non-demo database run `java -jar meow.jar` and create an administrator interactively with `java -jar meow.jar --meow.create-admin=you@example.com` from a terminal. Do not use the demonstration credentials on a public deployment.

To inspect method traces locally: `java -jar meow.jar --meow.seed-demo=true --meow.sequence-trace=true`. Responses then include `sequence` and separately labelled `infrastructure`. Tracing is off by default.

To add this prototype to a shared GitHub repository safely, use a new branch and a separate folder such as `prototypes/meow-java-sprints/`, then open a pull request. Do not commit the `instance/` directory or overwrite the existing prototype.
