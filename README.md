# MEOW - CSIT314 interior design platform

An online platform that matches renovation customers with interior designers. It covers the 36 completed sprint stories, and each use case uses the boundary, controller and entity names from its diagrams.

## Quick start (Windows)

The app is already built (`meow.jar`), so you only need Java. No database, Maven, Python or Node is needed.

### Step 1: Install Java 17 or newer (once)

1. Open Command Prompt and type `java -version`, then `javac -version`.
2. If both show version 17 or higher, go to step 2.
3. If either command is not found, or the version is lower than 17, install a JDK, for example **Eclipse Temurin 17** or **21** from https://adoptium.net. During setup, turn on **Add to PATH** and **Set JAVA_HOME**.
4. Close Command Prompt, open it again and repeat 1 to check.

A JDK is needed (not only a JRE), because the test tools are run straight from their `.java` source files.

### Step 2: Get the project

1. Download the project as a zip (on GitHub: **Code > Download ZIP**) or clone it.
2. Right-click the zip and choose **Extract All**. Do not run the batch files from inside the zip.
3. Open the extracted folder (the one that contains `meow.jar`).

### Step 3a: Run the demo

1. Double-click **`start-demo.bat`**.
2. A second window called **MEOW server** opens. Leave it open while you use the site.
3. The first window shows steps `[1/6]` to `[6/6]`: it clears the old database, starts MEOW with the demo accounts, generates the test data, checks it, loads it and checks the database.
4. When it shows **MEOW is ready**, the site opens in your browser at http://127.0.0.1:8000.
5. Log in with one of the accounts below.

### Step 3b: Run the tests

1. Double-click **`run-tests.bat`**.
2. It does the same reset and data load as the demo, then runs the test runner (test cases TC1.1 to TC6.6 and the flows of every use case).
3. At the end it shows `TOTAL ... PASS ... FAIL ...` and `RESULT: PASS` or `RESULT: FAIL`.
4. The results are saved in `test\testdata\test_results.csv`.
5. The **MEOW server** window stays open, so you can still use the site afterwards.

### Step 4: Stop the app

Close the **MEOW server** window. Running `start-demo.bat` or `run-tests.bat` again also stops an older server first, then starts on a fresh database.

### If something goes wrong

| Message | Fix |
| --- | --- |
| `Java was not found` or `javac was not found` | Do step 1 again, then reopen the folder. |
| `meow.jar is missing` | The project was not extracted fully. Extract the zip again. |
| `Port 8000 is used by another program` | Close that program, or open Command Prompt in the folder and run `set MEOW_PORT=8080` then `start-demo.bat`. The site is then at http://127.0.0.1:8080. |
| `Could not delete the instance folder` | Close any open MEOW server window and try again. If the folder is in OneDrive, move it to a normal folder such as `C:\MEOW`. |

## Accounts

The admin account is always created, even without demo data:

| Role | Email | Password |
| --- | --- | --- |
| User admin | admin@meow.demo | MEOWDemo!2026 |
| Platform manager | manager@meow.demo | MEOWDemo!2026 |
| Interior designer | designer@meow.demo | MEOWDemo!2026 |
| Customer | customer@meow.demo | MEOWDemo!2026 |

The generated users (100 customers, 100 designers, 3 admins) and their passwords are in `test\testdata\users.csv` after a demo or test run. These are demo credentials only; do not use them on a public deployment.

## All scripts

| Windows | macOS / Linux | What it does |
| --- | --- | --- |
| `start-demo.bat` | `sh start-demo.sh` | Fresh database, demo accounts and test data, ready to use |
| `run-tests.bat` | `sh run-tests.sh` | Fresh database and test data, then the test runner |
| `start-windows.bat` | `sh start.sh` | Starts MEOW and keeps the data from last time |
| `build-windows.bat` | `sh build.sh` | Rebuilds `meow.jar` from source (developers only, see below) |

