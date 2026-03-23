# Runary Backend Architecture

## Overview

Runary is a self-hosted digital library management application built with
**Kotlin** and **http4k**. It follows a thin handler → service → database
layering with no annotation magic: all wiring is explicit, making the
dependency graph trivially readable from the source.

The application is a single executable JAR that serves both the JSON API and
server-rendered HTML pages (HTMX-driven UI). No separate frontend build is
required.

---

## Technology Stack

| Concern | Library | Version |
|---|---|---|
| Language | Kotlin | 2.3.10 |
| HTTP framework | http4k | 6.1.0.0 |
| Templates | JTE (Kotlin flavour, `.kte`) | 3.2.3 |
| DI container | Koin | 3.5.6 |
| SQL access | JDBI 3 | 3.46.0 |
| JSON | Jackson + kotlin-module | 2.18.2 |
| DB migrations | Flyway | (Maven plugin) |
| Default DB | H2 file-backed (PostgreSQL mode) | 2.2.x |
| Optional DB | PostgreSQL | 42.7.x |
| Build | Maven | — |
| Auth | JWT (jjwt) | — |
| PDF metadata | Apache PDFBox | — |
| EPUB metadata | epublib | — |
| CSV import | Apache Commons CSV | — |

---

## Directory Structure

```
src/main/kotlin/org/runary/
├── Main.kt                        # Entry point — builds the app via Koin
├── config/
│   ├── AppConfig.kt               # Typed config loaded from application.conf
│   ├── Database.kt                # JDBI setup and connection pool
│   ├── Json.kt                    # Shared ObjectMapper
│   ├── KoinModule.kt              # Koin DI module — single source of truth for wiring
│   └── TemplateRenderer.kt        # JTE template engine wrapper
├── filters/
│   ├── JwtAuthFilter.kt           # Extracts and validates the token cookie
│   ├── AdminFilter.kt             # Rejects non-admin users with 403
│   ├── CsrfFilter.kt              # Origin / Referer host check
│   ├── GlobalErrorFilter.kt       # Catches unhandled exceptions → 500 JSON
│   ├── RateLimitFilter.kt         # Per-IP rate limiting (auth endpoints)
│   └── StaticCacheFilter.kt       # Cache-Control headers for /static/**
├── handlers/
│   ├── Handlers.kt                # AppHandler: constructor-wires every handler,
│   │                              #   defines all routes via http4k routing DSL
│   ├── PageHandler.kt             # HTML page routes (libraries, books, series, …)
│   ├── AuthHandler2.kt            # Register / login / logout / password reset
│   ├── BookHandler2.kt            # JSON CRUD for books + sessions endpoint
│   ├── LibraryHandler2.kt         # JSON CRUD for libraries + folder scan
│   ├── FileHandler.kt             # Book file upload/download + cover serving
│   ├── BulkBookHandler.kt         # Bulk move / delete / tag / status
│   ├── AdminHandler.kt            # Admin panel (user management)
│   ├── UserSettingsHandler.kt     # Per-user key-value settings
│   ├── BookmarkHandler.kt         # Bookmark CRUD
│   ├── AnnotationHandler.kt       # Highlight / annotation CRUD
│   ├── ApiTokenHandler.kt         # Programmatic API token management
│   ├── ExportHandler.kt           # Full data export as JSON
│   ├── GoodreadsImportHandler.kt  # Goodreads CSV import
│   ├── OpdsHandler.kt             # OPDS 1.2 catalog for e-reader clients
│   └── WeblateHandler.kt          # Translation pull from Weblate
├── services/
│   ├── AuthService.kt             # User registration, login, password hashing
│   ├── JwtService.kt              # JWT sign / verify / claims extraction
│   ├── BookService.kt             # Book CRUD, search, progress, series/author queries
│   ├── LibraryService.kt          # Library CRUD, folder scan (PDF/EPUB discovery)
│   ├── BookmarkService.kt         # Bookmark CRUD
│   ├── AnnotationService.kt       # Highlight CRUD
│   ├── UserSettingsService.kt     # Per-user key-value persistence
│   ├── AnalyticsService.kt        # Daily pages-read, streak, chart data
│   ├── ReadingSessionService.kt   # Per-session reading log (start/end page)
│   ├── PdfMetadataService.kt      # PDF cover + metadata extraction (PDFBox)
│   ├── EpubMetadataService.kt     # EPUB cover + metadata extraction
│   ├── MetadataFetchService.kt    # External metadata lookup (Open Library etc.)
│   ├── MagicShelfService.kt       # Smart shelves (rule-based book collections)
│   ├── AdminService.kt            # User listing, admin grant/revoke, deletion
│   ├── ApiTokenService.kt         # API token hashing and validation
│   ├── ExportService.kt           # Full export serialisation
│   ├── GoodreadsImportService.kt  # Goodreads CSV parsing and book creation
│   ├── PasswordResetService.kt    # Self-hosted password reset token flow
│   ├── ComicService.kt            # CBZ/CBR page extraction
│   └── ScanScheduleService.kt     # Background auto-scan scheduler
├── models/
│   └── Models.kt                  # All DTOs and request/response types
├── i18n/
│   └── I18nService.kt             # MessageFormat-based translations
├── web/
│   └── WebContext.kt              # Per-request theme + language helpers
└── filters/
    └── AuthenticatedUser.kt       # Reads userId from a validated request attribute

src/main/jte/                      # JTE templates (Kotlin flavour)
├── components/
│   ├── layout.kte                 # Full-page shell (sidebar nav, topbar, theme)
│   ├── bookCard.kte               # Book grid tile with cover/placeholder
│   ├── coverPlaceholder.kte       # Gradient placeholder when no cover image
│   ├── bookmarkItem.kte           # Single bookmark row
│   ├── libraryCard.kte            # Library grid tile
│   └── shelfCard.kte              # Magic shelf row
├── index.kte / dashboard.kte      # Home / dashboard page
├── library.kte                    # Single library with filter bar
├── libraries.kte                  # All libraries + smart shelves
├── book.kte                       # Book detail page
├── series-list.kte                # All series (browseable grid)
├── series.kte                     # Books in a single series
├── author-list.kte                # All authors (browseable grid)
├── author.kte                     # Books by a single author
├── search.kte                     # Full-text search results
├── analytics.kte                  # Reading analytics + session log
├── profile.kte                    # User profile, import/export, tokens
├── shelf.kte                      # Smart shelf detail
└── …                              # login/register/reader/admin pages

src/main/resources/
├── application.conf               # Typesafe Config (HOCON)
├── db/migration/
│   └── V1__initial_schema.sql     # Single consolidated Flyway migration
├── messages.properties            # English strings (MessageFormat)
├── messages_fr.properties         # French
├── messages_de.properties         # German
└── static/                        # CSS, JS served at /static/**
```

