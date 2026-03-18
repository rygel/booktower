# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
