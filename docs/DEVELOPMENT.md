# Development Guide

## Prerequisites

- JDK 21+
- Maven 3.9+
- (Optional) Calibre for MOBI/AZW3 conversion

## Quick Start

```powershell
# Foreground mode (see logs directly, Ctrl+C to stop)
.\dev.ps1

# Background mode (logs to data\booktower.log)
.\start-dev.ps1
.\start-dev.ps1 -OpenBrowser   # also opens browser
.\start-dev.ps1 -Clean         # mvn clean first
.\start-dev.ps1 -SkipBuild     # skip compilation
```

Both scripts auto-kill any previous BookTower instance on port 9999.

Default dev credentials: **dev / dev12345**

## Dev Mode vs Production Mode

| | Dev Mode | Production Mode |
|---|---------|----------------|
| **Activated by** | Default (no env var) | `BOOKTOWER_ENV=production` |
| **Database** | H2 file (`data/booktower`) | PostgreSQL via `BOOKTOWER_DB_*` |
| **Templates** | Dynamic — live-reload from `src/main/jte/` | Precompiled into JAR |
| **JWT secret** | Auto-generated (warning logged) | **Must** set `BOOKTOWER_JWT_SECRET` |
| **Dev user** | Auto-created (dev/dev12345) | Not created |
| **Registration** | Open by default | Set `BOOKTOWER_REGISTRATION_OPEN` |
| **HTML caching** | `no-cache` (always fresh) | `no-cache` (revalidates) |
| **Static assets** | 1-day cache | 1-day cache |

## Template Engine

BookTower uses [JTE](https://jte.gg/) templates in `src/main/jte/`.

**Dev mode**: Templates compile on-the-fly from source. Edit a `.kte` file, refresh the browser — changes appear immediately. Compiled classes go to `target/jte-dynamic/` (auto-cleaned on startup).

**Production/JAR mode**: Templates are precompiled by the Maven JTE plugin during `mvn package`. The fat JAR includes the compiled template classes. Source `.kte` files are not needed at runtime.

**If templates look stale**: Run `.\dev.ps1` (it clears `target/jte-dynamic/`) or do `mvn clean compile`.

## Project Structure

```
src/main/jte/              # JTE templates (.kte)
src/main/jte/components/   # Reusable components (layout, modal, breadcrumb, etc.)
src/main/kotlin/           # Kotlin source
  config/                  # AppConfig, Database, KoinModule, TemplateEngine
  filters/                 # HTTP filters (auth, CSRF, compression, caching)
  handlers/                # HTTP handlers (thin, delegate to services)
  routers/                 # Domain routers (auth, books, libraries, admin, etc.)
  services/                # Business logic + database access (JDBI)
  models/                  # Data classes and DTOs
src/main/resources/
  db/migration/            # Flyway SQL (single V1__initial_schema.sql)
  static/                  # CSS, JS, vendor libs, icons
  messages*.properties     # i18n strings (10 languages)
  application.conf         # Default config (Typesafe Config format)
```

## Running Tests

```bash
# Single test class (recommended)
mvn test -Dtest="AuthIntegrationTest"

# Multiple classes
mvn test -Dtest="AuthIntegrationTest,BookIntegrationTest"

# Compile check only (no tests)
mvn test-compile

# Full suite (slow — ~10 min, 1600+ tests)
mvn test
```

Never run the full suite without reason. See [CLAUDE.md](../CLAUDE.md) for test rules.

## Docker

### Build locally

```bash
docker build -t booktower:dev .
docker run -p 9999:8080 -e BOOKTOWER_QUICKSTART=true booktower:dev
```

### Dockerfile details

- **Build stage**: `maven:3.9-eclipse-temurin-21` — compiles and packages the fat JAR
- **Runtime stage**: `eclipse-temurin:21-jre-jammy` — minimal JRE, ~200MB
- **Internal port**: 8080 (set by `BOOKTOWER_PORT=8080` in Dockerfile)
- **External port**: Map to whatever you want (`-p 9999:8080`)
- **User**: Runs as non-root `booktower` user
- **Data**: Mount volumes at `/data/books`, `/data/covers`, `/data/db`

### Pre-built images

```bash
# Latest stable
docker pull ghcr.io/rygel/booktower:latest

# Latest from develop
docker pull ghcr.io/rygel/booktower:beta

# Specific version
docker pull ghcr.io/rygel/booktower:0.6.0
```

Multi-arch: `linux/amd64` + `linux/arm64` (Raspberry Pi, AWS Graviton).

### docker-compose

```bash
docker compose up -d          # H2 (default, zero config)
# Edit docker-compose.yml to enable PostgreSQL section, then:
docker compose up -d          # PostgreSQL
```

See [docker-compose.yml](../docker-compose.yml) for full configuration.

## Code Quality

```bash
mvn verify -DskipTests -Dflyway.skip=true -Dsonar.skip=true
```

Runs: SpotBugs, Spotless (ktlint), Detekt, Checkstyle, PMD, duplicate-finder, forbiddenapis.

To auto-fix formatting: `mvn spotless:apply`

## Useful Commands

```bash
# Update all dependencies
mvn versions:display-dependency-updates
mvn versions:display-plugin-updates

# Build fat JAR
mvn package -DskipTests -Dflyway.skip=true

# Run from fat JAR
BOOKTOWER_QUICKSTART=true java -jar target/booktower-*-fat.jar

# Run from Maven (dev mode, dynamic templates)
mvn exec:java -Dexec.mainClass=org.booktower.BookTowerAppKt
```
