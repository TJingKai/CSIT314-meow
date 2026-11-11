# MEOW - CSIT314 interior design platform (Sprints 1-3)

Java implementation of the 36 completed sprint stories. Each use case uses the boundary, controller and entity names from its diagrams.

Needs a Java 17 or newer **JDK** (`java -version` and `javac -version` should both work). The prebuilt `meow.jar` already contains its dependencies, so no database server, Python or Node is needed.

## Windows

| File | What it does |
| --- | --- |
| `start-demo.bat` | Deletes the old database, starts MEOW with the demo accounts, generates the test data, loads it and checks the database. Opens the site when it is ready. |
| `run-tests.bat` | Same reset and the same fixed test data (seed 20261108), then runs the test runner. `run-tests.bat 12345` uses another seed if you want different data. |
| `start-windows.bat` | Starts MEOW and keeps the existing data. |
| `build-windows.bat` | Rebuilds `meow.jar` from source with Maven (needs internet the first time). |

The server runs in a separate window called "MEOW server". Close that window to stop it. Both demo scripts stop an older MEOW server on the same port first.

`start-demo.bat` and `run-tests.bat` both call `test\prepare-demo.bat`, which does these steps:

1. Stops a MEOW server already running on the port (only if it is a Java process; another program on the port stops the script).
2. Deletes `instance\`, which holds the database and uploads.
3. Starts MEOW with the demo accounts in the "MEOW server" window.
4. Generates the test data into `test\testdata` with `TestDataGenerator.java` (seed 20261108 unless another is given).
5. Checks the CSV files with `DataCheck.java csv`.
6. Loads them through the REST API with `TestDataLoader.java`, then compares the database with the CSV files using `DataCheck.java db`.

It first checks that `java`, `javac` and `meow.jar` are present. If any step fails, the script stops and shows the error. `run-tests.bat` then runs `TestRunner.java` and writes `test\testdata\test_results.csv`. The server keeps running afterwards.

| To | Run |
| --- | --- |
| Demo the app with test data | `start-demo.bat` |
| Get the PASS/FAIL results | `run-tests.bat` |
| Continue with the data from last time | `start-windows.bat` |
| Rebuild after changing Java code | `build-windows.bat` |

On macOS/Linux use `sh start-demo.sh`, `sh run-tests.sh`, `sh start.sh` and `sh build.sh`.

## Accounts

The admin account is always created, even without demo data:

| Role | Email | Password |
| --- | --- | --- |
| User admin | admin@meow.demo | MEOWDemo!2026 |
| Platform manager | manager@meow.demo | MEOWDemo!2026 |
| Interior designer | designer@meow.demo | MEOWDemo!2026 |
| Customer | customer@meow.demo | MEOWDemo!2026 |

The generated users (100 customers, 100 designers, 3 admins) and their passwords are in `test/testdata/users.csv` after a demo or test run. Do not use these passwords on a public deployment.

## Test tools (`test/`)

- `TestDataGenerator.java` writes the CSV files in `test/testdata`. Same seed gives the same data (default seed 20261108).
- `DataCheck.java csv` checks the CSV files: unique ids and emails, valid references, stats that match the raw view and save rows.
- `TestDataLoader.java` loads the CSV files into the running app through the REST API.
- `DataCheck.java db` compares the database with the CSV files: every user, company, category, IDP, photo, favourite, view and shortlist, plus orphan rows, duplicate shortlists, owner views and cover photos.
- `TestRunner.java` runs TC1.1 to TC6.6 and the normal and alternative flows of every use case, and writes `test/testdata/test_results.csv`.

The tools run with `java File.java` from the `test` folder, so they need no separate compile step.

## Sprint scope

- Sprint 1: #220-232 and #256 (14 stories).
- Sprint 2: #233-240, #242, #263, #266 and #273 (12 stories).
- Sprint 3: #241, #243-251 (10 stories).

Trailing `// step N` comments in the source mark the lines that match each step of a story's sequence diagram. Steps in an alternative branch carry a letter, for example `// step 7a`.

`src/test/resources/sprint-contracts.json` lists the expected call order for each story. `SprintSequenceIntegrationTest` sends real HTTP requests, compares the recorded calls with that list and checks the database result. It runs as part of `build-windows.bat`.

## Project structure

- `api/` REST transport, input conversion and role checks.
- `boundary/` page methods from the diagrams.
- `control/` use-case controllers.
- `entity/` entities and SQL.
- `workflow/` request state and report calculations.
- `src/main/resources/static/` browser UI.

Spring Boot 3.5.16, Java 17, JDBC and a file-based H2 database in `instance/`. Passwords are hashed with PBKDF2, sessions use HttpOnly cookies with CSRF tokens, and login is locked for 15 minutes after 10 failed attempts.

## Configuration

Defaults: `127.0.0.1:8000`. Environment variables: `MEOW_PORT`, `MEOW_HOST`, `MEOW_DATABASE_URL`, `MEOW_DATABASE_PASSWORD`, `MEOW_UPLOAD_DIRECTORY`, `MEOW_COOKIE_SECURE`, `MEOW_SEED_DEMO`.

An extra administrator can be created from a terminal with `java -jar meow.jar --meow.create-admin=you@example.com`.

Method traces: `java -jar meow.jar --meow.seed-demo=true --meow.sequence-trace=true`. Responses then include `sequence` and `infrastructure`.
