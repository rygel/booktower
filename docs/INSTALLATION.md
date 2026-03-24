# Runary Installation Guide

This guide covers setting up Runary for production use, including PostgreSQL full-text search, OIDC single sign-on, email delivery, and reverse proxy configuration.

## Quick Start (5 minutes)

The fastest way to get Runary running:

```bash
docker run -d --name runary \
  -p 9999:8080 \
  -v runary_db:/data/db \
  -v runary_books:/data/books \
  -v runary_covers:/data/covers \
  -e RUNARY_ENV=production \
  -e RUNARY_JWT_SECRET="$(openssl rand -base64 32)" \
  ghcr.io/rygel/runary:latest
```

Open http://localhost:9999 and register your first account. The first registered user becomes admin.

> **Accessing from a domain or IP other than localhost?** You must set `RUNARY_CSRF_ALLOWED_HOSTS` to your domain/IP, otherwise you'll get a "Cross-site request blocked" error:
> ```bash
> -e RUNARY_CSRF_ALLOWED_HOSTS="yourdomain.com"
> -e RUNARY_BASE_URL="https://yourdomain.com"
> ```

## Docker Compose (Recommended)

### Simple Setup (H2 Embedded Database)

Best for personal use with a small library (< 10,000 books). Zero external dependencies.

```bash
curl -O https://raw.githubusercontent.com/rygel/runary/main/docs/examples/docker-compose.simple.yml
docker compose -f docker-compose.simple.yml up -d
```

See [`docs/examples/docker-compose.simple.yml`](examples/docker-compose.simple.yml).

### Production Setup (PostgreSQL + Full-Text Search)

Recommended for larger libraries, multi-user setups, or when you need full-text search.

```bash
curl -O https://raw.githubusercontent.com/rygel/runary/main/docs/examples/docker-compose.postgres.yml
# Edit the file — change passwords!
docker compose -f docker-compose.postgres.yml up -d
```

See [`docs/examples/docker-compose.postgres.yml`](examples/docker-compose.postgres.yml).

### Full Production Setup (PostgreSQL + OIDC + SMTP)

For organizations or advanced self-hosters who want SSO, email notifications, and all features enabled.

```bash
curl -O https://raw.githubusercontent.com/rygel/runary/main/docs/examples/docker-compose.full.yml
curl -O https://raw.githubusercontent.com/rygel/runary/main/docs/examples/.env.example
cp .env.example .env
# Edit .env with your passwords and domain
docker compose -f docker-compose.full.yml --env-file .env up -d
```

See [`docs/examples/docker-compose.full.yml`](examples/docker-compose.full.yml) and [`docs/examples/.env.example`](examples/.env.example).

## Fat JAR (No Docker)

Requires Java 21 or later.

```bash
# Download the latest release
curl -LO https://github.com/rygel/runary/releases/latest/download/runary.jar

# Run with H2 (zero config)
RUNARY_ENV=production \
RUNARY_JWT_SECRET="$(openssl rand -base64 32)" \
java -Xms128m -Xmx512m -jar runary.jar
```

### systemd Service

For Linux servers, install as a systemd service:

```bash
sudo useradd -r -s /bin/false runary
sudo mkdir -p /opt/runary /var/lib/runary/{books,covers,db}
sudo chown -R runary:runary /opt/runary /var/lib/runary
sudo cp runary.jar /opt/runary/runary.jar
sudo cp docs/examples/runary.service /etc/systemd/system/
# Edit /etc/systemd/system/runary.service — set JWT_SECRET and DB config
sudo systemctl daemon-reload
sudo systemctl enable --now runary
```

See [`docs/examples/runary.service`](examples/runary.service).

## Native Binary (Experimental)

Pre-built binaries for Linux, macOS, and Windows. No JVM required.

| Platform | Binary |
|----------|--------|
| Linux x64 | `runary-linux-x64` |
| Linux ARM64 | `runary-linux-arm64` |
| macOS Intel | `runary-macos-x64` |
| macOS Apple Silicon | `runary-macos-arm64` |
| Windows x64 | `runary-windows-x64.exe` |

```bash
chmod +x runary-linux-x64
RUNARY_ENV=production \
RUNARY_JWT_SECRET="$(openssl rand -base64 32)" \
./runary-linux-x64
```

## PostgreSQL Setup

### Why PostgreSQL?

- **Full-text search** — search across book titles, authors, descriptions, and extracted content in 30 languages
- **Better performance** — connection pooling, query planning, and caching for large libraries
- **Concurrent access** — proper row-level locking for multi-user setups

### Database Creation

```sql
CREATE USER runary WITH PASSWORD 'your-strong-password';
CREATE DATABASE runary OWNER runary;
```

### Environment Variables

```bash
RUNARY_DB_URL=jdbc:postgresql://localhost:5432/runary
RUNARY_DB_USERNAME=runary
RUNARY_DB_PASSWORD=your-strong-password
RUNARY_DB_DRIVER=org.postgresql.Driver
```

Runary runs Flyway migrations automatically on startup — no manual schema setup needed.

## Full-Text Search

Full-text search requires PostgreSQL and is enabled with:

```bash
RUNARY_FTS_ENABLED=true
```

### Features

