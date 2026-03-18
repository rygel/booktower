# BookTower

A self-hosted personal digital library manager built with Kotlin, http4k, and HTMX.

## Features

- **Library management** — organize books into named libraries with folder-backed storage
- **Book reader** — in-browser PDF, EPUB, and comic (CBZ/CBR) reader with bookmarks, annotations, and reading progress
- **Audio book support** — listen to audio books and manage them in the smae application as your ebooks
- **Metadata** — automatic PDF metadata extraction and cover generation; manual metadata editing with online fetch
- **Reading analytics** — daily page tracking, streaks, finished-book counts, and reading goals
- **Bulk operations** — select multiple books to move, delete, tag, or change status at once
- **Smart shelves** — auto-populated virtual shelves driven by status, tag, or minimum rating rules
- **Multi-user** — user registration, admin panel, per-user settings and data isolation
- **Themes** — multiple color themes, persisted per user
- **Internationalization** — AI translations: English, French, and German; locale switching via sidebar; Weblate integration for translation management
- **API tokens** — Bearer token support for OPDS and programmatic access
- **Data export** — download all reading data as JSON
- **Auto-scan** — background folder scanning on a configurable interval
- **Password reset** — self-hosted token-based password reset flow

## Quick Start

```bash
# Run with defaults (H2 in-memory database, ./data for storage)
mvn exec:java -Dexec.mainClass="org.booktower.BookTowerAppKt"

# Or use the dev script
bash dev.sh
```

Open `http://localhost:8080` and register an account.

### Docker

```yaml
services:
  booktower:
    image: booktower:latest
    ports:
      - "8080:8080"
    environment:
      BOOKTOWER_DB_URL: jdbc:h2:file:/data/booktower;AUTO_SERVER=TRUE
      BOOKTOWER_JWT_SECRET: change-me-in-production
      BOOKTOWER_BOOKS_PATH: /data/books
      BOOKTOWER_COVERS_PATH: /data/covers
      BOOKTOWER_ENV: production
    volumes:
      - booktower_data:/data

volumes:
  booktower_data:
```

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9 |
| HTTP framework | http4k |
| Templates | JTE (`.kte`) |
| Frontend interactivity | HTMX |
| Database | H2 (file or in-memory) |
| Migrations | Flyway |
| SQL access | JDBI 3 |
| DI | Koin |
| Auth | JWT (cookie) + SHA-256 hashed API tokens |
| Build | Maven |
| Testing | JUnit 5 — 833 tests |

## Configuration

All settings can be overridden with environment variables. See [docs/CONFIGURATION.md](docs/CONFIGURATION.md) for the full reference.

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_HOST` | `0.0.0.0` | Bind address |
| `BOOKTOWER_PORT` | `8080` | HTTP port |
| `BOOKTOWER_JWT_SECRET` | *(dev default)* | **Change in production** |
| `BOOKTOWER_DB_URL` | H2 in-memory | JDBC URL |
| `BOOKTOWER_BOOKS_PATH` | `./data/books` | Book file storage |
| `BOOKTOWER_COVERS_PATH` | `./data/covers` | Cover image storage |
| `BOOKTOWER_AUTO_SCAN_MINUTES` | `60` | Folder scan interval (0 = off) |
| `BOOKTOWER_ENV` | — | Set to `production` to enforce JWT secret |

## Development

```bash
# Compile
mvn compile

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=BookServiceTest

# Start the server
mvn exec:java -Dexec.mainClass="org.booktower.BookTowerAppKt"
```

## Project Structure

```
booktower/
├── src/
│   ├── main/
│   │   ├── kotlin/org/booktower/
│   │   │   ├── config/         # AppConfig, Database, Koin module
│   │   │   ├── filters/        # Auth filter, CSRF filter
│   │   │   ├── handlers/       # HTTP request handlers
│   │   │   ├── model/          # Theme catalog, theme definitions
│   │   │   ├── models/         # DTOs and request/response models
│   │   │   ├── services/       # Business logic
│   │   │   └── web/            # WebContext (i18n, theme helpers)
│   │   ├── jte/                # JTE templates (.kte)
│   │   │   └── components/     # Reusable template fragments
│   │   └── resources/
│   │       ├── db/migration/   # Flyway SQL migrations (V1–V9)
│   │       ├── messages*.properties  # i18n strings
│   │       ├── static/         # CSS and JS assets
│   │       └── themes.json     # Theme definitions
│   └── test/
│       └── kotlin/org/booktower/
│           ├── handlers/       # Handler + template rendering tests
│           ├── integration/    # Full-stack integration tests
│           └── services/       # Service unit tests
├── docs/                       # Documentation
├── data/                       # Runtime data (books, covers, DB)
└── pom.xml
```

## Documentation

- [Configuration reference](docs/CONFIGURATION.md)
- [API reference](docs/API.md)
- [Deployment guide](docs/DEPLOYMENT.md)

## License

[GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE)
