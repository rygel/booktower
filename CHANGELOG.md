# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.7.3] - 2026-03-24

### Added

- **First registered user auto-admin** — the first user to register becomes admin automatically, and registration closes after that
- Docker Compose + Caddy example with automatic HTTPS (`docker-compose.caddy.yml`)
- CSRF setup guide and Docker networking pitfall documentation
- PostgreSQL Docker Compose example in docs

### Fixed

- **Logout button not working** — changed from HTMX POST (silently failed CSRF) to regular form POST
- **Demo mode not blocking UI mutations** — DemoModeFilter now blocks `/ui/*` POST/PUT/DELETE in addition to `/api/*`
- **Service worker cache error** — removed non-existent `/static/css/style.css` from APP_SHELL cache list
- **EPUB reader `lastLocation` ReferenceError** — moved variable declaration before function that references it
- **Demo seed not downloading book files** — `seed()` now auto-triggers `seedFiles()` so books are readable, not just metadata
- **Flaky Windows search test** — `UserWorkflowE2ETest` now falls back to list endpoint when async EPUB title extraction races

## [0.7.2] - 2026-03-23

### Changed

- **PageHandler refactored** — split 1,279-line god class (33 params, 70 methods) into 5 focused domain handlers: PageHandler (slimmed), BrowsePageHandler, StatsPageHandler, SettingsPageHandler, DiscoveryPageHandler
- **UserApiRouter refactored** — extracted 33 inline methods (1,381 → 906 lines) into 6 dedicated API handlers: CollectionApiHandler, ReadingListApiHandler, WishlistApiHandler, WebhookApiHandler, NotificationApiHandler, CustomFieldApiHandler
- **BookService.getBooks() deduplicated** — consolidated 4 near-identical SQL queries into 2 using shared clause builder

### Added

- Caffeine caching for UserSettingsService (5min TTL) and LibraryService.getLibraries() (10min TTL) with proper invalidation on mutations
- API utility functions: `jsonResponse()`, `jsonError()`, `Request.lastPathUuid()`, `Request.secondToLastPathUuid()`
- TestPageHandlers factory — centralizes handler construction across all 4 test sites
- V13 migration: composite index on `book_moods(user_id, mood)`
- Comprehensive installation guide (`docs/INSTALLATION.md`)
- Demo instance setup guide (`docs/DEMO.md`)
- Example configs in `docs/examples/`: 4 Docker Compose variants, Nginx, Caddy, systemd, .env template

## [0.7.1] - 2026-03-23

### Fixed

- GraalVM native image startup crash — add `org.jdbi.v3.meta.Legacy` proxy registration to `proxy-config.json`
- XXE hardening in SeedService — add `isExpandEntityReferences = false`
- Dockerfile now includes `HEALTHCHECK` instruction
- Install `curl` in Docker runtime image for health checks

## [0.7.0] - 2026-03-23

### Added

- **ISBN barcode scanner** — scan a book barcode with your phone/webcam camera to auto-fill title, author, and metadata (uses html5-qrcode, EAN-13/EAN-8/UPC-A)
- `fetchMetadataByIsbn()` method in MetadataFetchService — ISBN-specific lookup via OpenLibrary and Google Books
- Full-stack UI pages for 15+ features that previously had API-only support:
  - Library statistics page
  - Webhooks management page
  - Reading timeline page
  - Annotation export page
  - Smart discovery / recommendations page
  - Database backup/restore admin page
  - Custom metadata fields page
  - Public profile settings page
  - Dashboard widgets (reading streaks, pages read)
  - Book condition tracker page
  - Reading lists page
  - Wishlist (want to read) page
  - Shared annotations in reader
  - Admin duplicate detection/merge page
  - Batch import + collections management page
  - Device management page (Kobo + KOReader)
  - Filter presets page
  - Scheduled tasks admin page
  - OPDS credentials settings page
  - Content restrictions settings page
  - Reading speed analytics page
  - Library health check page
  - Hardcover sync settings page
  - Book delivery (email) settings page
  - Book drop management page
  - Metadata proposals review page
- 80+ new E2E tests for all new UI pages
- i18n keys for all new pages across 10 languages

### Changed

- Beta release workflow changed from auto (every push to develop) to manual dispatch
- Test passwords now dynamically generated at runtime (no more hardcoded credentials)

### Fixed

- Path traversal defense in FileHandler — reject `..` sequences in stored file paths
- Path traversal defense in LibraryHandler2 — sanitize icon extensions and validate icon paths
- Input validation in BookHandler2 — validate libraryId as UUID before passing to service
- CSS class injection in showNotification — whitelist allowed notification types
- Content-Disposition header injection — sanitize filenames in download responses
- H2 compatibility fixes across 19 service files (Int/Boolean boxing, reserved words)
- PostgreSQL UNION type mismatches in ReadingTimelineService
- Merge conflict markers in messages.properties
- Fat JAR reduced from 90MB to 37MB (exclude kotlin-compiler-embeddable)

