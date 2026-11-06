# Architecture and diagram alignment

The implementation follows the report's boundary–control–entity responsibilities. It preserves the documented workflow conditions while replacing the earlier browser-only simulation with shared server persistence. It does not claim that the report's language-specific class signatures are literal Python interfaces.

| Diagram responsibility | Code | Responsibility |
| --- | --- | --- |
| Boundary pages | frontend/js/pages.js, management.js, ui.js | Render forms, details, galleries, lists and reports |
| UI control | frontend/js/app.js | Convert clicks and forms to REST requests; display API validation results |
| API boundary | backend auth, projects, catalog, admin, analytics, uploads Blueprints | HTTP routes, role dependencies and input validation |
| Controller/use-case logic | Guarded resource functions and helpers in each Blueprint | Apply verification, ownership, publication, uniqueness and reporting rules |
| Entity persistence | backend/schema.sql and database.py | Relational entities, foreign keys, uniqueness and transactions |

## Data relationships

Users have one role and a status. A designer can own one company and many projects. A project has photos, categories, view/shortlist events and customer favourites. Favourites use a composite `(user_id, project_id)` primary key, so duplicate protection holds even when two requests arrive close together. Photos reference upload records; imported sample photos can instead reference a seeded external URL. Uploads record their owner and type. Proof downloads do not become public portfolio assets.

SQLite foreign keys are enabled on every connection. Deleting a user cascades its company, projects, sessions, uploads metadata and favourites. Historical activity events retain their timestamp/type with nullable user/project references; deleted projects no longer appear in rankings. A project's `stage` (Current/Past), `published`, `archived` and moderation reason are separate fields. A company owner's inactive account also hides its projects.

## Important request sequences

1. **Registration and verification:** validate account input → hash password with scrypt → insert user and, for designers, company and proof → mark designer pending → admin fetches private verification data → approve/reject → record verification state and revoke old sessions. New or replaced business proof requires approval.
2. **Create an IDP:** authenticate verified designer → upload owned photos → validate project data and active categories → insert draft, categories and photos in one transaction → return stored representation. Failed category/photo validation rolls back project rows.
3. **Publish/unpublish:** authenticate verified owner → reject archived or taken-down publication → update publication state → customer search only returns visible projects.
4. **Open IDP details:** GET retrieves project and gallery without changing data → frontend POSTs a view event → server checks current visibility before recording → return updated count. A hidden project cannot acquire a view through this endpoint.
5. **Save IDP:** authenticate Customer → check published visibility → INSERT OR IGNORE favourite → record shortlist only if a new row was inserted → return added/already-saved result. Removing a favourite does not rewrite historical events.
6. **Reporting:** authenticate designer or Manager → validate requested date/period → calculate Singapore calendar boundaries → aggregate stored UTC events → return numeric totals and table-ready rows.

## REST and error design

GET performs reads. POST creates registrations, sessions, projects, uploads and view events. PATCH partially updates existing project/user/category/profile resources. PUT replaces company details, publication/archive state, verification decisions, moderation state or favourite membership. DELETE removes a session, project, account or favourite. The HTTP contract is documented in API.md.

Responses use consistent JSON envelopes and standard HTTP statuses. Database uniqueness failures become conflicts; users receive readable validation errors without SQL or Python stack traces. Parameterised SQL and allowlisted sort expressions prevent user values from becoming SQL code. API result objects expose only allowed account fields, never password hashes or session token digests.

## Session security

The server stores digests of random session tokens and a separate CSRF token. Cookies are HttpOnly and SameSite=Strict; enable the Secure flag for HTTPS deployment. Every authenticated mutation checks the supplied CSRF token. Browser Origin checks reject cross-origin mutations. No role is accepted from browser storage, and hiding a menu is never the security boundary.

Passwords use Werkzeug's scrypt implementation with salts. Session expiration is checked on each request. Suspensions, role changes, approvals/rejections and password changes revoke relevant sessions. Business verification uses a server-recorded verification timestamp, so suspending and reactivating a pending designer cannot bypass approval.

## Practical limits

- No email delivery or forgotten-password email reset (#253). Password changes while logged in are supported.
- Uploaded content gets size and signature checks, not malware scanning or full image/PDF decoding.
- SQLite is designed here for local/small deployments; no distributed session store or database migration framework is included.
- The UI displays up to 100 owned/favourite/studio projects per management list; the project API supports pagination for larger datasets. Customer discovery is paginated.
- Uploaded files are separate from the SQLite database. Back up both. An upload is a resource created before project submission; unattached uploads can remain after cancelled forms or failed submissions.
- The assessment seed contains fictional account/company data and remote sample photos. Seeded verified designers are intentionally preverified; real applications require proof.
- Tests cover the HTTP contract, SQLite persistence, permissions, ownership, CSRF, visibility and reporting boundaries. Frontend JavaScript is syntax/format checked. The provided environment's preview could not access its Python virtual environment, so this download did not receive a completed browser walkthrough.