---

## Request Lifecycle

```
HTTP request
    │
    ▼
GlobalErrorFilter          — catch-all → 500 JSON
    │
CsrfFilter                 — Origin/Referer host check
    │
StaticCacheFilter          — adds Cache-Control for /static/**
    │
AppHandler.routes()        — http4k routing table
    │
    ├─ /static/**           → ResourceLoader.Classpath
    ├─ /covers/{filename}   → FileHandler
    ├─ /auth/**             → RateLimitFilter → AuthHandler2
    ├─ /api/**              → JwtAuthFilter → *Handler
    ├─ /admin               → JwtAuthFilter → AdminFilter → AdminHandler
    └─ /*, /books, …        → PageHandler   (HTML pages, may check cookie)
            │
            ▼
        Service layer       — business logic, no HTTP types
            │
            ▼
        JDBI handle         — parameterised SQL, manual mapping
            │
            ▼
        H2 / PostgreSQL
```

---

## Authentication

- **Cookie-based**: a `token=<JWT>` HttpOnly cookie is set on login/register
  and read by every authenticated request.
- **API token path**: OPDS and external integrations use a `Bearer` token
  from `api_tokens` (hashed with SHA-256 before storage).
- `JwtAuthFilter` validates the cookie, extracts `userId` and `isAdmin`
  claims, and stores them as request attributes. Handlers read them via
  `AuthenticatedUser.from(req)` (JSON API) or `jwtService.extractUserId()`
  (page handlers).
- **CSRF protection**: `CsrfFilter` rejects cross-origin POST/PUT/DELETE
  requests whose `Origin` or `Referer` host is not in the configured
  `csrf.allowed-hosts` list.

---

## Database

### Engine

**H2** in PostgreSQL-compatibility mode (`MODE=PostgreSQL`) is the default —
no external service required. Switch to **PostgreSQL** by setting the
`RUNARY_DB_*` environment variables (see `application.conf`).

### Migrations

A single Flyway migration (`V1__initial_schema.sql`) defines the complete
schema. New migrations are added as `V2__…`, `V3__…`, etc. when the schema
evolves. The Maven Flyway plugin runs migrations before the test phase.

### Access pattern

