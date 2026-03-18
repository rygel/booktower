# Configuration Reference

All BookTower settings have sensible defaults for local development. Override any of them with environment variables for production deployments.

## Server

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_HOST` | `0.0.0.0` | Network interface to bind. Use `127.0.0.1` to restrict to localhost. |
| `BOOKTOWER_PORT` | `8080` | TCP port the HTTP server listens on. |
| `BOOKTOWER_ENV` | — | Set to `production` to enforce a non-default JWT secret and enable production-mode guards. |
| `BOOKTOWER_BASE_URL` | `http://{host}:{port}` | Public-facing base URL. Used for OIDC redirect URIs, email links, and OPDS feeds. Derived from host/port if not set. |

## Database

BookTower uses JDBI 3 with H2 by default. Any JDBC-compatible database can be used by changing the URL and driver. SQL is written to be compatible with both H2 (PostgreSQL mode) and PostgreSQL.

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_DB_URL` | `jdbc:h2:mem:booktower;DB_CLOSE_DELAY=-1` | JDBC connection URL. For persistence use `jdbc:h2:file:/data/booktower`. |
| `BOOKTOWER_DB_USERNAME` | `sa` | Database username. |
| `BOOKTOWER_DB_PASSWORD` | *(empty)* | Database password. |
| `BOOKTOWER_DB_DRIVER` | `org.h2.Driver` | Fully-qualified JDBC driver class name. Use `org.postgresql.Driver` for PostgreSQL. |

### Persistent H2 (recommended for simple deployments)

```
BOOKTOWER_DB_URL=jdbc:h2:file:/data/booktower;AUTO_SERVER=TRUE
```

`AUTO_SERVER=TRUE` allows multiple JVM processes to connect to the same file, which is useful if you run a maintenance script alongside the server.

### PostgreSQL (recommended for production)

```
BOOKTOWER_DB_URL=jdbc:postgresql://localhost:5432/booktower
BOOKTOWER_DB_USERNAME=booktower
BOOKTOWER_DB_PASSWORD=secret
BOOKTOWER_DB_DRIVER=org.postgresql.Driver
```

## Security

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_JWT_SECRET` | `booktower-secret-key-change-in-production` | HMAC secret used to sign JWT session cookies. **Must be changed for production** — the app will refuse to start with the default secret when `BOOKTOWER_ENV=production`. |
| `BOOKTOWER_CSRF_ALLOWED_HOSTS` | `localhost,localhost:8080` | Comma-separated list of `Host` header values permitted for state-changing requests. Add your domain name when deploying behind a reverse proxy. |

## Storage

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_BOOKS_PATH` | `./data/books` | Root directory where uploaded book files are stored. Each library gets a sub-directory. |
| `BOOKTOWER_COVERS_PATH` | `./data/covers` | Directory for generated and uploaded cover images. |
| `BOOKTOWER_TEMP_PATH` | `./data/temp` | Scratch space for file uploads before they are moved to the books directory. |

All three directories are created automatically on startup if they do not exist.

## Email / SMTP

SMTP is disabled by default. Set at minimum `BOOKTOWER_SMTP_HOST` and `BOOKTOWER_SMTP_FROM` to enable email features (password resets, book delivery).

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_SMTP_HOST` | *(empty)* | SMTP server hostname. Leave empty to disable email. |
| `BOOKTOWER_SMTP_PORT` | `587` | SMTP server port. |
| `BOOKTOWER_SMTP_USER` | *(empty)* | SMTP authentication username. |
| `BOOKTOWER_SMTP_PASS` | *(empty)* | SMTP authentication password. |
| `BOOKTOWER_SMTP_FROM` | *(empty)* | Sender email address (e.g. `booktower@example.com`). |
| `BOOKTOWER_SMTP_TLS` | `true` | Enable STARTTLS. Set to `false` to disable. |

## OIDC / SSO

OIDC is auto-enabled when `OIDC_ISSUER` is set to a non-empty value.

