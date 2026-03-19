# BookTower

A self-hosted personal book, audiobook, and comic manager with a built-in reader and rich metadata support.

[![Version](https://img.shields.io/badge/version-0.5.2-blue)](https://github.com/rygel/booktower/releases)
[![License](https://img.shields.io/badge/license-AGPL--3.0-green)](LICENSE)
[![Docker](https://img.shields.io/badge/docker-ghcr.io%2Frygel%2Fbooktower-blue)](https://ghcr.io/rygel/booktower)

<!-- ![BookTower Screenshot](docs/screenshot.png) -->

> [!WARNING]
> This is a new project and still under active development. There may be bugs. Test it with a small set of books before entrusting it with your entire library.

### Feature Maturity

| Area | Status | How tested |
|------|--------|------------|
| Core (libraries, books, auth, search) | **Stable** | 1600+ integration tests, CI on Linux/macOS/Windows |
| EPUB/PDF/Comic readers | **Stable** | Playwright browser tests + manual testing |
| Metadata fetching | **Stable** | Integration tests against live APIs |
| Bookmarks, journals, notebooks, reviews | **Stable** | Integration tests |
| Reading/listening stats & analytics | **Stable** | Integration tests |
| Smart shelves & filter presets | **Stable** | Integration tests |
| Whispersync (ebook↔audiobook) | **Experimental** | Integration tests only; chapter mapping is approximate |
| Docker image | **Untested** | Builds in CI but no E2E test runs against the container |
| PostgreSQL | **Untested** | SQL written for H2 PostgreSQL-mode; never connected to real PostgreSQL |
| Native binaries (GraalVM) | **Untested** | CI builds for 5 platforms but no smoke test verifies they start |
| Kobo / KOReader sync | **Untested** | Implements protocols; never tested with real hardware |
| OPDS catalog | **Untested** | Basic tests; not verified with real client apps |
| OIDC / SSO | **Untested** | Implements OIDC spec; tested only with mock providers |
| Hardcover.app sync | **Untested** | API integration exists; not tested with real accounts |
| Email delivery | **Untested** | SMTP works with GreenMail mock; not tested with real SMTP or Kindle |
| Full-text search | **Untested** | PostgreSQL only; requires `BOOKTOWER_FTS_ENABLED=true` |

## Features

### Library Management

- Organize books into named libraries with folder-backed storage
- Bulk operations: move, delete, tag, or change status for multiple books at once
- Smart shelves with auto-population driven by status, tag, or minimum rating rules
- Filter presets for saved search configurations
- Book drop: drag-and-drop file upload into libraries
- Auto-scan: background folder scanning on a configurable interval
- Library health checks and duplicate detection
- Calibre format conversion support
- Full-text search with FTS indexing

### Reading

- **EPUB** reader with customizable fonts, themes, margins, and RTL support
- **PDF** reader with in-browser viewing and text extraction
- **Comic** reader (CBZ/CBR) with page hash navigation
- **FB2** reader
- **DJVU** support
- **Audiobook** playback with listening sessions, bookmarks, and progress tracking
- Reader preferences per user (font family, font size, theme, layout)

### Metadata

- Fetch metadata from **OpenLibrary**, **Google Books**, **Hardcover**, **ComicVine**, and **Audible**
- Automatic metadata extraction from EPUB, PDF, comic, and audiobook files
- Metadata proposals: review and accept/reject fetched metadata before applying
- Metadata locks: prevent automatic overwrites of manually curated fields
- Author metadata enrichment
- Alternative cover management and bulk cover fetching
- Sidecar metadata file support
- Filename-based metadata parsing

### Device Sync *(experimental)*

- **Kobo** device synchronization *(not tested with real hardware)*
- **KOReader** sync protocol support *(not tested with real devices)*
- **OPDS** catalog feed for e-reader apps *(basic, not verified with all clients)*
- Book delivery via email (Send-to-Kindle and similar)

### User Features

- Bookmarks and annotations
- Reading journals
- Notebooks per book
- Reviews and community ratings
- Reading goals with progress tracking
- Reading sessions with daily page tracking and streaks
- Listening sessions with listening stats and heatmaps
- Recommendations engine
- Smart shelves (auto-populated virtual collections)
- Content restrictions support

### Analytics

- Reading statistics: pages read, books finished, daily streaks
- Listening statistics for audiobooks
- Reading heatmaps
- Per-library and per-user analytics

### Admin

- Multi-user support with registration and user management
- Per-user permissions and library access control
- Scheduled background tasks with monitoring
- Audit log for administrative actions
- Demo mode for showcasing without modifications
- Notification system

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
java -jar booktower-0.5.2.jar
```

### Native Binary

Download the native binary for your platform from [Releases](https://github.com/rygel/booktower/releases):

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
