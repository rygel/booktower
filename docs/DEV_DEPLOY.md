# Development Deployment via SCP

Deploy a development build from your local machine to a remote server without Docker.

## Prerequisites

**Local machine (build):**
- Java 21 JDK
- Maven 3.9+

**Server:**
- Java 21 JRE (or JDK)
- SSH access

Install Java 21 on the server (Debian/Ubuntu):

```bash
sudo apt update && sudo apt install -y temurin-21-jre
# or: sudo apt install -y openjdk-21-jre-headless
```

## Build

```bash
mvn clean package -DskipTests -T 4
```

This produces `runary-web/target/runary-web-0.7.3-fat.jar` (~80 MB fat JAR with all dependencies).

## Deploy

```bash
# One-liner: build + copy
mvn clean package -DskipTests -T 4 && \
  scp runary-web/target/runary-web-*-fat.jar yourserver:/opt/runary/app.jar
```

Or with a deploy script (`deploy.sh`):

```bash
#!/usr/bin/env bash
set -euo pipefail

SERVER="${1:?Usage: ./deploy.sh user@host}"
JAR=$(ls runary-web/target/runary-web-*-fat.jar 2>/dev/null | head -1)

if [ -z "$JAR" ]; then
  echo "Building..."
  mvn clean package -DskipTests -T 4
  JAR=$(ls runary-web/target/runary-web-*-fat.jar | head -1)
fi

echo "Deploying $JAR to $SERVER..."
scp "$JAR" "$SERVER:/opt/runary/app.jar"
ssh "$SERVER" 'sudo systemctl restart runary'
echo "Done. Check: ssh $SERVER journalctl -u runary -f"
```

## Server Setup (first time)

```bash
# Create user and directories
sudo useradd -r -m -d /opt/runary runary
sudo mkdir -p /opt/runary /var/lib/runary/{books,covers,temp,db}
sudo chown -R runary:runary /opt/runary /var/lib/runary

# Create environment file
sudo tee /etc/runary.env > /dev/null <<'EOF'
RUNARY_ENV=production
RUNARY_PORT=9999
RUNARY_JWT_SECRET=CHANGE_ME_RUN_openssl_rand_base64_32
RUNARY_DB_URL=jdbc:h2:file:/var/lib/runary/db/runary;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
RUNARY_BOOKS_PATH=/var/lib/runary/books
RUNARY_COVERS_PATH=/var/lib/runary/covers
RUNARY_TEMP_PATH=/var/lib/runary/temp
RUNARY_CSRF_ALLOWED_HOSTS=localhost,books.yourdomain.com
RUNARY_BASE_URL=https://books.yourdomain.com
EOF
sudo chmod 600 /etc/runary.env

# Generate a real JWT secret
sudo sed -i "s|CHANGE_ME_RUN_openssl_rand_base64_32|$(openssl rand -base64 32)|" /etc/runary.env

# Create systemd service
sudo tee /etc/systemd/system/runary.service > /dev/null <<'EOF'
[Unit]
Description=Runary Book Server
After=network.target

[Service]
User=runary
WorkingDirectory=/opt/runary
EnvironmentFile=/etc/runary.env
ExecStart=/usr/bin/java -Xms128m -Xmx512m -jar /opt/runary/app.jar
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable --now runary
```

## Day-to-day Workflow

```bash
# 1. Make changes locally, verify they compile
mvn test-compile -T 4

# 2. Run a quick test (optional)
mvn test -pl runary-web -am -Dtest="KoinResolutionTest" -Dsurefire.failIfNoSpecifiedTests=false

# 3. Build and deploy
mvn clean package -DskipTests -T 4
scp runary-web/target/runary-web-*-fat.jar yourserver:/opt/runary/app.jar
ssh yourserver sudo systemctl restart runary

# 4. Check logs
ssh yourserver journalctl -u runary -f

# 5. Verify health
curl https://books.yourdomain.com/health
```

## Useful Server Commands

```bash
# View logs
journalctl -u runary -f              # follow live
journalctl -u runary --since "5m ago" # last 5 minutes
journalctl -u runary -p err           # errors only

# Restart
sudo systemctl restart runary

# Stop
sudo systemctl stop runary

# Check status
systemctl status runary
```

## Using PostgreSQL Instead of H2

Update `/etc/runary.env`:

```env
RUNARY_DB_URL=jdbc:postgresql://localhost:5432/runary
RUNARY_DB_USERNAME=runary
RUNARY_DB_PASSWORD=your-db-password
RUNARY_DB_DRIVER=org.postgresql.Driver
RUNARY_FTS_ENABLED=true
```

PostgreSQL setup:

```bash
sudo apt install -y postgresql
sudo -u postgres createuser runary
sudo -u postgres createdb -O runary runary
sudo -u postgres psql -c "ALTER USER runary PASSWORD 'your-db-password';"
```

Flyway migrations run automatically on startup — no manual schema setup needed.

## Reverse Proxy

### Caddy (recommended — auto TLS)

```
books.yourdomain.com {
    reverse_proxy localhost:9999
}
```

### Nginx

```nginx
server {
    listen 443 ssl;
    server_name books.yourdomain.com;

    ssl_certificate     /etc/letsencrypt/live/books.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/books.yourdomain.com/privkey.pem;

    client_max_body_size 500M;

    location / {
        proxy_pass http://127.0.0.1:9999;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Make sure `RUNARY_CSRF_ALLOWED_HOSTS` includes your domain and `RUNARY_BASE_URL` uses the public HTTPS URL.

## Upgrading

```bash
# On your local machine
git pull && mvn clean package -DskipTests -T 4
scp runary-web/target/runary-web-*-fat.jar yourserver:/opt/runary/app.jar
ssh yourserver sudo systemctl restart runary
```

Database migrations run automatically on startup. No manual steps needed.

## Backup

```bash
# H2 database (stop first to avoid corruption)
ssh yourserver 'sudo systemctl stop runary && \
  cp /var/lib/runary/db/runary.mv.db /var/lib/runary/backup-$(date +%Y%m%d).mv.db && \
  sudo systemctl start runary'

# Books and covers (can be done live)
rsync -avz yourserver:/var/lib/runary/books/ ./backup/books/
rsync -avz yourserver:/var/lib/runary/covers/ ./backup/covers/
```
