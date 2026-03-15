# API Reference

All API endpoints are served by the same process as the web UI. JSON endpoints return `Content-Type: application/json`. HTML endpoints return `Content-Type: text/html` and are designed for HTMX partial updates.

## Authentication

Most endpoints require authentication. Two mechanisms are supported:

1. **Cookie** — a `token=<jwt>` cookie set on login. Used by the browser UI.
2. **Bearer token** — an `Authorization: Bearer <api_token>` header. API tokens are managed in Profile → API Tokens.

Unauthenticated requests to protected endpoints return `401 Unauthorized`.

## Auth Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/login` | Login with username/email and password. Sets `token` cookie. |
| `POST` | `/auth/register` | Create a new account. |
| `POST` | `/auth/logout` | Clear the session cookie. |
| `POST` | `/auth/forgot-password` | Request a password-reset token (admin-mediated). |
| `POST` | `/auth/reset-password` | Reset password using a token. |

### POST /auth/login

```json
{ "usernameOrEmail": "alice", "password": "hunter2" }
```

Success: `200 OK` — sets `token` cookie, returns `{ "token": "...", "user": { ... } }`.

### POST /auth/register

```json
{ "username": "alice", "email": "alice@example.com", "password": "hunter2" }
```

Success: `200 OK` — same response shape as login.

## Page Endpoints

Server-rendered HTML pages. All require authentication except the login/register pages.

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Dashboard — stats, recently added books, reading in progress |
| `GET` | `/libraries` | All libraries |
| `GET` | `/library/{id}` | Library detail with book grid |
| `GET` | `/book/{id}` | Book detail (metadata, upload, progress, bookmarks) |
| `GET` | `/reader/{id}` | In-browser reader for PDF, EPUB, or comic |
| `GET` | `/search` | Full-text search across titles and authors |
| `GET` | `/profile` | Profile, settings, API tokens, data export |
| `GET` | `/analytics` | Reading analytics dashboard |
| `GET` | `/admin` | Admin panel (admin role required) |
| `GET` | `/login` | Login page |
| `GET` | `/register` | Registration page |
| `GET` | `/forgot-password` | Password reset request page |
| `GET` | `/reset-password` | Password reset form |

## Library API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/libraries` | List all libraries for the current user |
| `POST` | `/api/libraries` | Create a library |
| `PUT` | `/api/libraries/{id}` | Rename a library |
| `DELETE` | `/api/libraries/{id}` | Delete a library (books not deleted from disk) |
| `POST` | `/api/libraries/{id}/scan` | Scan library folder (synchronous) |
| `POST` | `/api/libraries/{id}/scan-async` | Start background folder scan, returns `{ "jobId": "..." }` |
| `GET` | `/api/scan-jobs/{jobId}` | Poll scan job status |

### POST /api/libraries

```json
{ "name": "Sci-Fi", "path": "/data/books/sci-fi" }
```

## Book API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/books/{id}` | Get book details |
| `PUT` | `/api/books/{id}` | Update book metadata |
| `DELETE` | `/api/books/{id}` | Delete book (file on disk not deleted) |
| `POST` | `/api/books/{id}/upload` | Upload a PDF, EPUB, CBZ, or CBR file |
| `GET` | `/api/books/{id}/download` | Download the book file |
| `POST` | `/api/books/{id}/cover` | Upload a cover image |
| `GET` | `/api/books/{id}/cover` | Get cover image |
| `POST` | `/api/books/{id}/progress` | Save reading progress |
| `GET` | `/api/books/{id}/progress` | Get reading progress |
| `POST` | `/api/books/{id}/move` | Move book to another library |
| `POST` | `/api/books/{id}/fetch-metadata` | Fetch metadata from online source |

### PUT /api/books/{id}

```json
{
  "title": "Dune",
  "author": "Frank Herbert",
  "description": "...",
  "series": "Dune Chronicles",
  "seriesIndex": 1.0
}
```

## Bulk Book API

