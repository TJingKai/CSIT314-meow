# Validation record — sprint edition

Validated on OpenJDK 17.0.20 with Spring Boot 3.5.16 and Maven 3.9.11.

`spotless:apply verify` completed successfully. Google Java Format checks passed. JUnit executed **49 tests with zero failures, errors or skips** against real Spring MVC components and an isolated H2 database.

## Sequence verification

36 parameterized cases compare actual boundary/controller/entity method entry order with the expected calls independently transcribed from the supplied sprint sequence diagrams. They also check persistence or response effects. Separate HTTP interactions are concatenated where the diagram spans form opening and submission. Designer registration and pending verification are assessed as consecutive #222/#223 episodes.

13 further tests cover duplicate favourites, duplicate shortlists, hidden-project views, empty category selections, duplicate/blank category names, cancelled deletion, rejected designer verification, missing/future report dates, empty reports, draft creation versus first publication, empty search results, confirmation passwords, and role/CSRF restrictions.

The sequence contract file lists normal paths; it is not a claim of exhaustive failure-path, concurrent-load or browser coverage. Explicit report inconsistencies and supplementary infrastructure calls are documented in DIAGRAM_ALIGNMENT.md.

## Executable JAR

The packaged Java process was launched with an independent file-backed H2 database. HTTP checks verified HTML and cat SVG delivery, login/session/CSRF, project listing/details, explicit view recording, first favourite save, duplicate favourite prevention and separate shortlist recording. A restart check verifies retained session and favourite data.

The three browser JavaScript modules pass Node syntax checks. A full interactive browser/layout walkthrough was not performed; HTTP and sequence tests do not prove every visual interaction.

Repeat with `mvnw.cmd clean verify` or `sh mvnw clean verify`. Existing Python code and its download remain unchanged.
