# MEOW REST transport

Base path: `/api/v1`. JSON responses use `{ "data": ..., "messages": [...] }`. Errors from transport/security use `{ "error": { "code": ..., "message": ... } }`. Validation by a diagram boundary can return its messages with a 4xx status. Browser requests stay on the same origin.

Authenticate with `POST /auth/session` and JSON email/password. The response sets an HttpOnly session cookie; `GET /auth/session` returns the user and CSRF token. Send `X-CSRF-Token` for authenticated mutations. `DELETE /auth/session` logs out.

| Role | Endpoints |
| --- | --- |
| Public | GET registration-form, POST registration-validations, POST registrations (JSON customer or multipart designer + proof), GET login-form, GET companies/{id} |
| Authenticated | GET navigation, GET projects/{id}, POST projects/{id}/views |
| Customer | GET projects?q=, GET search-results?q=, GET projects/{id}/company, PUT me/favourites/{id}, GET me/favourites, POST projects/{id}/shortlists |
| Designer | GET projects?mine=true, POST projects, PATCH projects/{id}, POST projects/{id}/photos, PUT projects/{id}/categories, PUT projects/{id}/publication, GET/PUT me/company, GET me/analytics/totals, GET me/analytics/projects |
| Admin | GET/POST users, GET users/form, PATCH/DELETE users/{id}, GET users/{id}/deletion-confirmation, POST users/{id}/deletion-cancellations, GET/PUT users/{id}/verification |
| Manager | GET/POST categories, GET categories/form, GET categories/{id}/edit-form, PATCH categories/{id}, PUT categories/{id}/availability, GET reports/options, POST reports/period, GET reports/activity, GET reports/table |

Paths in the table are relative to the base path. Supporting endpoints for form data, assigned categories and uploaded content are defined in the REST adapters. The Java annotations are authoritative for their request fields and permissions.

Create/edit IDP body: `title`, `description`, `projectDetails`, `status` (`Current` or `Past`). Photo upload is multipart with repeated `photos` parts and `coverIndex`; max 2 MB per file, max 20 photos per project. Categories use `{ "categories": ["category-id"] }`. Publication uses `{ "action": "Publish" }` or `Unpublish`. Verification uses `{ "decision": "approve" }` or `reject`. Category data uses `name` and `description`; availability uses `available`. Admin account creation requires email, password, confirmPassword and role. Company profile requires name, description, uen, year.

Reports use `period=daily|weekly|monthly` and ISO `date=YYYY-MM-DD`. Selection is POST JSON; totals/table are GET query parameters. Totals keys: registration, project, view, shortlist. Table rows add date, with a final Total row when activity exists.

Use status codes for transport outcomes. A diagram's integer return is carried as `data.result` where relevant; this is distinct from the HTTP status. GET requests do not increment views; POST views is explicit. Duplicate favourites and shortlists do not create another row/event.
