# Deployment Guide

## Requirements

- Java 17 or later
- At least 256 MB heap (512 MB recommended for PDF rendering)
- A writable directory for book files, covers, and the H2 database

## Option 1: JAR (bare metal / VM)

### Build

```bash
mvn package -DskipTests
```

This produces `target/runary-*.jar` as a fat JAR with all dependencies.

### Run

```bash
java -jar target/runary-*.jar
```

Set environment variables before running, or pass them inline:

```bash
RUNARY_JWT_SECRET=my-secret \
RUNARY_DB_URL=jdbc:h2:file:/var/lib/runary/db \
RUNARY_BOOKS_PATH=/var/lib/runary/books \
RUNARY_COVERS_PATH=/var/lib/runary/covers \
RUNARY_ENV=production \
java -jar target/runary-*.jar
```

### systemd Unit

```ini
[Unit]
Description=Runary
After=network.target

[Service]
User=runary
WorkingDirectory=/opt/runary
EnvironmentFile=/etc/runary/env
ExecStart=/usr/bin/java -Xmx512m -jar /opt/runary/runary.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

`/etc/runary/env`:

```env
RUNARY_ENV=production
RUNARY_JWT_SECRET=change-me
RUNARY_DB_URL=jdbc:h2:file:/var/lib/runary/db
RUNARY_BOOKS_PATH=/var/lib/runary/books
RUNARY_COVERS_PATH=/var/lib/runary/covers
RUNARY_TEMP_PATH=/var/lib/runary/temp
RUNARY_CSRF_ALLOWED_HOSTS=books.example.com
```

## Option 2: Docker Compose

```yaml
services:
  runary:
    image: runary:latest          # build locally or from registry
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      RUNARY_ENV: production
      RUNARY_JWT_SECRET: change-me-in-production
      RUNARY_DB_URL: jdbc:h2:file:/data/runary;AUTO_SERVER=TRUE
      RUNARY_BOOKS_PATH: /data/books
      RUNARY_COVERS_PATH: /data/covers
      RUNARY_TEMP_PATH: /data/temp
      RUNARY_CSRF_ALLOWED_HOSTS: books.example.com
      RUNARY_AUTO_SCAN_MINUTES: "60"
    volumes:
      - runary_data:/data

volumes:
  runary_data:
```

### Build the Docker image

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/runary-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
```

```bash
mvn package -DskipTests
docker build -t runary:latest .
```

## Reverse Proxy

Runary itself handles HTTPS termination if you put it behind a reverse proxy. Set `RUNARY_CSRF_ALLOWED_HOSTS` to your external hostname.

### Nginx

```nginx
server {
    listen 443 ssl;
    server_name books.example.com;

    ssl_certificate     /etc/letsencrypt/live/books.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/books.example.com/privkey.pem;

    client_max_body_size 500M;   # allow large book uploads

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### Caddy

```
books.example.com {
    reverse_proxy localhost:8080
}
```

Caddy handles TLS automatically.

## Database

The default H2 in-memory database loses all data on restart. For production, use file-based H2:

```
RUNARY_DB_URL=jdbc:h2:file:/var/lib/runary/db;AUTO_SERVER=TRUE
```

The database file is created automatically. Flyway migrations run on every startup and are idempotent.

### Backup

Stop the server, then copy the H2 database files:

```bash
systemctl stop runary
cp /var/lib/runary/db.mv.db /backup/runary-$(date +%Y%m%d).mv.db
systemctl start runary
```

## First-time Setup

1. Start Runary.
2. Register the first account — it automatically receives admin privileges.
3. Create a library, point it at a folder containing your books.
4. Use "Scan Folder" to import existing files.

## Upgrading

Runary applies Flyway database migrations automatically on startup. To upgrade:

1. Replace the JAR (or Docker image).
2. Restart the service.

No manual migration steps are required.

## Storage Layout

```
/data/
├── runary.mv.db     # H2 database (if using file mode)
├── books/
│   └── <library-id>/   # One directory per library
│       └── mybook.pdf
├── covers/
│   └── <book-id>.jpg   # Generated or uploaded cover images
└── temp/               # Scratch space (cleared after upload)
```

## Performance Notes

- PDF page rendering is CPU-bound. A modern single-core is sufficient for personal use.
- The default 512 MB heap is enough for most libraries. Increase with `-Xmx` if you see out-of-memory errors from PDF rendering.
- Auto-scan is single-threaded and runs in a daemon thread; it will not block page rendering.
