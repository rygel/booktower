#!/usr/bin/env bash
# dev.sh — start BookTower in development mode
#
# JTE templates reload automatically on every request (DirectoryCodeResolver).
# Kotlin/Java source changes require a restart — Ctrl+C then run this again,
# or open a second terminal and run: mvn fizzed-watcher:run
# which watches src/main/kotlin and src/main/resources and restarts on change.

set -e

cd "$(dirname "$0")"

echo "Building BookTower..."
mvn compile -q

echo ""
echo "Starting BookTower on http://localhost:9999"
echo "  Dev login → username: dev  password: dev12345"
echo "  JTE templates hot-reload on every request (no restart needed)"
echo "  Press Ctrl+C to stop"
echo ""

exec mvn exec:java -Dexec.mainClass=org.booktower.BookTowerAppKt -q
