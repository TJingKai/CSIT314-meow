# MEOW REST API

Base URL: `http://127.0.0.1:8000/api/v1`. The frontend and API share one origin. There is no permissive CORS configuration and no token stored in browser JavaScript storage.

## Conventions

- JSON request bodies use `Content-Type: application/json`. File uploads and designer registrations use `multipart/form-data`.
- Successful JSON responses use `{ "data": ... }`. Project lists also include `meta: { page, limit, total }`.
- Errors use `{ "error": { "code": "validation_error", "message": "Readable explanation." } }`.
- `200` indicates success, `201` creation, and `204` successful deletion with no body.
- `400` indicates validation failure, `401` no valid session, `403` denied role/ownership/CSRF, `404` missing or hidden resource, `409` conflict, `413` oversized upload, and `429` login throttling.
- IDs are opaque strings. Do not construct them from names or indexes.
- GET requests do not record views or change user data. Opening a detail page triggers an explicit POST view request from the frontend.
- PUT favourite saving is idempotent: a duplicate returns `200` and does not add another shortlist event. DELETE favourite removal is also idempotent.
- Reports use Singapore calendar dates; timestamps are stored in UTC. Weekly reports start Monday.

## Authentication and CSRF

1. POST `/auth/session` with email and password. The response sets an HttpOnly session cookie.
2. GET `/auth/session` to obtain the current user and `csrfToken`.
3. Send the cookie plus `X-CSRF-Token: <csrfToken>` on every authenticated POST, PUT, PATCH or DELETE, including multipart uploads.
4. DELETE `/auth/session` logs out and revokes the server-side session. Sessions expire after eight hours. Suspended/rejected accounts cannot use old sessions.

Login failures are limited to ten attempts per email/IP in a fifteen-minute window. The development/assessment runner binds to localhost by default; it does not trust arbitrary forwarding headers.

Example using Python's standard library:

```python
import http.cookiejar
import json
import urllib.request

base = 'http://127.0.0.1:8000/api/v1'
client = urllib.request.build_opener(
    urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
)

login = urllib.request.Request(
    base + '/auth/session',
    data=json.dumps({
        'email': 'customer@meow.demo',
        'password': 'MEOWDemo!2026',
    }).encode(),
    headers={'Content-Type': 'application/json'},
    method='POST',
)
client.open(login).close()
session = json.load(client.open(base + '/auth/session'))['data']
projects = json.load(client.open(base + '/projects?q=Slow'))['data']
request = urllib.request.Request(
    base + '/me/favourites/' + projects[0]['id'],
    headers={'X-CSRF-Token': session['csrfToken']},
    method='PUT',
)
print(json.load(client.open(request)))
```

## Resources

| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| GET | /health | Anyone | Health status |
| POST | /registrations | Anyone | Create Customer or pending Interior Designer |
| POST | /auth/session | Anyone; CSRF if already logged in | Authenticate and create a session |
| GET | /auth/session | Anyone | Current user/CSRF, or null user |
| DELETE | /auth/session | Any logged-in role | Revoke current session |
| PATCH | /me | Any logged-in role | Edit name/contact/preferences |
| PUT | /me/password | Any logged-in role | Change password and revoke all sessions |
| GET | /users | User Admin | Search/list accounts |
| POST | /users | User Admin | Create any role; designers begin pending |
| PATCH | /users/{id} | User Admin | Set role/status and revoke sessions |
| DELETE | /users/{id} | User Admin | Delete account and owned content |
| GET | /users/{id}/verification | User Admin | Review company and private proof link |
| PUT | /users/{id}/verification | User Admin | Approve or reject designer |
| GET | /companies | Any logged-in role | Active studio list |
| GET | /companies/{id} | Any logged-in role | Active studio details; owner can also see own pending profile |
| GET | /me/company | Interior Designer | Own profile including proof link |
| PUT | /me/company | Interior Designer | Create/replace company details and optional branding references |
| POST | /me/company/proof | Interior Designer | Upload/replace proof; account returns to pending verification |
| GET | /categories | Any logged-in role | Active categories; Manager sees all |
| POST | /categories | Platform Manager | Create category |
| PATCH | /categories/{id} | Platform Manager | Edit name or active state |
| GET | /projects | Any logged-in role | Search/list published projects; optional owned/favourite lists |
| GET | /projects/{id} | Any logged-in role | Published details; owner and Manager may inspect hidden projects |
| POST | /projects | Verified Interior Designer | Create own draft |
| PATCH | /projects/{id} | Verified owner | Edit details, categories, added photos and cover |
| DELETE | /projects/{id} | Designer owner | Delete project |
| PUT | /projects/{id}/publication | Verified owner | Publish/unpublish |
| PUT | /projects/{id}/archive | Designer owner | Archive/restore; archiving unpublishes |
| POST | /projects/{id}/views | Any logged-in role | Record view of a currently published project |
| PUT | /me/favourites/{projectId} | Customer | Save once and record shortlist if new |
| DELETE | /me/favourites/{projectId} | Customer | Remove favourite; keep historical event |
| POST | /uploads | Interior Designer | Upload one project/branding image |
| GET | /uploads/{id}/content | Authorised logged-in user | Deliver image or private proof |
| GET | /me/analytics | Interior Designer | Own project totals and date range |
| GET | /reports/activity | Platform Manager | Activity totals and rankings for selected period |
| GET | /moderation/projects | Platform Manager | Project moderation list |
| PUT | /projects/{id}/moderation | Platform Manager | Take down with reason; designer cannot republish |

