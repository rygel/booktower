# BookTower Installation Guide

This guide covers setting up BookTower for production use, including PostgreSQL full-text search, OIDC single sign-on, email delivery, and reverse proxy configuration.

## Quick Start (5 minutes)

The fastest way to get BookTower running:

```bash
docker run -d --name booktower \
  -p 9999:8080 \
  -v booktower_db:/data/db \
  -v booktower_books:/data/books \
  -v booktower_covers:/data/covers \
  -e BOOKTOWER_ENV=production \
  -e BOOKTOWER_JWT_SECRET="$(openssl rand -base64 32)" \
  ghcr.io/rygel/booktower:latest
```

Open http://localhost:9999 and register your first account. The first registered user becomes admin.

## Docker Compose (Recommended)

### Simple Setup (H2 Embedded Database)

Best for personal use with a small library (< 10,000 books). Zero external dependencies.

```bash
curl -O https://raw.githubusercontent.com/rygel/booktower/main/docs/examples/docker-compose.simple.yml
docker compose -f docker-compose.simple.yml up -d
```

See [`docs/examples/docker-compose.simple.yml`](examples/docker-compose.simple.yml).

### Production Setup (PostgreSQL + Full-Text Search)

Recommended for larger libraries, multi-user setups, or when you need full-text search.

```bash
curl -O https://raw.githubusercontent.com/rygel/booktower/main/docs/examples/docker-compose.postgres.yml
# Edit the file — change passwords!
docker compose -f docker-compose.postgres.yml up -d
```

See [`docs/examples/docker-compose.postgres.yml`](examples/docker-compose.postgres.yml).

### Full Production Setup (PostgreSQL + OIDC + SMTP)

For organizations or advanced self-hosters who want SSO, email notifications, and all features enabled.

```bash
curl -O https://raw.githubusercontent.com/rygel/booktower/main/docs/examples/docker-compose.full.yml
curl -O https://raw.githubusercontent.com/rygel/booktower/main/docs/examples/.env.example
cp .env.example .env
# Edit .env with your passwords and domain
docker compose -f docker-compose.full.yml --env-file .env up -d
```

See [`docs/examples/docker-compose.full.yml`](examples/docker-compose.full.yml) and [`docs/examples/.env.example`](examples/.env.example).

## Fat JAR (No Docker)

Requires Java 21 or later.

```bash
# Download the latest release
curl -LO https://github.com/rygel/booktower/releases/latest/download/booktower.jar

# Run with H2 (zero config)
BOOKTOWER_ENV=production \
BOOKTOWER_JWT_SECRET="$(openssl rand -base64 32)" \
java -Xms128m -Xmx512m -jar booktower.jar
```

### systemd Service

For Linux servers, install as a systemd service:

```bash
sudo useradd -r -s /bin/false booktower
sudo mkdir -p /opt/booktower /var/lib/booktower/{books,covers,db}
sudo chown -R booktower:booktower /opt/booktower /var/lib/booktower
sudo cp booktower.jar /opt/booktower/booktower.jar
sudo cp docs/examples/booktower.service /etc/systemd/system/
# Edit /etc/systemd/system/booktower.service — set JWT_SECRET and DB config
sudo systemctl daemon-reload
sudo systemctl enable --now booktower
```

See [`docs/examples/booktower.service`](examples/booktower.service).

## Native Binary (Experimental)

Pre-built binaries for Linux, macOS, and Windows. No JVM required.

| Platform | Binary |
|----------|--------|
| Linux x64 | `booktower-linux-x64` |
| Linux ARM64 | `booktower-linux-arm64` |
| macOS Intel | `booktower-macos-x64` |
| macOS Apple Silicon | `booktower-macos-arm64` |
| Windows x64 | `booktower-windows-x64.exe` |

```bash
chmod +x booktower-linux-x64
BOOKTOWER_ENV=production \
BOOKTOWER_JWT_SECRET="$(openssl rand -base64 32)" \
./booktower-linux-x64
```

## PostgreSQL Setup

### Why PostgreSQL?

- **Full-text search** — search across book titles, authors, descriptions, and extracted content in 30 languages
- **Better performance** — connection pooling, query planning, and caching for large libraries
- **Concurrent access** — proper row-level locking for multi-user setups

### Database Creation

```sql
CREATE USER booktower WITH PASSWORD 'your-strong-password';
CREATE DATABASE booktower OWNER booktower;
```

### Environment Variables

```bash
BOOKTOWER_DB_URL=jdbc:postgresql://localhost:5432/booktower
BOOKTOWER_DB_USERNAME=booktower
BOOKTOWER_DB_PASSWORD=your-strong-password
BOOKTOWER_DB_DRIVER=org.postgresql.Driver
```

BookTower runs Flyway migrations automatically on startup — no manual schema setup needed.

## Full-Text Search

Full-text search requires PostgreSQL and is enabled with:

```bash
BOOKTOWER_FTS_ENABLED=true
```

### Features

- **30 language configurations** — Arabic, Danish, Dutch, English, Finnish, French, German, Greek, Hindi, Hungarian, Indonesian, Italian, Lithuanian, Norwegian, Portuguese, Romanian, Russian, Spanish, Swedish, Turkish, and more
- **Natural query syntax** — `"exact phrase"`, `-exclude`, `OR`, `AND`
- **Weighted ranking** — title matches rank higher than description matches
- **Content extraction** — searches inside EPUB and PDF text
- **CJK support** — Chinese, Japanese, Korean via trigram matching
- **BM25 scoring** — automatically enabled on PostgreSQL 17+ with `pg_textsearch`

