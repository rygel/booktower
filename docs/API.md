# API Reference

All API endpoints are served by the same process as the web UI. JSON endpoints return `Content-Type: application/json`. HTML endpoints return `Content-Type: text/html` and are designed for HTMX partial updates.

## Authentication

Most endpoints require authentication. Two mechanisms are supported:

1. **Cookie** — a `token=<jwt>` cookie set on login. Used by the browser UI.
2. **Bearer token** — an `Authorization: Bearer <api_token>` header. API tokens are managed in Profile → API Tokens.

Unauthenticated requests to protected endpoints return `401 Unauthorized`.

---

## 1. Authentication

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/register` | No | Create a new account |
| `POST` | `/auth/login` | No | Login with username/email and password; sets `token` cookie |
| `POST` | `/auth/logout` | No | Clear the session cookie |
| `POST` | `/auth/forgot-password` | No | Request a password-reset token (admin-mediated) |
| `POST` | `/auth/reset-password` | No | Reset password using a token |
| `POST` | `/auth/refresh` | No | Refresh a JWT session token |
| `POST` | `/auth/revoke` | No | Revoke a refresh token |
| `POST` | `/api/auth/change-password` | Yes | Change password (authenticated user) |
| `POST` | `/api/auth/change-email` | Yes | Change email address (authenticated user) |

### OIDC / SSO

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/auth/oidc/login` | No | Redirect to OIDC provider login |
| `GET` | `/auth/oidc/callback` | No | OIDC callback (processes authorization code) |
| `POST` | `/auth/oidc/backchannel-logout` | No | OIDC backchannel logout endpoint |
| `GET` | `/api/oidc/status` | No | Check whether OIDC is enabled and configured |

---

## 2. Libraries

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/libraries` | Yes | List all libraries for the current user |
| `POST` | `/api/libraries` | Yes | Create a library |
| `DELETE` | `/api/libraries/{id}` | Yes | Delete a library (files not deleted from disk) |
| `GET` | `/api/libraries/{id}/settings` | Yes | Get library settings |
| `PUT` | `/api/libraries/{id}/settings` | Yes | Update library settings |
| `POST` | `/api/libraries/{id}/organize` | Yes | Organize library folder structure |
| `POST` | `/api/libraries/{id}/scan` | Yes | Scan library folder (synchronous) |
| `POST` | `/api/libraries/{id}/scan/async` | Yes | Start background folder scan; returns `{ "jobId": "..." }` |
| `GET` | `/api/libraries/{id}/scan/{jobId}` | Yes | Poll scan job status |
| `POST` | `/api/libraries/{id}/icon` | Yes | Upload a library icon |
| `GET` | `/api/libraries/{id}/icon` | Yes | Get the library icon |
| `DELETE` | `/api/libraries/{id}/icon` | Yes | Delete the library icon |
| `GET` | `/api/libraries/health` | Yes | Library health check (missing files, orphaned records) |

### BookDrop

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/bookdrop` | Yes | List pending files in the drop folder |
| `POST` | `/api/bookdrop/{filename}/import` | Yes | Import a dropped file into a library |
| `DELETE` | `/api/bookdrop/{filename}` | Yes | Discard a dropped file |

---

## 3. Books

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/books` | Yes | List books (supports query/filter params) |
| `POST` | `/api/books` | Yes | Create a book record |
| `GET` | `/api/books/{id}` | Yes | Get book details |
| `PUT` | `/api/books/{id}` | Yes | Update book metadata |
| `DELETE` | `/api/books/{id}` | Yes | Delete a book |
| `PUT` | `/api/books/{id}/progress` | Yes | Save reading progress |
| `POST` | `/api/books/{id}/status` | Yes | Set reading status (WANT_TO_READ, READING, FINISHED, NONE) |
| `POST` | `/api/books/{id}/merge` | Yes | Merge another book into this one |
| `GET` | `/api/books/{id}/community-rating` | Yes | Get community rating for a book |
| `POST` | `/api/books/{id}/community-rating/fetch` | Yes | Fetch community rating from external sources |
| `GET` | `/api/books/{id}/sessions` | Yes | Get reading sessions for a book |
| `GET` | `/api/recent` | Yes | List recently accessed books |
| `GET` | `/api/search` | Yes | Search books by title, author, etc. |
| `POST` | `/api/books/{id}/apply-filename-metadata` | Yes | Extract and apply metadata from filename |
| `POST` | `/api/books/{id}/apply-sidecar-metadata` | Yes | Apply metadata from sidecar file (OPF, etc.) |
| `GET` | `/api/books/{id}/similar` | Yes | Get similar book recommendations |

### Bulk Operations

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/books/bulk/move` | Yes | Move multiple books to a library |
| `POST` | `/api/books/bulk/delete` | Yes | Delete multiple books |
| `POST` | `/api/books/bulk/tag` | Yes | Replace tags on multiple books |
| `POST` | `/api/books/bulk/status` | Yes | Set reading status on multiple books |