Operate on multiple books in one request. All endpoints require authentication and enforce ownership — books belonging to other users are silently ignored.

| Method | Path | Body | Description |
|---|---|---|---|
| `POST` | `/api/books/bulk/delete` | `{ "bookIds": ["uuid", ...] }` | Delete multiple books |
| `POST` | `/api/books/bulk/move` | `{ "bookIds": [...], "targetLibraryId": "uuid" }` | Move books to a library |
| `POST` | `/api/books/bulk/tag` | `{ "bookIds": [...], "tags": ["sci-fi", ...] }` | Replace tags on multiple books |
| `POST` | `/api/books/bulk/status` | `{ "bookIds": [...], "status": "READING" }` | Set reading status (`WANT_TO_READ`, `READING`, `FINISHED`, `NONE`) |

All bulk endpoints return the count of affected records:

```json
{ "moved": 3 }
{ "deleted": 2 }
{ "updated": 5 }
```

## Reader API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/books/{id}/pages/{page}` | Serve a PDF page as PNG |
| `GET` | `/api/books/{id}/epub` | Serve the raw EPUB file for the in-browser reader |
| `GET` | `/api/books/{id}/comic/pages` | Get comic page count: `{ "pageCount": 42 }` |
| `GET` | `/api/books/{id}/comic/{page}` | Serve a comic page image (0-indexed) |

## Bookmark API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/books/{id}/bookmarks` | List bookmarks for a book |
| `POST` | `/api/books/{id}/bookmarks` | Create a bookmark |
| `DELETE` | `/api/books/{id}/bookmarks/{bmId}` | Delete a bookmark |

### POST /api/books/{id}/bookmarks

```json
{ "bookId": "uuid", "page": 42, "title": "Chapter 5", "note": "Important scene" }
```

## Annotation API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/books/{id}/annotations` | List annotations (optional `?page=N` filter) |
| `POST` | `/api/books/{id}/annotations` | Create an annotation (text highlight) |
| `DELETE` | `/api/books/{id}/annotations/{annId}` | Delete an annotation |

### POST /api/books/{id}/annotations

```json
{ "page": 7, "selectedText": "the spice must flow", "color": "yellow" }
```

## Smart Shelf API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/shelves` | List smart shelves |
| `POST` | `/api/shelves` | Create a smart shelf |
| `GET` | `/api/shelves/{id}/books` | Get books matching a shelf's rule |
| `DELETE` | `/api/shelves/{id}` | Delete a smart shelf |

### POST /api/shelves

```json
{ "name": "Currently Reading", "ruleType": "STATUS", "ruleValue": "READING" }
```

`ruleType` values: `STATUS`, `TAG`, `RATING`.

## Analytics API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/analytics` | Reading stats (streak, total pages, finished books, daily chart) |
| `POST` | `/api/analytics/enable` | Enable analytics for the current user |

Analytics must be opted into per user. The API returns `403` if analytics are not enabled.

## Profile & Settings API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/settings` | Get all user settings as `{ "key": "value" }` |
| `POST` | `/api/settings` | Set a user setting |
| `POST` | `/api/profile/email` | Change email address |
| `POST` | `/api/profile/password` | Change password |
| `GET` | `/api/export` | Download all user data as JSON |

## API Token API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/tokens` | List API tokens |
| `POST` | `/api/tokens` | Create a new token (`{ "name": "OPDS Client" }`) — token shown only once |
| `DELETE` | `/api/tokens/{id}` | Revoke a token |

## Admin API

Admin endpoints require the authenticated user to have the admin role.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/admin/users` | List all users |
| `POST` | `/api/admin/users/{id}/promote` | Grant admin role |
| `POST` | `/api/admin/users/{id}/demote` | Revoke admin role |
| `DELETE` | `/api/admin/users/{id}` | Delete a user |
| `GET` | `/api/admin/password-reset-tokens` | List active (unused, unexpired) password reset tokens |

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
| `500` | Internal server error |
