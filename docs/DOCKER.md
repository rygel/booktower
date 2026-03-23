# Docker Deployment Guide

Runary publishes multi-architecture Docker images (`amd64` and `arm64`) to the GitHub Container Registry.

## Quick Start

The fastest way to run Runary:

```bash
docker run -d \
  --name runary \
  -p 9999:8080 \
  -v runary_db:/data/db \
  -v runary_books:/data/books \
  -v runary_covers:/data/covers \
  -e RUNARY_ENV=production \
  -e RUNARY_JWT_SECRET="$(openssl rand -base64 32)" \
  ghcr.io/rygel/runary:latest
```

Open `http://localhost:9999` and create your first account.

## Docker Compose

### Default (H2 — zero configuration)

The included `docker-compose.yml` uses an embedded H2 database, which requires no additional setup:

```bash
# 1. Clone the repository (or just grab docker-compose.yml)
# 2. Edit RUNARY_JWT_SECRET in docker-compose.yml
# 3. Start
docker compose up -d
```

Runary will be available at `http://localhost:9999`.

### With PostgreSQL (production)

For production workloads, PostgreSQL is recommended. Open `docker-compose.yml` and:

1. Uncomment the `postgres` service block.
2. Uncomment `runary_pgdata` in the `volumes` section.
3. Add the `RUNARY_DB_*` environment variables to the `runary` service:

```yaml
services:
  runary:
    environment:
      RUNARY_DB_URL: "jdbc:postgresql://postgres:5432/runary"
      RUNARY_DB_USERNAME: "runary"
      RUNARY_DB_PASSWORD: "change-me-to-a-strong-password"
      RUNARY_DB_DRIVER: "org.postgresql.Driver"
    depends_on:
      postgres:
        condition: service_healthy
```

4. Set matching credentials on the `postgres` service.
5. Start: `docker compose up -d`

## Volume Mounts

| Volume | Container Path | Purpose |
|--------|---------------|---------|
| `runary_db` | `/data/db` | H2 database files (not needed when using PostgreSQL) |
| `runary_books` | `/data/books` | Uploaded and managed book files |
| `runary_covers` | `/data/covers` | Extracted and uploaded cover images |
| `runary_pgdata` | `/var/lib/postgresql/data` | PostgreSQL data (only when using PostgreSQL) |

To mount an existing book collection for import:

```yaml
volumes:
  - /path/to/your/books:/mnt/library:ro
```

The `:ro` flag ensures Runary cannot modify your source files.

## Environment Variables

### Core

| Variable | Default | Description |
|----------|---------|-------------|
| `RUNARY_ENV` | — | Set to `production` to enforce JWT secret validation |
| `RUNARY_PORT` | `8080` | Internal HTTP port (rarely needs changing in Docker) |
| `RUNARY_HOST` | `0.0.0.0` | Bind address |
| `RUNARY_BASE_URL` | `http://{host}:{port}` | Public-facing URL (used in Kobo sync, OPDS links, email) |
| `RUNARY_JWT_SECRET` | — | **Required in production.** JWT signing key |
| `RUNARY_REGISTRATION_OPEN` | `true` | Set to `false` to disable new user registration |
| `RUNARY_AUTO_SCAN_MINUTES` | `60` | Library auto-scan interval; `0` to disable |

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `RUNARY_DB_URL` | H2 in-memory | JDBC connection string |
| `RUNARY_DB_USERNAME` | `sa` | Database username |
| `RUNARY_DB_PASSWORD` | (empty) | Database password |
| `RUNARY_DB_DRIVER` | `org.h2.Driver` | JDBC driver class |

### SMTP (email)

| Variable | Default | Description |
|----------|---------|-------------|
| `RUNARY_SMTP_HOST` | (empty) | SMTP server hostname |
| `RUNARY_SMTP_PORT` | `587` | SMTP port |
| `RUNARY_SMTP_USER` | (empty) | SMTP username |
| `RUNARY_SMTP_PASS` | (empty) | SMTP password |
| `RUNARY_SMTP_FROM` | (empty) | Sender address |
| `RUNARY_SMTP_TLS` | `true` | Enable STARTTLS |

### OIDC / SSO

| Variable | Default | Description |
|----------|---------|-------------|
| `OIDC_ISSUER` | (empty) | OIDC issuer URL (presence enables OIDC) |
| `OIDC_CLIENT_ID` | (empty) | OAuth2 client ID |
| `OIDC_CLIENT_SECRET` | (empty) | OAuth2 client secret |
| `OIDC_REDIRECT_URI` | (empty) | Callback URL |
| `OIDC_SCOPE` | `openid email profile` | OAuth2 scopes |
| `OIDC_FORCE_ONLY` | `false` | Disable local login when `true` |
| `OIDC_ADMIN_GROUP_PATTERN` | (empty) | Regex matched against OIDC groups to grant admin |
| `OIDC_GROUPS_CLAIM` | `groups` | Name of the groups claim in the userinfo response |

### Metadata Providers

| Variable | Default | Description |
|----------|---------|-------------|
| `RUNARY_HARDCOVER_API_KEY` | (empty) | Hardcover API key for book metadata |
| `RUNARY_COMICVINE_API_KEY` | (empty) | ComicVine API key for comic metadata |

### Full-Text Search

| Variable | Default | Description |
|----------|---------|-------------|
| `RUNARY_FTS_ENABLED` | `false` | Enable full-text search indexing |
| `RUNARY_FTS_THROTTLE_MS` | `300` | Debounce interval for search queries |

## Building from Source

```bash
git clone https://github.com/rygel/runary.git
cd runary
docker build -t runary:local .
```

Then use `runary:local` in place of `ghcr.io/rygel/runary:latest`, or set `build: .` in `docker-compose.yml`.

The Dockerfile uses a two-stage build:

1. **Build stage** — Maven + JDK 21 compiles the fat JAR (`mvn package -DskipTests`)
2. **Runtime stage** — Eclipse Temurin 21 JRE runs the JAR as a non-root `runary` user

## Multi-Architecture Support

The published images support both `linux/amd64` and `linux/arm64`. Docker will automatically pull the correct image for your platform.

To build multi-arch images locally:

```bash
docker buildx create --use
docker buildx build --platform linux/amd64,linux/arm64 -t runary:local .
```

## Updating to New Versions

```bash
# Pull the latest image
docker compose pull

# Recreate the container with the new image
docker compose up -d
```

Your data is preserved in the named volumes. To pin a specific version instead of `latest`:

```yaml
image: ghcr.io/rygel/runary:0.6.0
```

### Backup Before Upgrading

```bash
# Stop the container
docker compose down

# Back up volumes
docker run --rm \
  -v runary_db:/data \
  -v $(pwd)/backup:/backup \
  alpine tar czf /backup/runary-db.tar.gz -C /data .

# Pull and restart
docker compose pull
docker compose up -d
```

## Reverse Proxy

When running behind a reverse proxy (nginx, Caddy, Traefik), set `RUNARY_BASE_URL` to your public URL and configure `RUNARY_CSRF_ALLOWED_HOSTS` with your domain:

```yaml
environment:
  RUNARY_BASE_URL: "https://books.example.com"
  RUNARY_CSRF_ALLOWED_HOSTS: "books.example.com"
```

Ensure the proxy forwards the `Host`, `X-Forwarded-For`, and `X-Forwarded-Proto` headers.