---

## 4. Book Metadata

| Method | Path | Auth | Description |
|---|---|---|---|
| `PUT` | `/api/books/{id}/authors` | Yes | Set the authors list |
| `PUT` | `/api/books/{id}/categories` | Yes | Set categories/genres |
| `PUT` | `/api/books/{id}/moods` | Yes | Set mood tags |
| `PUT` | `/api/books/{id}/extended-metadata` | Yes | Set extended metadata fields |
| `PUT` | `/api/books/{id}/external-ids` | Yes | Set external identifiers (ISBN, Goodreads, etc.) |
| `PUT` | `/api/books/{id}/reading-direction` | Yes | Set reading direction (LTR/RTL) |
| `GET` | `/api/books/{id}/comic-metadata` | Yes | Get comic-specific metadata (series, issue, etc.) |
| `PUT` | `/api/books/{id}/comic-metadata` | Yes | Update comic-specific metadata |

---

## 5. Metadata Fetch

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/metadata/search` | Yes | Search external metadata sources (`?title=&author=&source=`) |
| `GET` | `/api/metadata/sources` | Yes | List available metadata sources |
| `POST` | `/api/books/{id}/metadata/propose` | Yes | Fetch and create a metadata change proposal |
| `GET` | `/api/books/{id}/metadata/proposals` | Yes | List pending metadata proposals for a book |
| `POST` | `/api/books/{id}/metadata/proposals/{proposalId}/apply` | Yes | Apply a metadata proposal |
| `DELETE` | `/api/books/{id}/metadata/proposals/{proposalId}` | Yes | Dismiss a metadata proposal |
| `GET` | `/api/authors/{name}/metadata` | Yes | Fetch author biographical metadata |
| `GET` | `/api/books/{id}/metadata-locks` | Yes | Get locked metadata fields for a book |
| `PUT` | `/api/books/{id}/metadata-locks` | Yes | Set locked metadata fields |

---

## 6. Files & Formats

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/books/{id}/upload` | Yes | Upload a book file (PDF, EPUB, CBZ, CBR, etc.) |
| `GET` | `/api/books/{id}/file` | Yes | Download the book file |
| `GET` | `/api/books/{id}/kepub` | Yes | Download as Kobo KEPUB |
| `GET` | `/api/books/{id}/read-content` | Yes | Serve book content for the in-browser reader |
| `POST` | `/api/books/{id}/cover` | Yes | Upload a cover image |
| `GET` | `/api/books/{id}/covers/alternatives` | Yes | Get alternative cover images |
| `POST` | `/api/books/{id}/cover/apply-url` | Yes | Apply a cover image from a URL |
| `GET` | `/api/books/{id}/formats` | Yes | List alternative file formats for a book |
| `POST` | `/api/books/{id}/formats` | Yes | Add an alternative file format |
| `DELETE` | `/api/books/{id}/formats/{fileId}` | Yes | Remove an alternative file format |
| `GET` | `/api/books/{id}/audio` | Yes | Stream audiobook audio |
| `GET` | `/api/books/{id}/chapters` | Yes | List audiobook chapters/tracks |
| `POST` | `/api/books/{id}/chapters` | Yes | Upload an audiobook chapter |
| `GET` | `/api/books/{id}/chapters/{trackIndex}` | Yes | Stream a specific chapter |
| `DELETE` | `/api/books/{id}/chapters/{trackIndex}` | Yes | Delete a chapter |
| `PUT` | `/api/books/{id}/chapters/{trackIndex}` | Yes | Update chapter metadata |
| `GET` | `/api/books/{id}/comic/pages` | Yes | Get comic page count |
| `GET` | `/api/books/{id}/comic/{page}` | Yes | Serve a comic page image (0-indexed) |
| `GET` | `/api/formats` | No | List all supported file formats and MIME types |
| `POST` | `/api/covers/regenerate` | Yes | Bulk regenerate cover thumbnails |

