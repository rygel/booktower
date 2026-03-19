#!/usr/bin/env bash
# Run the full test suite against a real PostgreSQL instance in Docker.
# Usage: ./test-postgres.sh [17|18]   (default: 18)
set -euo pipefail

PG_VERSION="${1:-18}"
CONTAINER_NAME="booktower-test-pg"

echo "=== Starting PostgreSQL $PG_VERSION ==="
docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
docker run -d --name "$CONTAINER_NAME" \
  -e POSTGRES_USER=booktower \
  -e POSTGRES_PASSWORD=booktower \
  -e POSTGRES_DB=booktower \
  -p 5432:5432 \
  "postgres:${PG_VERSION}-alpine"

echo "Waiting for PostgreSQL to be ready..."
for i in $(seq 1 30); do
  if docker exec "$CONTAINER_NAME" pg_isready -U booktower -q 2>/dev/null; then
    echo "PostgreSQL $PG_VERSION ready after ${i}s"
    break
  fi
  [ "$i" -eq 30 ] && { echo "Timeout waiting for PostgreSQL"; docker rm -f "$CONTAINER_NAME"; exit 1; }
  sleep 1
done

echo "=== Running full test suite against PostgreSQL $PG_VERSION ==="
BOOKTOWER_DB_URL="jdbc:postgresql://localhost:5432/booktower" \
BOOKTOWER_DB_USERNAME=booktower \
BOOKTOWER_DB_PASSWORD=booktower \
BOOKTOWER_DB_DRIVER=org.postgresql.Driver \
mvn test -Dflyway.skip=false "$@"
TEST_EXIT=$?

echo "=== Cleaning up ==="
docker rm -f "$CONTAINER_NAME"

exit $TEST_EXIT
