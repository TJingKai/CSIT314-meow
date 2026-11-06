# Final report traceability

The source is the CSIT314 Final Report supplied in the conversation. This package implements the six core requirements and relevant additional user stories. The original diagrams define workflow responsibilities and preconditions; endpoint handlers implement those rules on the server.

| Requirement | Report stories | UI | REST resources / implementation |
| --- | --- | --- | --- |
| 1. User accounts and profiles | 220–230, 256 | Register, Login, Profile, User Accounts, Designer Verification | /registrations, /auth/session, /me, /users, /users/{id}/verification; auth.py, security.py, admin.py |
| 2. Interior designer company information | 231–232 | Company Profile, Public Studio Profile | /me/company, /companies/{id}; catalog.py |
| 3. Portfolio of current and past IDPs | 233–236, 263, 266 | My Projects, Create/Edit Project, Gallery/Cover, Publish/Unpublish | /projects, /projects/{id}, /projects/{id}/publication; projects.py |
| 4. Search, view and save IDPs | 237–240, 242, 273 | Discover, Search/Filters, Project Details, Studio Link, Favourites | /projects queries, /projects/{id}, /companies/{id}, /me/favourites/{id}; projects.py |
| 5. Record and review interest | 241, 243–245 | Project Details, Designer Analytics | /projects/{id}/views, /me/favourites/{id}, /me/analytics; projects.py, analytics.py |
| 6. Categories and platform reports | 246–251 | Categories, Daily/Weekly/Monthly Reports | /categories, /reports/activity; catalog.py, analytics.py |

Additional stories:

| Stories | Support |
| --- | --- |
| 252, 254 | Profile editing and authenticated password change |
| 255, 257 | Pending application list and role management |
| 258–261 | Company logo/banner, specialisations, contacts/hours/areas, certifications/awards |
| 262, 264–267 | Draft, archive/restore, delete, category selection and project attributes |
| 268–272 | Browse, category/style/budget/property/location filters, sorting |
| 274–275 | Remove favourite and designer date-range analytics |
| 276–279 | Category deactivation, top viewed/shortlisted reports and take-down moderation |
| 253 | Email-based forgotten-password recovery is not connected; requires a mail service and recovery workflow |

## Exceptions aligned with the diagrams

- Invalid email, duplicate email, mismatched passwords or missing business details produce validation/conflict responses.
- Pending/unverified designers cannot create or publish projects.
- Non-owner designers cannot edit, delete, publish or archive another designer's project.
- No project matches: the discovery screen displays an empty-results state.
- Duplicate favourite: the server reports already saved and creates no duplicate shortlist event.
- Missing category selection: project creation/update fails transactionally.
- Unpublished/archived/taken-down/inactive-owner projects are excluded from customer results and cannot record view/save events.
- Deactivated categories remain historically assigned but are excluded from new selection and assignment.
- Reports use an explicit date and daily/weekly/monthly period, returning both totals and rankings as tables.