### Cover Image (public path)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/covers/{filename}` | No | Serve a cover image by filename |

---

## 7. Bookmarks & Annotations

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/bookmarks` | Yes | List bookmarks (all books or filtered) |
| `POST` | `/api/bookmarks` | Yes | Create a bookmark |
| `DELETE` | `/api/bookmarks/{id}` | Yes | Delete a bookmark |

Annotation endpoints are served via the HTMX UI layer:

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/ui/books/{id}/annotations` | Yes | List annotations for a book (HTML partial) |
| `POST` | `/ui/books/{id}/annotations` | Yes | Create an annotation (HTML partial) |
| `DELETE` | `/ui/annotations/{id}` | Yes | Delete an annotation (HTML partial) |

---

## 8. Journals, Notebooks & Reviews

### Journal Entries

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/books/{id}/journal` | Yes | List journal entries for a book |
| `POST` | `/api/books/{id}/journal` | Yes | Create a journal entry |
| `PUT` | `/api/books/{id}/journal/{entryId}` | Yes | Update a journal entry |
| `DELETE` | `/api/books/{id}/journal/{entryId}` | Yes | Delete a journal entry |
| `GET` | `/api/journal` | Yes | List all journal entries across books |

### Notebooks

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/books/{id}/notebooks` | Yes | List notebooks for a book |
| `POST` | `/api/books/{id}/notebooks` | Yes | Create a notebook |
| `GET` | `/api/books/{id}/notebooks/{notebookId}` | Yes | Get a notebook |
| `PUT` | `/api/books/{id}/notebooks/{notebookId}` | Yes | Update a notebook |
| `DELETE` | `/api/books/{id}/notebooks/{notebookId}` | Yes | Delete a notebook |

### Reviews

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/books/{id}/reviews` | Yes | List reviews for a book |
| `POST` | `/api/books/{id}/reviews` | Yes | Create a review |
| `PUT` | `/api/books/{id}/reviews/{reviewId}` | Yes | Update a review |
| `DELETE` | `/api/books/{id}/reviews/{reviewId}` | Yes | Delete a review |

---

## 9. Smart Shelves

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/shelves` | Yes | List smart shelves |
| `POST` | `/api/shelves` | Yes | Create a smart shelf |
| `POST` | `/api/shelves/{id}/share` | Yes | Generate a public share link for a shelf |
| `DELETE` | `/api/shelves/{id}/share` | Yes | Revoke the public share link |
| `GET` | `/public/shelf/{token}` | No | View a publicly shared shelf |

---

## 10. Reading & Listening Stats

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/stats/reading` | Yes | Reading statistics (`?days=N`, default 365) |
| `GET` | `/api/stats/listening` | Yes | Listening statistics (`?days=N`, default 365) |

---

## 11. Audiobook

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/books/{id}/listen` | Yes | Record a listening session |
| `GET` | `/api/books/{id}/listen-progress` | Yes | Get audiobook listening progress |
| `PUT` | `/api/books/{id}/listen-progress` | Yes | Update audiobook listening progress |
| `GET` | `/api/listen-sessions` | Yes | Get recent listening sessions (`?limit=N`) |
| `GET` | `/api/books/{id}/audiobook-meta` | Yes | Get audiobook metadata (narrator, duration, etc.) |
| `PUT` | `/api/books/{id}/audiobook-meta` | Yes | Update audiobook metadata |
| `DELETE` | `/api/books/{id}/audiobook-meta` | Yes | Delete audiobook metadata |
| `POST` | `/api/books/{id}/audiobook-cover` | Yes | Upload an audiobook cover image |
| `GET` | `/api/books/{id}/audiobook-cover` | Yes | Get the audiobook cover image |

---

## 12. User Settings & Preferences

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/settings` | Yes | Get all user settings |
| `PUT` | `/api/settings/{key}` | Yes | Set a user setting |
| `DELETE` | `/api/settings/{key}` | Yes | Delete a user setting |
| `GET` | `/api/user/preferences/browse-mode` | Yes | Get browse mode (grid, list, table) |
| `PUT` | `/api/user/preferences/browse-mode` | Yes | Set browse mode |
| `GET` | `/api/user/content-restrictions` | Yes | Get content restriction settings |
| `PUT` | `/api/user/content-restrictions` | Yes | Update content restriction settings |

### Filter Presets

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/user/filter-presets` | Yes | List saved filter presets |
| `POST` | `/api/user/filter-presets` | Yes | Create a filter preset |
| `GET` | `/api/user/filter-presets/{id}` | Yes | Get a filter preset |
| `PUT` | `/api/user/filter-presets/{id}` | Yes | Update a filter preset |
| `DELETE` | `/api/user/filter-presets/{id}` | Yes | Delete a filter preset |

