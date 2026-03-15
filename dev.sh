#!/usr/bin/env bash
# dev.sh — start BookTower in development mode
#
# JTE templates reload automatically on every request (DirectoryCodeResolver).
# Kotlin/Java source changes require a restart — Ctrl+C then run this again,
# or open a second terminal and run: mvn fizzed-watcher:run
# which watches src/main/kotlin and src/main/resources and restarts on change.

set -e

cd "$(dirname "$0")"

# Install git hooks if not already in place
HOOK=".git/hooks/pre-push"
if [ ! -f "$HOOK" ] || ! diff -q hooks/pre-push "$HOOK" > /dev/null 2>&1; then
    cp hooks/pre-push "$HOOK"
    chmod +x "$HOOK"
    echo "Installed git pre-push hook."
fi

echo "Building BookTower..."
mvn compile -q

echo ""
echo "Starting BookTower on http://localhost:9999"
echo "  Dev login → username: dev  password: dev12345"
echo "  JTE templates hot-reload on every request (no restart needed)"
echo "  Press Ctrl+C to stop"
echo ""

exec mvn exec:java -Dexec.mainClass=org.booktower.BookTowerAppKt -q
