# Configuration Reference

All BookTower settings have sensible defaults for local development. Override any of them with environment variables for production deployments.

## Server

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_HOST` | `0.0.0.0` | Network interface to bind. Use `127.0.0.1` to restrict to localhost. |
| `BOOKTOWER_PORT` | `8080` | TCP port the HTTP server listens on. |
| `BOOKTOWER_ENV` | — | Set to `production` to enforce a non-default JWT secret and enable production-mode guards. |

## Security

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_JWT_SECRET` | `booktower-secret-key-change-in-production` | HMAC secret used to sign JWT session cookies. **Must be changed for production** — the app will refuse to start with the default secret when `BOOKTOWER_ENV=production`. |
| `BOOKTOWER_CSRF_ALLOWED_HOSTS` | `localhost,localhost:8080` | Comma-separated list of `Host` header values permitted for state-changing requests. Add your domain name when deploying behind a reverse proxy. |

## Database

BookTower uses JDBI 3 with H2 by default. Any JDBC-compatible database can be used by changing the URL and driver.

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_DB_URL` | `jdbc:h2:mem:booktower;DB_CLOSE_DELAY=-1` | JDBC connection URL. For persistence use `jdbc:h2:file:/data/booktower`. |
| `BOOKTOWER_DB_USERNAME` | `sa` | Database username. |
| `BOOKTOWER_DB_PASSWORD` | *(empty)* | Database password. |
| `BOOKTOWER_DB_DRIVER` | `org.h2.Driver` | Fully-qualified JDBC driver class name. |

### Persistent H2 (recommended for production)

```
BOOKTOWER_DB_URL=jdbc:h2:file:/data/booktower;AUTO_SERVER=TRUE
```

`AUTO_SERVER=TRUE` allows multiple JVM processes to connect to the same file, which is useful if you run a maintenance script alongside the server.

## Storage

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_BOOKS_PATH` | `./data/books` | Root directory where uploaded book files are stored. Each library gets a sub-directory. |
| `BOOKTOWER_COVERS_PATH` | `./data/covers` | Directory for generated and uploaded cover images. |
| `BOOKTOWER_TEMP_PATH` | `./data/temp` | Scratch space for file uploads before they are moved to the books directory. |

All three directories are created automatically on startup if they do not exist.

## Scheduled Auto-Scan

| Variable | Default | Description |
|---|---|---|
| `BOOKTOWER_AUTO_SCAN_MINUTES` | `60` | How often (in minutes) BookTower scans all library folders for new files. Set to `0` to disable automatic scanning. |

When auto-scan runs, it queries every `(user_id, library_id)` pair in the database and calls the same folder-scan logic as the manual "Scan Folder" button.

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

## Full Example (production `.env`)

```env
BOOKTOWER_ENV=production
BOOKTOWER_HOST=0.0.0.0
BOOKTOWER_PORT=8080
BOOKTOWER_JWT_SECRET=your-long-random-secret-here
BOOKTOWER_CSRF_ALLOWED_HOSTS=books.example.com
BOOKTOWER_DB_URL=jdbc:h2:file:/data/booktower;AUTO_SERVER=TRUE
BOOKTOWER_DB_USERNAME=sa
BOOKTOWER_DB_PASSWORD=
BOOKTOWER_BOOKS_PATH=/data/books
BOOKTOWER_COVERS_PATH=/data/covers
BOOKTOWER_TEMP_PATH=/data/temp
BOOKTOWER_AUTO_SCAN_MINUTES=60
```