### OPDS Credentials

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/user/opds-credentials` | Yes | Get OPDS credential status |
| `PUT` | `/api/user/opds-credentials` | Yes | Set OPDS username and password |
| `DELETE` | `/api/user/opds-credentials` | Yes | Clear OPDS credentials |

### Telemetry

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/telemetry/status` | Yes | Get telemetry opt-in status |
| `POST` | `/api/telemetry/opt-in` | Yes | Opt in to telemetry |
| `POST` | `/api/telemetry/opt-out` | Yes | Opt out of telemetry |

### Setup Wizard

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/setup/status` | Yes | Get first-run setup wizard status |
| `POST` | `/api/setup/complete` | Yes | Mark setup wizard as completed |
| `POST` | `/api/setup/steps/{step}/complete` | Yes | Mark a setup step as completed |

### API Tokens

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/tokens` | Yes | List API tokens |
| `POST` | `/api/tokens` | Yes | Create a new API token (token shown only once) |
| `DELETE` | `/api/tokens/{id}` | Yes | Revoke an API token |

### Export & Import

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/export` | Yes | Download all user data as JSON |
| `POST` | `/api/import/goodreads` | Yes | Import books from a Goodreads CSV export |

### Background Tasks

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/tasks` | Yes | List background tasks for the current user |
| `DELETE` | `/api/tasks/{id}` | Yes | Dismiss a background task |

---

## 13. Notifications

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/notifications` | Yes | List notifications (`?unread=true` to filter) |
| `GET` | `/api/notifications/count` | Yes | Get unread notification count |
| `GET` | `/api/notifications/stream` | Yes | Server-sent event stream for notifications |
| `POST` | `/api/notifications/read-all` | Yes | Mark all notifications as read |
| `POST` | `/api/notifications/{id}/read` | Yes | Mark a single notification as read |
| `DELETE` | `/api/notifications/{id}` | Yes | Delete a notification |

---

## 14. Device Sync

### OPDS Catalog (HTTP Basic Auth)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/opds/catalog` | Basic | Root OPDS catalog feed |
| `GET` | `/opds/catalog/{libraryId}` | Basic | OPDS feed for a specific library |
| `GET` | `/opds/books/{id}/file` | Basic | Download a book via OPDS |
| `GET` | `/opds/books/{id}/chapters/{trackIndex}` | Basic | Stream an audiobook chapter via OPDS |

### Kobo Sync

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/kobo/devices` | Yes | Register a Kobo device |
| `GET` | `/api/kobo/devices` | Yes | List registered Kobo devices |
| `DELETE` | `/api/kobo/devices/{token}` | Yes | Delete a Kobo device |
| `GET` | `/kobo/{token}/v1/initialization` | Token | Kobo initialization endpoint |
| `POST` | `/kobo/{token}/v1/library/sync` | Token | Kobo library sync |
| `GET` | `/kobo/{token}/v1/library/snapshot` | Token | Kobo library snapshot |
| `PUT` | `/kobo/{token}/v1/library/{bookId}/reading-state` | Token | Update Kobo reading state |

### KOReader Sync (kosync protocol)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/koreader/devices` | Yes | Register a KOReader device |
| `GET` | `/api/koreader/devices` | Yes | List registered KOReader devices |
| `DELETE` | `/api/koreader/devices/{token}` | Yes | Delete a KOReader device |
| `PUT` | `/koreader/{token}/syncs/progress` | Token | Push reading progress from KOReader |
| `GET` | `/koreader/{token}/syncs/progress/{document}` | Token | Get reading progress for a document |

---

## 15. Admin