| Variable | Default | Description |
|---|---|---|
| `OIDC_ISSUER` | *(empty)* | OIDC provider issuer URL (e.g. `https://auth.example.com/realms/main`). Setting this enables OIDC. |
| `OIDC_CLIENT_ID` | *(empty)* | OIDC client ID. |
| `OIDC_CLIENT_SECRET` | *(empty)* | OIDC client secret. |
| `OIDC_REDIRECT_URI` | *(empty)* | Callback URL (e.g. `https://books.example.com/auth/oidc/callback`). |
| `OIDC_SCOPE` | `openid email profile` | Space-separated OIDC scopes to request. |
| `OIDC_FORCE_ONLY` | `false` | When `true`, local username/password login is disabled; users must authenticate via OIDC. |
| `OIDC_ADMIN_GROUP_PATTERN` | *(empty)* | Regex matched against OIDC groups claim to grant admin role (e.g. `^booktower-admin$`). |
| `OIDC_GROUPS_CLAIM` | `groups` | Name of the claim in the userinfo response that contains group memberships. |

## Metadata API Keys

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_HARDCOVER_API_KEY` | *(empty)* | API key for Hardcover.app metadata lookups and sync. |
| `BOOKTOWER_COMICVINE_API_KEY` | *(empty)* | API key for ComicVine comic metadata lookups. |

## Full-Text Search

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_FTS_ENABLED` | `false` | Enable full-text search indexing. |
| `BOOKTOWER_FTS_THROTTLE_MS` | `300` | Debounce interval (ms) for search-as-you-type queries. |

## Auto-Scan

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_AUTO_SCAN_MINUTES` | `60` | How often (in minutes) BookTower scans all library folders for new files. Set to `0` to disable automatic scanning. |

When auto-scan runs, it queries every `(user_id, library_id)` pair in the database and calls the same folder-scan logic as the manual "Scan Folder" button.

## Demo Mode

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_DEMO_MODE` | `false` | Enable demo mode. Restricts destructive operations and displays a demo banner. |

## Registration

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_REGISTRATION_OPEN` | `true` | Allow new user registration. Set to `false` to close registration (admin can still create users). |

## Internationalisation (Weblate)

Weblate integration is optional and disabled by default. It allows translators to contribute strings via a hosted Weblate instance.

These settings are configured in `src/main/resources/application.conf` rather than environment variables:

```hocon
app.weblate {
  enabled = false
  url = "https://weblate.example.com"
  api-token = ""
  component = "booktower/main"
}
```

Set `enabled = true` and provide a valid `api-token` to activate the `/admin/i18n` translation management panel.

---

## Full Example (production `.env`)

```env
# Server
BOOKTOWER_ENV=production
BOOKTOWER_HOST=0.0.0.0
BOOKTOWER_PORT=8080
BOOKTOWER_BASE_URL=https://books.example.com

# Security
BOOKTOWER_JWT_SECRET=your-long-random-secret-here
BOOKTOWER_CSRF_ALLOWED_HOSTS=books.example.com

# Database (PostgreSQL)
BOOKTOWER_DB_URL=jdbc:postgresql://localhost:5432/booktower
BOOKTOWER_DB_USERNAME=booktower
BOOKTOWER_DB_PASSWORD=secret
BOOKTOWER_DB_DRIVER=org.postgresql.Driver

# Storage
BOOKTOWER_BOOKS_PATH=/data/books
BOOKTOWER_COVERS_PATH=/data/covers
BOOKTOWER_TEMP_PATH=/data/temp

# Email
BOOKTOWER_SMTP_HOST=smtp.example.com
BOOKTOWER_SMTP_PORT=587
BOOKTOWER_SMTP_USER=booktower@example.com
BOOKTOWER_SMTP_PASS=smtp-password
BOOKTOWER_SMTP_FROM=booktower@example.com
BOOKTOWER_SMTP_TLS=true

# OIDC (optional)
OIDC_ISSUER=https://auth.example.com/realms/main
OIDC_CLIENT_ID=booktower
OIDC_CLIENT_SECRET=oidc-client-secret
OIDC_REDIRECT_URI=https://books.example.com/auth/oidc/callback

# Metadata
BOOKTOWER_HARDCOVER_API_KEY=hc_xxxxxxxxxxxx
BOOKTOWER_COMICVINE_API_KEY=xxxxxxxxxxxxxxxx

# Features
BOOKTOWER_FTS_ENABLED=true
BOOKTOWER_AUTO_SCAN_MINUTES=60
BOOKTOWER_REGISTRATION_OPEN=true
```
