# BookTower

A self-hosted personal book, audiobook, and comic manager with a built-in reader and rich metadata support.

[![Version](https://img.shields.io/badge/version-0.7.2-blue)](https://github.com/rygel/booktower/releases)
[![License](https://img.shields.io/badge/license-AGPL--3.0-green)](LICENSE)
[![Docker](https://img.shields.io/badge/docker-ghcr.io%2Frygel%2Fbooktower-blue)](https://ghcr.io/rygel/booktower)

<!-- ![BookTower Screenshot](docs/screenshot.png) -->

> [!WARNING]
> This is a new project and still under active development. There may be bugs. Test it with a small set of books before entrusting it with your entire library.

### Feature Maturity

| Area | Status | How tested |
|------|--------|------------|
| Core (libraries, books, auth, search) | **Stable** | 1900+ integration tests, CI on Linux/macOS/Windows |
| EPUB reader (with TOC sidebar) | **Stable** | Playwright browser tests + manual testing |
| PDF reader (with TOC sidebar) | **Stable** | Integration tests |
| Comic reader (CBZ/CBR) | **Stable** | Double page, continuous scroll, fit modes, swipe, page gaps |
| Audiobook player | **Stable** | Multi-chapter playback, speed control |
| Bookmarks, journals, notebooks, reviews | **Stable** | Integration tests |
| Reading/listening stats & analytics | **Stable** | Streaks, speed, heatmaps, goals |
| Smart shelves & filter presets | **Stable** | Integration tests |
| Full-text search (PostgreSQL) | **Stable** | 14 E2E tests against real PostgreSQL, 30 language configs |
| PostgreSQL 17/18 | **Stable** | Full test suite in CI |
| Docker image | **Stable** | CI builds + 37MB fat JAR |
| Kobo / KOReader sync | **Tested** | 17 integration tests, not verified with real hardware |
| OPDS 1.2 + 2.0 | **Tested** | Integration tests; OPDS 2.0 JSON feeds |
| OIDC / SSO | **Tested** | 8 E2E tests against real Keycloak 26.2 |
| Book sharing | **Works** | Integration tests |
| Collections | **Works** | API + UI |
| Metadata fetching | **Stable** | Bulk refresh, 5 providers |
| Demo data seeding | **Stable** | Resumable downloads, retry on failure |
| ISBN barcode scanner | **Works** | Camera-based scanning with html5-qrcode |
| Webhook notifications | **Works** | API + full management UI |
| Custom metadata fields | **Works** | User-defined fields per book |
| Public reading profile | **Works** | Opt-in, metadata only |
| Database backup/restore | **Works** | Full JSON export/import |
| Native binaries (GraalVM 25) | **Experimental** | CI builds; may fail |
| Email delivery | **Untested** | SMTP works with GreenMail mock; not tested with real Kindle |
| Hardcover.app sync | **Untested** | API exists; not tested with real accounts |

## Features

### Library Management

- Organize books into named libraries with folder-backed storage
- **Batch import from directory** with configurable performance limits (throttle, max concurrency)
- Bulk operations: move, delete, tag, or change status for multiple books at once
- Smart shelves with auto-population driven by status, tag, or minimum rating rules
- Filter presets for saved search configurations
- **Custom metadata fields** — user-defined fields (text, number, date, select, boolean) per book
- **ISBN barcode scanner** — scan a book's barcode with your camera to auto-fill metadata
- Book drop: drag-and-drop file upload into libraries
- Auto-scan: background folder scanning on a configurable interval
- Library health checks and **duplicate detection with merge**
- **Library statistics** — format/language breakdown, top authors/tags, storage usage
- Calibre format conversion support
- Full-text search with FTS indexing (30 languages, BM25 optional)

### Reading

- **EPUB** reader with customizable fonts, themes, margins, RTL support, and **table of contents sidebar**
- **PDF** reader with in-browser viewing, text extraction, and **table of contents from bookmarks**
- **Comic** reader (CBZ/CBR) with double page spread, continuous scroll, 5 fit modes, swipe gestures, configurable page gaps
- **FB2** reader
- **DJVU** support
- **Audiobook** playback with listening sessions, bookmarks, and progress tracking
- **Cross-device position sync** — resume reading on any device (EPUB CFI, PDF page, audio timestamp)
- Reader preferences per user (font family, font size, theme, layout)

### Metadata

- Fetch metadata from **OpenLibrary**, **Google Books**, **Hardcover**, **ComicVine**, and **Audible**
- **Bulk metadata refresh** — auto-fetch missing covers/ISBNs for entire library
- Automatic metadata extraction from EPUB, PDF, comic, and audiobook files
- Metadata proposals: review and accept/reject fetched metadata before applying
- Metadata locks: prevent automatic overwrites of manually curated fields
- Author metadata enrichment
- Alternative cover management and bulk cover fetching
- Sidecar metadata file support
- Filename-based metadata parsing

### Device Sync

- **Kobo** device synchronization (17 integration tests)
- **KOReader** sync protocol support (kosync)
- **OPDS 1.2** (Atom XML) and **OPDS 2.0** (JSON) catalog feeds
- Book delivery via email (Send-to-Kindle and similar)

### User Features

- **Whispersync**: link an ebook and audiobook, seamlessly switch between reading and listening at the matching position
- Bookmarks and annotations with **export** (Markdown, JSON, Readwise CSV)
- Reading journals and notebooks per book
- Reviews and community ratings
- **Reading goals** with progress ring, monthly pacing, projected finish
- **Reading timeline** — chronological feed of reading activity
- Reading sessions with daily page tracking and streaks
- **Reading speed analytics** — pages/hour, estimated time to finish
- Listening sessions with listening stats and heatmaps
- **Smart discovery recommendations** — unfinished series, favorite authors, top tags
- **Public reading profile** — opt-in activity feed (metadata only)
- **Book condition tracker** — for physical collections (condition, purchase info, shelf location)
- **Webhook notifications** — Discord/Slack/generic HTTP POST for events
- Smart shelves (auto-populated virtual collections)
- Content restrictions support
- Saved filter presets

### Analytics

- **Library statistics** — total books, format/language breakdown, top authors, storage usage
- Reading statistics: pages read, books finished, daily streaks
- **Reading streaks widget** — current/longest streak for dashboard
- Listening statistics for audiobooks
- Reading heatmaps
- Per-library and per-user analytics

### Admin

- Multi-user support with registration and user management
- Per-user permissions and library access control
- **Database backup/restore** — full JSON export/import
- **Batch import from directory** with performance throttling
- Scheduled background tasks with monitoring
- Audit log for administrative actions
- Demo mode for showcasing without modifications
- Notification system
- **Health check endpoint** (`/health`) for Docker/Kubernetes

### Integrations

- **Hardcover.app** reading activity sync
- **Goodreads** library import
- **Weblate** integration for community translation management
- Email delivery service (configurable SMTP provider)
- OPDS credentials management

### Security

- JWT authentication with configurable session timeout
- OIDC / SSO support for single sign-on
- CSRF protection
- Rate limiting
- API tokens for programmatic and OPDS access
- Password reset flow
- Request logging and GeoIP tracking

### Self-Hosting

- **Docker** images (linux/amd64, linux/arm64)
- Native binaries for Linux (x64/arm64), macOS (x64/arm64), and Windows (x64)
- Fat JAR for any JVM platform
- PostgreSQL for production, H2 for zero-config development
- Demo mode and quickstart mode for easy evaluation
- Gzip response compression
- Caffeine caching for JWT and user lookups

### Internationalization

10 languages supported: English, French, German, Spanish, Portuguese, Italian, Dutch, Polish, Japanese, and Chinese.

> [!NOTE]
> This project is developed with the help of AI. However, a human is guiding it and making all critical design decisions. On top of that,
  an extensive end-to-end test suite is co-developed and maintained to keep the quality high and prevent regressions.

## Quick Start

### Docker (recommended)

```yaml
services:
  booktower:
    image: ghcr.io/rygel/booktower:latest
    ports:
      - "9999:9999"
    environment:
      BOOKTOWER_JWT_SECRET: change-me-in-production
      BOOKTOWER_ENV: production
    volumes:
      - booktower_data:/data

volumes:
  booktower_data:
```

```bash
docker compose up -d
```

Open `http://localhost:9999` and register your first account.

### Fat JAR

Download the latest release JAR from [Releases](https://github.com/rygel/booktower/releases), then:

```bash
java -jar booktower-v0.7.2.jar
```

### Native Binary

Native binaries are built with GraalVM 25 Community Edition — no JVM installation required. Download from [Releases](https://github.com/rygel/booktower/releases):

| Platform       | Binary                          |
|----------------|---------------------------------|
| Linux x64      | `booktower-linux-x64`           |
| Linux arm64    | `booktower-linux-arm64`         |
| macOS x64      | `booktower-macos-x64`           |
| macOS arm64    | `booktower-macos-arm64`         |
| Windows x64    | `booktower-windows-x64.exe`     |

```bash
chmod +x booktower-linux-x64
./booktower-linux-x64
```

## Support Matrix

### Deployment Options

| Method | JDK Required | Platforms | Notes |
|--------|-------------|-----------|-------|
| **Docker** | No | linux/amd64, linux/arm64 | Recommended for production |
| **Native Binary** | No | Linux x64/arm64, macOS x64/arm64, Windows x64 | Built with GraalVM 25 CE |
| **Fat JAR** | Java 21+ | Any OS with JDK | Temurin, Corretto, GraalVM all work |

### Operating Systems

| OS | Fat JAR | Native Binary | Docker | CI Tested |
|----|---------|--------------|--------|-----------|
| Ubuntu / Debian | Yes | Yes (x64, arm64) | Yes | Yes |
| macOS (Intel) | Yes | Yes | Via Docker Desktop | Yes |
| macOS (Apple Silicon) | Yes | Yes | Via Docker Desktop | Yes |
| Windows 10/11 | Yes | Yes (x64) | Via WSL2/Docker Desktop | Yes |

### Databases

| Database | Versions | Notes |
|----------|----------|-------|
| **H2** (embedded) | 2.4.x | Default for dev/quickstart — zero config |
| **PostgreSQL** | 17, 18 | Recommended for production. Set `BOOKTOWER_DB_*` env vars |

### Java Versions (Fat JAR only)

| Version | Supported |
|---------|-----------|
| Java 21 (LTS) | Yes — minimum required |
| Java 22-24 | Yes |
| Java 25 | Yes — tested in CI |

## Configuration

All settings can be overridden with environment variables. See [docs/CONFIGURATION.md](docs/CONFIGURATION.md) for the full reference.

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_HOST` | `0.0.0.0` | Bind address |
| `BOOKTOWER_PORT` | `9999` | HTTP port |
| `BOOKTOWER_ENV` | — | Set to `production` to enforce JWT secret |
| `BOOKTOWER_JWT_SECRET` | *(dev default)* | **Change in production** |
| `BOOKTOWER_DB_URL` | H2 file-backed | JDBC URL (H2 or PostgreSQL) |
| `BOOKTOWER_DB_USERNAME` | `sa` | Database username |
| `BOOKTOWER_DB_PASSWORD` | — | Database password |
| `BOOKTOWER_DB_DRIVER` | `org.h2.Driver` | JDBC driver class |
| `BOOKTOWER_BOOKS_PATH` | `./data/books` | Book file storage |
| `BOOKTOWER_COVERS_PATH` | `./data/covers` | Cover image storage |

### PostgreSQL example

```bash
export BOOKTOWER_DB_URL=jdbc:postgresql://localhost:5432/booktower
export BOOKTOWER_DB_USERNAME=booktower
export BOOKTOWER_DB_PASSWORD=secret
export BOOKTOWER_DB_DRIVER=org.postgresql.Driver
```

## Full-Text Search

BookTower supports full-text search on PostgreSQL with two backends. H2 (dev mode) uses basic LIKE search.

### Enabling FTS

```bash
export BOOKTOWER_FTS_ENABLED=true
```

Requires PostgreSQL. On startup, BookTower creates the necessary tsvector columns, GIN indexes, and triggers automatically.

### Built-in PostgreSQL FTS (default)

When FTS is enabled, BookTower uses PostgreSQL's native full-text search with:

- **`websearch_to_tsquery`** — natural query syntax:
  - `war worlds` — find books matching both words
  - `"war of the worlds"` — exact phrase match
  - `wells -invisible` — exclude results containing "invisible"
  - `mars OR venus` — match either word
- **Weighted metadata search** — title matches (A) rank higher than author (B), series (B), and description (C)
- **Content search** — extracted text from EPUBs and PDFs is indexed and searchable
- **Combined ranking** — metadata matches are boosted above content-only matches

### pg_textsearch BM25 (optional)

If you install the [pg_textsearch](https://github.com/timescale/pg_textsearch) extension (PostgreSQL 17+), BookTower automatically detects it and uses BM25 ranking for content search. BM25 provides statistically better relevance scoring than the built-in `ts_rank`.

```sql
-- Install pg_textsearch (requires shared_preload_libraries config)
CREATE EXTENSION pg_textsearch;
```

BookTower logs which backend is active at startup:
```
FTS: metadata=true, bm25=false   -- built-in only
FTS: metadata=true, bm25=true    -- pg_textsearch detected
```

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| HTTP framework | http4k |
| Templates | JTE (`.kte`) |
| Frontend interactivity | HTMX |
| Database | H2 (dev) / PostgreSQL (prod) |
| Migrations | Flyway |
| SQL access | JDBI 3 |
| Dependency injection | Koin |
| Caching | Caffeine |
| Build | Maven |

## Documentation

- [Configuration reference](docs/CONFIGURATION.md)
- [API reference](docs/API.md)
- [Deployment guide](docs/DEPLOYMENT.md)
- [Architecture overview](docs/BACKEND_ARCHITECTURE.md)
- [Quick start guide](docs/QUICKSTART.md)

## Contributing

Contributions are welcome! Please open an issue to discuss proposed changes before submitting a pull request.

Translation contributions are managed through Weblate integration. See the project documentation for details on adding or improving translations.

## License

[GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE)