Admin endpoints require the authenticated user to have the admin role.

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/admin/users` | Admin | List all users |
| `POST` | `/api/admin/users/{userId}/promote` | Admin | Grant admin role |
| `POST` | `/api/admin/users/{userId}/demote` | Admin | Revoke admin role |
| `POST` | `/api/admin/users/{userId}/reset-password` | Admin | Generate a password reset link |
| `DELETE` | `/api/admin/users/{userId}` | Admin | Delete a user |
| `GET` | `/api/admin/users/{userId}/permissions` | Admin | Get user permissions |
| `PUT` | `/api/admin/users/{userId}/permissions` | Admin | Set user permissions |
| `GET` | `/api/admin/users/{userId}/library-access` | Admin | Get library access rules for a user |
| `PUT` | `/api/admin/users/{userId}/library-access` | Admin | Set library-restricted mode for a user |
| `POST` | `/api/admin/users/{userId}/library-access` | Admin | Grant library access to a user |
| `DELETE` | `/api/admin/users/{userId}/library-access/{libraryId}` | Admin | Revoke library access |
| `GET` | `/api/admin/password-reset-tokens` | Admin | List active password reset tokens |
| `GET` | `/api/admin/duplicates` | Admin | Find duplicate books |
| `GET` | `/api/admin/comic-page-duplicates` | Admin | Find duplicate comic pages |
| `GET` | `/api/admin/audit` | Admin | List audit log entries |
| `GET` | `/api/admin/tasks` | Admin | List all background tasks (all users) |
| `GET` | `/api/admin/telemetry/stats` | Admin | Get aggregated telemetry statistics |

### Email Providers

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/admin/email-providers` | Admin | List email providers |
| `POST` | `/api/admin/email-providers` | Admin | Create an email provider |
| `PUT` | `/api/admin/email-providers/{id}` | Admin | Update an email provider |
| `DELETE` | `/api/admin/email-providers/{id}` | Admin | Delete an email provider |
| `POST` | `/api/admin/email-providers/{id}/set-default` | Admin | Set the default email provider |

### Scheduled Tasks

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/admin/scheduled-tasks` | Admin | List scheduled tasks |
| `POST` | `/api/admin/scheduled-tasks` | Admin | Create a scheduled task |
| `PUT` | `/api/admin/scheduled-tasks/{id}` | Admin | Update a scheduled task |
| `DELETE` | `/api/admin/scheduled-tasks/{id}` | Admin | Delete a scheduled task |
| `POST` | `/api/admin/scheduled-tasks/{id}/trigger` | Admin | Trigger a scheduled task immediately |
| `GET` | `/api/admin/scheduled-tasks/{id}/history` | Admin | Get execution history for a task |

### Seeding (development/demo)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/admin/seed` | Admin | Seed the database with sample data |
| `POST` | `/admin/seed/files` | Admin | Seed sample book files |
| `POST` | `/admin/seed/librivox` | Admin | Seed from LibriVox public domain audiobooks |

### Weblate Translation Sync

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/weblate/status` | Admin | Get Weblate sync status |
| `POST` | `/api/weblate/pull` | Admin | Pull translations from Weblate |
| `POST` | `/api/weblate/push` | Admin | Push translations to Weblate |

---

## 16. Delivery

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/books/{id}/send` | Yes | Send a book to an email recipient |
| `GET` | `/api/delivery/recipients` | Yes | List delivery recipients |
| `POST` | `/api/delivery/recipients` | Yes | Add a delivery recipient |
| `DELETE` | `/api/delivery/recipients/{id}` | Yes | Delete a delivery recipient |

---

## 17. Hardcover Sync

| Method | Path | Auth | Description |
|---|---|---|---|
| `PUT` | `/api/user/hardcover/key` | Yes | Set Hardcover.app API key |
| `GET` | `/api/user/hardcover/status` | Yes | Check Hardcover connection status |
| `POST` | `/api/books/{id}/hardcover/sync` | Yes | Sync a book's status/progress to Hardcover |
| `GET` | `/api/books/{id}/hardcover/mapping` | Yes | Get Hardcover book mapping |

---

## 18. System

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/health` | No | Health check (`{"status":"ok"}`) |
| `GET` | `/api/version` | No | Application version info |
| `GET` | `/api/demo/status` | No | Check if demo mode is active |
| `GET` | `/manifest.json` | No | PWA manifest |

### Fonts

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/fonts` | Yes | List uploaded custom fonts |
| `POST` | `/api/fonts` | Yes | Upload a custom font |
| `DELETE` | `/api/fonts/{id}` | Yes | Delete a custom font |
| `GET` | `/fonts/{userId}/{filename}` | No | Serve a custom font file |