JDBI 3 is used for all SQL. There is no ORM:
- Every query is written as explicit SQL.
- Results are mapped manually with `RowView` lambdas.
- `jdbi.withHandle { … }` for reads, `jdbi.useHandle { … }` for writes.

### Key tables

| Table | Purpose |
|---|---|
| `users` | Accounts with password hash and admin flag |
| `sessions` | JWT refresh / API token hashes |
| `libraries` | Named collections with a filesystem path |
| `books` | Book metadata, file reference, series/index columns |
| `reading_progress` | Current page and percentage per user per book |
| `reading_sessions` | One row per progress update ≥ 1 page |
| `reading_daily` | Daily pages-read aggregate for analytics |
| `book_status` | Read status (WANT_TO_READ / READING / FINISHED) |
| `book_ratings` | 1–5 star rating per user per book |
| `book_tags` | Free-form tag strings per user per book |
| `bookmarks` | Page + note bookmarks |
| `book_annotations` | Highlighted text with colour |
| `magic_shelves` | Rule-based smart collections |
| `password_reset_tokens` | Self-hosted password reset flow |
| `api_tokens` | Programmatic access tokens |

---

## Configuration

`src/main/resources/application.conf` (HOCON) — values can be overridden by
environment variables:

| Env var | Default | Purpose |
|---|---|---|
| `RUNARY_HOST` | `0.0.0.0` | Bind address |
| `RUNARY_PORT` | `9999` | HTTP port |
| `RUNARY_DB_URL` | H2 file path | JDBC URL |
| `RUNARY_DB_USERNAME` | `sa` | DB username |
| `RUNARY_DB_PASSWORD` | `` | DB password |
| `RUNARY_DB_DRIVER` | `org.h2.Driver` | JDBC driver class |
| `RUNARY_JWT_SECRET` | dev default | JWT signing key — **change in production** |
| `RUNARY_BOOKS_PATH` | `./data/books` | Book file storage |
| `RUNARY_COVERS_PATH` | `./data/covers` | Cover image storage |
| `RUNARY_AUTO_SCAN_MINUTES` | `0` (disabled) | Library auto-scan interval |

---

## Key Architectural Decisions

### No framework magic
All wiring lives in `KoinModule.kt` and `Handlers.kt`. There are no
annotations for routing, injection, or transaction management. The dependency
graph is explicit and fully traceable.

### Handler/service split
HTTP concerns (request parsing, response building, auth checks) stay in
handlers. Business logic that touches the database lives in services.
Services receive only primitive or DTO types — never an `http4k Request`.

### HTMX for UI interactions
The HTML UI uses [HTMX](https://htmx.org) for partial updates (form
submissions, delete confirmations, toast notifications) without a frontend
build step. The server returns HTML fragments on HTMX requests and full pages
on direct navigation.

### Single-table per concern
Rather than one large `books` table with nullable foreign keys to many side
tables, metadata-like concerns (ratings, status, tags, annotations) live in
small satellite tables with `(user_id, book_id)` unique constraints. This
keeps the main query path fast and avoids wide, sparse rows.

### Deterministic cover placeholders
When no cover image is available, the UI renders a CSS gradient placeholder
(`coverPlaceholder.kte`) whose colour is derived from the first character of
the book title — consistent across all card views without server-side image
generation.

### Book sharing is instance-internal
Books can be shared via token-based links, but **only authenticated users** on
the same instance can view shared books. Anonymous visitors are redirected to
login. This prevents Runary from being used as a public file distribution
server. See [ADR-001](adr/001-book-sharing-authenticated-only.md).

---

## Architecture Decision Records (ADRs)

Design decisions are documented in `docs/adr/`:

| ADR | Decision |
|---|---|
| [001](adr/001-book-sharing-authenticated-only.md) | Book sharing requires authentication — no public links |

---

## Testing

Tests live under `src/test/kotlin/org/runary/` and use JUnit 5.

- **Integration tests** (`integration/`) spin up the full `AppHandler` +
  `GlobalErrorFilter` stack in-process against an H2 in-memory database.
  `IntegrationTestBase` wires all real services; no mocks. ~900 tests.
- **Handler tests** (`handlers/`) test specific handler behaviours
  (theme switching, language selection) with a real in-process stack.
- **Service tests** (`services/`) test service logic directly against the
  H2 test database.
- **Template rendering tests** (`handlers/TemplateRenderingTest`) compile all
  JTE templates and verify they render without errors.

The H2 Flyway migration runs once per Maven test phase via the
`flyway-maven-plugin`, creating a shared schema that all test classes reuse
through the static `TestFixture` singleton.
