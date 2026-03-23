# Runary Demo Instance Setup

Set up a demo instance of Runary with pre-seeded sample data for evaluation, showcasing, or testing.

## Quick Start (One Command)

```bash
docker run -d --name runary-demo \
  -p 9999:8080 \
  -e RUNARY_QUICKSTART=true \
  -e RUNARY_DEMO_MODE=true \
  -e RUNARY_JWT_SECRET=demo-instance-not-for-production \
  ghcr.io/rygel/runary:latest
```

Open http://localhost:9999 and log in with:
- **Username**: `demo`
- **Password**: `demo1234`

## Docker Compose

```bash
curl -O https://raw.githubusercontent.com/rygel/runary/main/docs/examples/docker-compose.demo.yml
docker compose -f docker-compose.demo.yml up -d
```

See [`docs/examples/docker-compose.demo.yml`](examples/docker-compose.demo.yml).

## Fat JAR

```bash
RUNARY_QUICKSTART=true java -jar runary.jar
```

## Native Binary

```bash
RUNARY_QUICKSTART=true ./runary-linux-x64
```

## What Gets Seeded

When `RUNARY_QUICKSTART=true`, Runary automatically creates:

### Demo Account
- **Username**: `demo`
- **Email**: `demo@runary.local`
- **Password**: `demo1234`
- **Library**: "My Library" with a default storage folder

### Sample Library: Science Fiction Classics
A curated library of 68+ public domain books with full metadata:

- H.G. Wells — *The War of the Worlds*, *The Time Machine*, *The Invisible Man*, *The Island of Doctor Moreau*
- Jules Verne — *Twenty Thousand Leagues Under the Sea*, *Journey to the Center of the Earth*, *Around the World in Eighty Days*
- Mary Shelley — *Frankenstein*
- And many more classic titles

Each book includes:
- Title, author, description, publisher, publication date
- Ratings and reading status
- Tags and categories
- Series information where applicable

### Audiobook Library: LibriVox Audiobooks
Three public domain audiobooks from LibriVox with real downloadable chapter audio:

- H.G. Wells — *The War of the Worlds*
- Jules Verne — *Around the World in 80 Days*
- Mary Shelley — *Frankenstein*

Audio chapters are downloaded from LibriVox on first startup (requires internet).

## Demo Mode vs Quickstart Mode

| Setting | Purpose |
|---------|---------|
| `RUNARY_QUICKSTART=true` | Seeds the demo account and sample data on first startup |
| `RUNARY_DEMO_MODE=true` | Restricts destructive operations (no deleting libraries/users) and shows a demo banner |

You can use them independently:
- **Quickstart only** — seeds data but allows full access (good for dev/testing)
- **Demo mode only** — restricts operations but doesn't seed data (good for shared instances)
- **Both** — seeds data and restricts operations (good for public demos)

## Dev Account

When `RUNARY_ENV` is **not** set to `production`, a dev account is also created:
- **Username**: `dev`
- **Password**: `dev12345`

## Resetting Demo Data

To reset the demo instance to a fresh state:

```bash
# Docker
docker compose -f docker-compose.demo.yml down -v
docker compose -f docker-compose.demo.yml up -d

# Fat JAR — delete the data directory
rm -rf ./data
RUNARY_QUICKSTART=true java -jar runary.jar
```

## Running a Public Demo

If you want to expose a demo instance publicly:

1. Use both `RUNARY_QUICKSTART=true` and `RUNARY_DEMO_MODE=true`
2. Set `RUNARY_REGISTRATION_OPEN=false` to prevent new sign-ups
3. Put it behind a reverse proxy with HTTPS (see [INSTALLATION.md](INSTALLATION.md#reverse-proxy))
4. Consider restarting the container daily to reset data:

```bash
# Cron job to reset demo nightly at 3 AM
0 3 * * * docker compose -f /path/to/docker-compose.demo.yml down -v && docker compose -f /path/to/docker-compose.demo.yml up -d
```