### Reader Preferences

Per-user, per-format reading preferences (theme, font, typography, layout). Preferences are scoped to a **device** via the optional `?device=` query parameter.

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/reader-preferences/{format}` | Yes | Get saved preferences for a format |
| `PUT` | `/api/reader-preferences/{format}` | Yes | Replace all preferences for a format |
| `PATCH` | `/api/reader-preferences/{format}` | Yes | Merge partial preference updates |
| `DELETE` | `/api/reader-preferences/{format}` | Yes | Reset preferences for a format |

Supported formats: `epub`, `pdf`, `cbz`, `cbr`, `comic`.

---

## 19. Page Endpoints (HTML)

Server-rendered HTML pages. All require authentication except login, register, and password reset.

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/` | No | Dashboard (redirects to login if unauthenticated) |
| `GET` | `/login` | No | Login page |
| `GET` | `/register` | No | Registration page |
| `GET` | `/forgot-password` | No | Password reset request page |
| `GET` | `/reset-password` | No | Password reset form |
| `GET` | `/libraries` | Yes | All libraries |
| `GET` | `/libraries/{id}` | Yes | Library detail with book grid |
| `GET` | `/books/{id}` | Yes | Book detail page |
| `GET` | `/books/{id}/read` | Yes | In-browser reader (PDF, EPUB, comic, audiobook) |
| `GET` | `/search` | Yes | Full-text search |
| `GET` | `/queue` | Yes | Reading queue |
| `GET` | `/series` | Yes | Series list |
| `GET` | `/series/{name}` | Yes | Series detail |
| `GET` | `/authors` | Yes | Author list |
| `GET` | `/authors/{name}` | Yes | Author detail |
| `GET` | `/tags` | Yes | Tag list |
| `GET` | `/tags/{name}` | Yes | Tag detail |
| `GET` | `/profile` | Yes | Profile and settings |
| `GET` | `/analytics` | Yes | Reading analytics dashboard |
| `GET` | `/admin` | Admin | Admin panel |
| `GET` | `/shelves/{id}` | Yes | Smart shelf detail page |

### HTMX UI Mutations

These endpoints return HTML partials for HTMX in-page updates.

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/ui/libraries` | Yes | Create a library |
| `DELETE` | `/ui/libraries/{id}` | Yes | Delete a library |
| `POST` | `/ui/libraries/{id}/rename` | Yes | Rename a library |
| `POST` | `/ui/libraries/{libId}/books` | Yes | Create a book in a library |
| `DELETE` | `/ui/books/{id}` | Yes | Delete a book |
| `POST` | `/ui/books/{id}/move` | Yes | Move a book |
| `POST` | `/ui/books/{id}/meta` | Yes | Edit book metadata |
| `POST` | `/ui/books/{id}/progress` | Yes | Update reading progress |
| `POST` | `/ui/books/{id}/status` | Yes | Set reading status |
| `POST` | `/ui/books/{id}/rating` | Yes | Set book rating |
| `POST` | `/ui/books/{id}/tags` | Yes | Set book tags |
| `POST` | `/ui/books/{id}/bookmarks` | Yes | Create a bookmark |
| `DELETE` | `/ui/bookmarks/{id}` | Yes | Delete a bookmark |
| `POST` | `/ui/books/{id}/fetch-metadata` | Yes | Fetch metadata from external source |
| `POST` | `/ui/goal` | Yes | Set reading goal |
| `POST` | `/ui/shelves` | Yes | Create a smart shelf |
| `DELETE` | `/ui/shelves/{id}` | Yes | Delete a smart shelf |
| `POST` | `/ui/preferences/analytics` | Yes | Toggle analytics |
| `POST` | `/preferences/theme` | No | Set UI theme (cookie-based) |
| `POST` | `/preferences/lang` | No | Set UI language (cookie-based) |

---

## Error Responses

All JSON error responses follow this shape:

```json
{ "code": "BAD_REQUEST", "message": "bookIds is required" }
```

Common HTTP status codes:

| Code | Meaning |
|---|---|
| `400` | Bad request — missing or invalid parameters |
| `401` | Unauthenticated — missing or invalid session |
| `403` | Forbidden — authenticated but insufficient permissions |
| `404` | Not found |
| `503` | Service unavailable — optional feature not enabled |
| `500` | Internal server error |