## Registration

Customer JSON fields: `name`, `email`, `password`, `confirmPassword`, `role: "customer"`.

Designer multipart fields: the same account fields with `role: "designer"`, plus `company`, `uen`, `year`, and file `proof`. Proof supports PDF, PNG, JPEG or WebP, at most 2MB. Email is case-insensitively unique. Passwords must be 10–128 characters. Public registrations cannot select Admin or Manager roles.

## Project payloads

Create a draft after uploading photos:

```json
{
  "title": "A Calm City Home",
  "description": "Warm textures and natural light.",
  "property": "HDB",
  "location": "Bishan",
  "style": "Japandi",
  "stage": "Current",
  "duration": "12 weeks",
  "size": 100,
  "budget": 75000,
  "categories": ["<active-category-id>"],
  "photoUploadIds": ["<owned-image-upload-id>"]
}
```

`size` must be positive; `budget` must be nonnegative. `stage` is `Current` or `Past`, independent from publication. At least one active category and one uploaded image are required for creation. Maximum 20 photos. A project starts unpublished.

PATCH accepts any of the project fields above. `photoUploadIds` adds images; existing images remain. To choose a cover, send `coverPhotoId` from the project's returned `photoIds` array. Ownership is checked for every upload ID and project. Client fields such as `owner`, `published` or `role` do not override server decisions.

Publication body: `{ "published": true }`. Archive body: `{ "archived": true }`. Moderation body: `{ "reason": "Guideline violation" }`. Taken-down projects cannot be republished by their owner.

## List queries

`GET /projects` supports `q`, `category` (ID), `style`, `maxBudget`, `property`, `location`, `company` (ID), `sort`, `page`, `limit`. Search matches title, description, location, style and company name. Filters combine with AND. `%` and `_` are literal search characters.

- `sort`: `newest` (default), `views` or `saves`.
- `page`: positive integer, default 1.
- `limit`: 1–100, default 24.
- `mine=true`: Interior Designer's own projects including hidden work.
- `favourites=true`: Customer's own saved projects, filtered to currently published/visible work.
- Other customers' favourite lists cannot be requested through a supplied user ID.

`GET /users` supports `q` and `status`. `GET /me/analytics` accepts optional `from` and `to` ISO dates, inclusive. `GET /reports/activity` requires `date=YYYY-MM-DD` and `period=daily|weekly|monthly`.

## Company and management payloads

Company PUT requires `name`, `uen`, and `year`, plus optional `description`, `styles`, `contact`, `hours`, `areas`, `awards`, `logo_id`, `banner_id`. Omitted text fields become empty because PUT replaces the profile representation. Branding IDs must reference an image owned by the designer; set null to clear. Replacing proof uses multipart `file` at `/me/company/proof` and resets verification.

Category POST: `{ "name": "Kitchen" }`. Category PATCH: `{ "name": "Kitchen & Dining", "active": true }`. Names are case-insensitively unique. Deactivation leaves historical assignments intact while blocking new assignments.

User POST: `name`, `email`, `password`, `role`. User PATCH: `role` and/or `status` (`active` or `suspended`). Admin cannot change or delete its own account. Assigning a new designer role requires verification; generic activation cannot approve an unverified designer.

Verification PUT: `{ "decision": "approve" }` or `{ "decision": "reject" }`. Approval requires company data and a stored proof document, records verification and revokes prior sessions.

Password PUT: `currentPassword`, `password`, `confirmPassword`. All sessions are revoked after success.

## File handling

POST `/uploads` and `/me/company/proof` accept one multipart field named `file`. The API returns an upload ID or proof URL. Content types are checked using file signatures and responses carry `nosniff`; user filenames are not used as disk paths. Proof downloads require owner/Admin access. Images are visible to owners or users authorised to view their published project/active company. Hidden project images cannot be retrieved by guessing their URL.