`start-demo.bat` and `run-tests.bat` both call `test\prepare-demo.bat`, which:

1. Stops a MEOW server already running on the port (only a Java process; another program on the port stops the script).
2. Deletes `instance\`, which holds the database and uploads.
3. Starts MEOW with the demo accounts in the "MEOW server" window.
4. Generates the test data into `test\testdata` with `TestDataGenerator.java` (seed 20261108, so every run has the same data).
5. Checks the CSV files with `DataCheck.java csv`.
6. Loads them through the REST API with `TestDataLoader.java`, then compares the database with the CSV files using `DataCheck.java db`.

If any step fails, the script stops and shows the error. `run-tests.bat 12345` uses seed 12345 instead, for a different batch of data.

## Test tools (`test\`)

- `TestDataGenerator.java` writes the CSV files in `test\testdata`. The same seed always gives the same data.
- `DataCheck.java csv` checks the CSV files: unique ids and emails, valid references, and stats that match the raw view and save rows.
- `TestDataLoader.java` loads the CSV files into the running app through the REST API. Every IDP photo is a different crop of the interior photos bundled in `meow.jar`; they are also saved in `test\testdata\photos`, and `TestRunner.java` reuses them for the IDPs it creates.
- `DataCheck.java db` compares the database with the CSV files: every user, company, category, IDP, photo, favourite, view and shortlist, plus orphan rows, duplicate shortlists, owner views and cover photos.
- `TestRunner.java` runs TC1.1 to TC6.6 and the normal and alternative flows of every use case, and writes `test\testdata\test_results.csv`.

## For developers: building from source

Only needed after changing the Java code. Requires internet the first time (Maven downloads its dependencies).

1. Run `build-windows.bat`. It compiles the code, checks the formatting, runs `SprintSequenceIntegrationTest` and copies the new `target\meow.jar` to `meow.jar`.
2. If the build stops in the format check (`spotless`) on a newer Java version, run `build-windows.bat skipformat`. This skips only the format check; the tests still run.
3. Run `start-demo.bat` to try the new version, then commit the new `meow.jar`.

## Design notes

- Sprint 1: #220-232 and #256 (14 stories). Sprint 2: #233-240, #242, #263, #266 and #273 (12 stories). Sprint 3: #241, #243-251 (10 stories).
- Trailing `// step N` comments in the source mark the lines that match each step of a story's sequence diagram. Steps in an alternative branch carry a letter, for example `// step 7a`.
- `src/test/resources/sprint-contracts.json` lists the expected call order for each story. `SprintSequenceIntegrationTest` sends real HTTP requests, compares the recorded calls with that list and checks the database result.
- Packages: `api/` REST transport, input conversion and role checks; `boundary/` page methods from the diagrams; `control/` use-case controllers; `entity/` entities and SQL; `workflow/` request state and report calculations; `src/main/resources/static/` browser UI.
- Interior photos (`src/main/resources/static/assets/photos`) are from Unsplash and used under the Unsplash License. They are bundled, so the demo works without internet.
- Spring Boot 3.5.16, Java 17, JDBC and a file-based H2 database in `instance\`. Passwords are hashed with PBKDF2, sessions use HttpOnly cookies with CSRF tokens, and login is locked for 15 minutes after 10 failed attempts.

## Configuration

Defaults: `127.0.0.1:8000`. Environment variables: `MEOW_PORT`, `MEOW_HOST`, `MEOW_DATABASE_URL`, `MEOW_DATABASE_PASSWORD`, `MEOW_UPLOAD_DIRECTORY`, `MEOW_COOKIE_SECURE`, `MEOW_SEED_DEMO`.

An extra administrator can be created from a terminal with `java -jar meow.jar --meow.create-admin=you@example.com`.

Method traces: `java -jar meow.jar --meow.seed-demo=true --meow.sequence-trace=true`. Responses then include `sequence` and `infrastructure`.