- **30 language configurations** — Arabic, Danish, Dutch, English, Finnish, French, German, Greek, Hindi, Hungarian, Indonesian, Italian, Lithuanian, Norwegian, Portuguese, Romanian, Russian, Spanish, Swedish, Turkish, and more
- **Natural query syntax** — `"exact phrase"`, `-exclude`, `OR`, `AND`
- **Weighted ranking** — title matches rank higher than description matches
- **Content extraction** — searches inside EPUB and PDF text
- **CJK support** — Chinese, Japanese, Korean via trigram matching
- **BM25 scoring** — automatically enabled on PostgreSQL 17+ with `pg_textsearch`

### Search-as-you-type

The search bar debounces queries by 300ms (configurable via `RUNARY_FTS_THROTTLE_MS`).

## OIDC / Single Sign-On

Runary supports OpenID Connect for SSO with any OIDC provider (Keycloak, Authentik, Auth0, etc.).

### Environment Variables

```bash
OIDC_ISSUER=https://auth.example.com/realms/runary
OIDC_CLIENT_ID=runary
OIDC_CLIENT_SECRET=your-client-secret
OIDC_REDIRECT_URI=https://books.example.com/auth/oidc/callback
OIDC_SCOPE=openid email profile
```

### Optional Settings

```bash
# Disable local login (SSO only)
OIDC_FORCE_ONLY=true

# Auto-grant admin to users in a specific group
OIDC_ADMIN_GROUP_PATTERN=^runary-admin$

# Custom groups claim name (default: "groups")
OIDC_GROUPS_CLAIM=groups
```

### Keycloak Example

1. Create a new client in your Keycloak realm
2. Set **Client Protocol** to `openid-connect`
3. Set **Access Type** to `confidential`
4. Add `https://books.example.com/auth/oidc/callback` to **Valid Redirect URIs**
5. Copy the client secret from the **Credentials** tab
6. Create a group `runary-admin` and add admin users to it

## Email / SMTP

Email enables password reset and send-to-Kindle book delivery.

```bash
RUNARY_SMTP_HOST=smtp.gmail.com
RUNARY_SMTP_PORT=587
RUNARY_SMTP_USER=you@gmail.com
RUNARY_SMTP_PASS=app-specific-password
RUNARY_SMTP_FROM=you@gmail.com
RUNARY_SMTP_TLS=true
```

## Reverse Proxy

### Nginx

See [`docs/examples/nginx.conf`](examples/nginx.conf) for a complete configuration.

Key points:
- Set `client_max_body_size 500M` for large book uploads
- Forward `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto` headers
- Disable buffering for SSE notification streaming
- Set `RUNARY_CSRF_ALLOWED_HOSTS` to your domain

```bash
RUNARY_BASE_URL=https://books.example.com
RUNARY_CSRF_ALLOWED_HOSTS=books.example.com
```

### Caddy

Caddy is the easiest reverse proxy — it automatically provisions HTTPS via Let's Encrypt with zero configuration.

**Quick start with Caddy + Docker Compose:**

```bash
curl -O https://raw.githubusercontent.com/rygel/runary/main/docs/examples/docker-compose.caddy.yml
# Edit the file — change domain, JWT secret, and passwords
docker compose -f docker-compose.caddy.yml up -d
```

See [`docs/examples/docker-compose.caddy.yml`](examples/docker-compose.caddy.yml) and [`docs/examples/Caddyfile`](examples/Caddyfile).

**Or manually** — add to your existing Caddyfile:
```
books.example.com {
    reverse_proxy localhost:9999
}
```

Then set on the Runary container:
```bash
RUNARY_BASE_URL=https://books.example.com
RUNARY_CSRF_ALLOWED_HOSTS=books.example.com
```

Caddy automatically forwards `Host`, `X-Forwarded-For`, and `X-Forwarded-Proto` headers.

## Device Sync

### Kobo

1. Go to **Devices** in Runary and register a Kobo device
2. Copy the device token
3. On your Kobo, set the sync URL to: `https://books.example.com/kobo/{token}/v1`

### KOReader

1. Go to **Devices** and register a KOReader device
2. Copy the device token
3. In KOReader, set the kosync server to: `https://books.example.com/koreader/{token}`

### OPDS

Access your library from any OPDS-compatible reader:

- **Catalog URL**: `https://books.example.com/opds/catalog`
- **Authentication**: HTTP Basic (your Runary username/password) or Bearer API token

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

Stop Runary and copy the database file:

```bash
docker compose stop runary
docker cp runary:/data/db ./backup-db
docker compose start runary
```

### PostgreSQL

```bash
docker exec runary-postgres pg_dump -U runary runary > backup.sql
```

### Full Export (JSON)

Runary provides a JSON export of all user data:

```bash
curl -H "Cookie: token=YOUR_JWT" http://localhost:9999/api/export > runary-export.json
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
| [`docker-compose.caddy.yml`](examples/docker-compose.caddy.yml) | Caddy reverse proxy (auto HTTPS) |
| [`docker-compose.demo.yml`](examples/docker-compose.demo.yml) | Demo instance with sample data |
| [`.env.example`](examples/.env.example) | Environment variable template |
| [`nginx.conf`](examples/nginx.conf) | Nginx reverse proxy |
| [`Caddyfile`](examples/Caddyfile) | Caddy reverse proxy |
| [`runary.service`](examples/runary.service) | systemd service unit |
