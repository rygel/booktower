#!/bin/bash
# Generate Runary User Manual as EPUB and PDF from Markdown docs.
# Requires: pandoc (https://pandoc.org/)
# Usage: ./docs/manual/generate-manual.sh
# Output: target/manual/runary-manual.epub, target/manual/runary-manual.pdf

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
DOCS_DIR="$PROJECT_DIR/docs"
MANUAL_DIR="$SCRIPT_DIR"
OUT_DIR="$PROJECT_DIR/target/manual"

mkdir -p "$OUT_DIR"

# User-facing chapters in reading order
CHAPTERS=(
    "$DOCS_DIR/INSTALLATION.md"
    "$DOCS_DIR/QUICKSTART.md"
    "$DOCS_DIR/CONFIGURATION.md"
    "$DOCS_DIR/DEPLOYMENT.md"
    "$DOCS_DIR/DOCKER.md"
    "$DOCS_DIR/DEVICE_SYNC.md"
    "$DOCS_DIR/API.md"
    "$DOCS_DIR/DEVELOPMENT.md"
)

# Filter to only existing files
EXISTING=()
for ch in "${CHAPTERS[@]}"; do
    if [ -f "$ch" ]; then
        EXISTING+=("$ch")
    else
        echo "Warning: $ch not found, skipping"
    fi
done

if [ ${#EXISTING[@]} -eq 0 ]; then
    echo "Error: no chapter files found"
    exit 1
fi

echo "Generating Runary User Manual from ${#EXISTING[@]} chapters..."

# Inject version from pom.xml if available
VERSION=$(grep -m1 '<version>' "$PROJECT_DIR/pom.xml" | sed 's/.*<version>\(.*\)<\/version>.*/\1/' || echo "dev")
export VERSION

# ── EPUB ──────────────────────────────────────────────────────────────────────
echo "  EPUB..."
pandoc \
    --metadata-file="$MANUAL_DIR/metadata.yaml" \
    --toc --toc-depth=2 \
    --split-level=1 \
    --shift-heading-level-by=-1 \
    -o "$OUT_DIR/runary-manual.epub" \
    "${EXISTING[@]}"

echo "  -> $OUT_DIR/runary-manual.epub"

# ── PDF ───────────────────────────────────────────────────────────────────────
if command -v typst >/dev/null 2>&1; then
    echo "  PDF (via typst)..."
    pandoc \
        --metadata-file="$MANUAL_DIR/metadata.yaml" \
        --toc --toc-depth=2 \
        --shift-heading-level-by=-1 \
        --pdf-engine=typst \
        -o "$OUT_DIR/runary-manual.pdf" \
        "${EXISTING[@]}"
    echo "  -> $OUT_DIR/runary-manual.pdf"
elif command -v xelatex >/dev/null 2>&1; then
    echo "  PDF (via LaTeX)..."
    pandoc \
        --metadata-file="$MANUAL_DIR/metadata.yaml" \
        --toc --toc-depth=2 \
        --shift-heading-level-by=-1 \
        --pdf-engine=xelatex \
        -V geometry:margin=2.5cm \
        -V fontsize=11pt \
        -o "$OUT_DIR/runary-manual.pdf" \
        "${EXISTING[@]}"
    echo "  -> $OUT_DIR/runary-manual.pdf"
else
    echo "  PDF skipped (install typst or texlive-xetex)"
fi

echo "Done."