### Security

- Path traversal: all file-serving endpoints now reject paths containing `..`
- Icon upload: extensions sanitized with `[^a-z0-9]` regex
- Icon serve/delete: stored paths validated against traversal
- CSS injection: notification type parameter whitelisted
- Header injection: Content-Disposition filenames sanitized
- Hardcoded test passwords: 177 occurrences across 68 test files replaced with `TestPasswords.DEFAULT`

## [0.6.7] - 2026-03-22

### Added

- Table of contents sidebar for EPUB and PDF reader
- Batch import from directory with performance limits
- Custom metadata fields (text, number, date, select, boolean)
- Public reading activity profile (opt-in)
- Reading lists with ordered items and completion tracking
- Shared annotations — share highlights with other users
- Want-to-read wishlist for books not yet owned
- Reading streaks widget, book condition tracker, reading speed analytics
- Health check endpoint (`/health`)
- Advanced search UI with per-field filters
- Library statistics page with format/language breakdown

### Changed

- Fat JAR reduced from 90MB to 37MB
- Release workflow: publish succeeds even when native builds fail

### Fixed

- Security: path traversal, DOM XSS, SQL injection defense
- H2/PostgreSQL compatibility across all services
- 3 critical feature gaps fixed

## [0.5.2] - 2026-03-18

### Added

- Docker image publishing to ghcr.io (multi-arch amd64 + arm64)
- Beta release workflow from develop branch
- GitHub Actions updated to latest versions (checkout v6, setup-java v5, etc.)
- Spotless 3.3.0, SpotBugs plugin 4.9.8.2

### Changed

- All dependencies updated to latest GA releases

## [0.5.1] - 2026-03-18

### Added

- Gzip response compression
- JWT + user-exists caching with Caffeine
- E2E smoke tests on all PRs
- 4 reusable template components (modal, breadcrumb, emptyState, pagination)
- CSS utility classes
- i18n for notification panel (10 languages)

### Changed

- AppHandler refactored: 10 domain routers extracted (3,193 → 134 lines)
- 42 Flyway migrations consolidated into single V1__initial_schema.sql
- Dependabot configured to target develop branch

### Fixed

- N+1 query in LibraryService
- 9 missing database indexes
- Reader hardcoded colors replaced with CSS variables
- Docker build: go-offline made non-fatal
- LibraryWatchServiceTest flaky on macOS (increased sleep)
- ReadingSessionIntegrationTest flaky on Windows (timestamp resolution)

## [0.5.0] - 2026-03-17

### Added

- Initial release with full feature set
- Library management with named, folder-backed libraries
- In-browser readers for EPUB, PDF, Comic (CBZ/CBR), FB2, and DJVU
- Audiobook playback with listening sessions and bookmarks
- Metadata fetching from OpenLibrary, Google Books, Hardcover, ComicVine, and Audible
- Metadata proposals and metadata lock system
- Kobo device sync, KOReader sync, and OPDS catalog
- Book delivery via email (Send-to-Kindle support)
- Bookmarks, annotations, journals, notebooks, and reviews
- Reading goals, reading sessions, and reading statistics
- Listening statistics and heatmaps
- Smart shelves with auto-population rules
- Bulk operations (move, delete, tag, status change)
- Full-text search with FTS indexing
- Duplicate detection and library health checks
- Multi-user support with registration, admin panel, and per-user settings
- Per-user permissions and library access control
- JWT authentication with OIDC/SSO support
- CSRF protection, rate limiting, and API tokens
- Hardcover.app reading activity sync
- Goodreads library import
- Data export as JSON
- Audit log for administrative actions
- Scheduled background tasks with monitoring
- Password reset flow
- Multiple color themes per user
- Internationalization: English, French, German, Spanish, Portuguese, Italian, Dutch, Polish, Japanese, and Chinese
- Weblate integration for community translation management
- Demo mode and quickstart mode
- Auto-scan with configurable interval
- Alternative cover management and bulk cover fetching
- Calibre format conversion support
- Custom font management
- Reader preferences (font, theme, margins, RTL)
- Content restrictions support
- Recommendation engine
- Community ratings
- GeoIP request tracking
- Notification system
- Filter presets for saved searches
- Sidecar metadata file support
- PostgreSQL and H2 database support with Flyway migrations
- Native binaries for Linux (x64/arm64), macOS (x64/arm64), Windows (x64)
- Docker images (linux/amd64, linux/arm64)
- Fat JAR distribution