### Search-as-you-type

The search bar debounces queries by 300ms (configurable via `BOOKTOWER_FTS_THROTTLE_MS`).

## OIDC / Single Sign-On

BookTower supports OpenID Connect for SSO with any OIDC provider (Keycloak, Authentik, Auth0, etc.).

### Environment Variables

```bash
OIDC_ISSUER=https://auth.example.com/realms/booktower
OIDC_CLIENT_ID=booktower
OIDC_CLIENT_SECRET=your-client-secret
OIDC_REDIRECT_URI=https://books.example.com/auth/oidc/callback
OIDC_SCOPE=openid email profile
```

### Optional Settings

```bash
# Disable local login (SSO only)
OIDC_FORCE_ONLY=true

# Auto-grant admin to users in a specific group
OIDC_ADMIN_GROUP_PATTERN=^booktower-admin$

# Custom groups claim name (default: "groups")
OIDC_GROUPS_CLAIM=groups
```

### Keycloak Example

1. Create a new client in your Keycloak realm
2. Set **Client Protocol** to `openid-connect`
3. Set **Access Type** to `confidential`
4. Add `https://books.example.com/auth/oidc/callback` to **Valid Redirect URIs**
5. Copy the client secret from the **Credentials** tab
6. Create a group `booktower-admin` and add admin users to it

## Email / SMTP

Email enables password reset and send-to-Kindle book delivery.

```bash
BOOKTOWER_SMTP_HOST=smtp.gmail.com
BOOKTOWER_SMTP_PORT=587
BOOKTOWER_SMTP_USER=you@gmail.com
BOOKTOWER_SMTP_PASS=app-specific-password
BOOKTOWER_SMTP_FROM=you@gmail.com
BOOKTOWER_SMTP_TLS=true
```

## Reverse Proxy

### Nginx

See [`docs/examples/nginx.conf`](examples/nginx.conf) for a complete configuration.

Key points:
- Set `client_max_body_size 500M` for large book uploads
- Forward `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto` headers
- Disable buffering for SSE notification streaming
- Set `BOOKTOWER_CSRF_ALLOWED_HOSTS` to your domain

```bash
BOOKTOWER_BASE_URL=https://books.example.com
BOOKTOWER_CSRF_ALLOWED_HOSTS=books.example.com
```

### Caddy

See [`docs/examples/Caddyfile`](examples/Caddyfile). Caddy auto-provisions HTTPS and forwards all required headers.

## Device Sync

### Kobo

1. Go to **Devices** in BookTower and register a Kobo device
2. Copy the device token
3. On your Kobo, set the sync URL to: `https://books.example.com/kobo/{token}/v1`

### KOReader

1. Go to **Devices** and register a KOReader device
2. Copy the device token
3. In KOReader, set the kosync server to: `https://books.example.com/koreader/{token}`

### OPDS

Access your library from any OPDS-compatible reader:

- **Catalog URL**: `https://books.example.com/opds/catalog`
- **Authentication**: HTTP Basic (your BookTower username/password) or Bearer API token

Compatible readers: KOReader, Librera, Moon+ Reader, Kybook, Aldiko, Thorium, Calibre.

## Monitoring

### Health Check

```bash
curl http://localhost:9999/health
```

Returns:
```json
{
  "status": "healthy",
  "version": "0.7.1",
  "database": "ok",
  "diskFreeBytes": 107374182400,
  "diskUsedPercent": 45,
  "uptime": "2h 30m",
  "jvmMemoryUsedMb": 256,
  "jvmMemoryMaxMb": 512
}
```

### Docker Health Check

The Docker image includes a built-in `HEALTHCHECK` that polls `/health` every 30 seconds.

## Backup

### H2 Database

Stop BookTower and copy the database file:

```bash
docker compose stop booktower
docker cp booktower:/data/db ./backup-db
docker compose start booktower
```

### PostgreSQL

```bash
docker exec booktower-postgres pg_dump -U booktower booktower > backup.sql
```

### Full Export (JSON)

BookTower provides a JSON export of all user data:

```bash
curl -H "Cookie: token=YOUR_JWT" http://localhost:9999/api/export > booktower-export.json
```

## Environment Variable Reference

See [`docs/CONFIGURATION.md`](CONFIGURATION.md) for a complete reference of all environment variables.

## Example Files

All example configurations are in [`docs/examples/`](examples/):

| File | Description |
|------|-------------|
| [`docker-compose.simple.yml`](examples/docker-compose.simple.yml) | Simple setup with H2 |
| [`docker-compose.postgres.yml`](examples/docker-compose.postgres.yml) | PostgreSQL + FTS |
| [`docker-compose.full.yml`](examples/docker-compose.full.yml) | Full production (PG + OIDC + SMTP) |
| [`docker-compose.demo.yml`](examples/docker-compose.demo.yml) | Demo instance with sample data |
| [`.env.example`](examples/.env.example) | Environment variable template |
| [`nginx.conf`](examples/nginx.conf) | Nginx reverse proxy |
| [`Caddyfile`](examples/Caddyfile) | Caddy reverse proxy |
| [`booktower.service`](examples/booktower.service) | systemd service unit |
