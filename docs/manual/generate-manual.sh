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

# All chapters in reading order, grouped by audience
CHAPTERS=(
    # Part I — Getting Started
    "$DOCS_DIR/README.md"
    "$DOCS_DIR/QUICKSTART.md"
    "$DOCS_DIR/INSTALLATION.md"

    # Part II — Configuration & Deployment
    "$DOCS_DIR/CONFIGURATION.md"
    "$DOCS_DIR/DEPLOYMENT.md"
    "$DOCS_DIR/DOCKER.md"
    "$DOCS_DIR/DEMO.md"
    "$DOCS_DIR/POWERSHELL_SCRIPTS.md"

    # Part III — Using Runary
    "$DOCS_DIR/DEVICE_SYNC.md"
    "$DOCS_DIR/API.md"

    # Part IV — Development
    "$DOCS_DIR/DEVELOPMENT.md"
    "$DOCS_DIR/BACKEND_ARCHITECTURE.md"
    "$DOCS_DIR/FRONTEND_ARCHITECTURE.md"
    "$DOCS_DIR/FRONTEND_SUMMARY.md"
    "$DOCS_DIR/HTTP4K_QUICKSTART.md"
    "$DOCS_DIR/HTMX_TEST_COVERAGE.md"
    "$DOCS_DIR/JACKSON_EXPLANATION.md"
    "$DOCS_DIR/AGENTS.md"

    # Part V — Architecture & Planning
    "$DOCS_DIR/ARCHITECTURE_IMPROVEMENTS.md"
    "$DOCS_DIR/COMPLETE_REWRITE_ANALYSIS.md"
    "$DOCS_DIR/REFACTORING_VS_GREENFIELD.md"
    "$DOCS_DIR/GREENFIELD_IMPLEMENTATION.md"
    "$DOCS_DIR/MVP_REWRITE_PLAN.md"
    "$DOCS_DIR/ANGULAR_TO_HTTP4K_MIGRATION_PLAN.md"
    "$DOCS_DIR/CROSS_CHECK_MIGRATION.md"
    "$DOCS_DIR/READER_ALTERNATIVES.md"
    "$DOCS_DIR/SERVER_DRIVEN_READERS.md"

    # Part VI — Reference
    "$DOCS_DIR/RELEASES.md"
    "$DOCS_DIR/BUG_FIXES.md"
    "$DOCS_DIR/TODO.md"
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
    -f markdown-citations \
    -o "$OUT_DIR/runary-manual.epub" \
    "${EXISTING[@]}"

echo "  -> $OUT_DIR/runary-manual.epub"

# ── PDF ───────────────────────────────────────────────────────────────────────
if command -v typst >/dev/null 2>&1; then
    echo "  PDF (via typst)..."
    if pandoc \
        --metadata-file="$MANUAL_DIR/metadata.yaml" \
        --toc --toc-depth=2 \
        --shift-heading-level-by=-1 \
        --pdf-engine=typst \
        -f markdown-citations \
        -o "$OUT_DIR/runary-manual.pdf" \
        "${EXISTING[@]}" 2>&1; then
        echo "  -> $OUT_DIR/runary-manual.pdf"
    else
        echo "  PDF generation failed (non-fatal, EPUB still available)"
    fi
elif command -v xelatex >/dev/null 2>&1; then
    echo "  PDF (via LaTeX)..."
    pandoc \
        --metadata-file="$MANUAL_DIR/metadata.yaml" \
        --toc --toc-depth=2 \
        --shift-heading-level-by=-1 \
        --pdf-engine=xelatex \
        -f markdown-citations \
        -V geometry:margin=2.5cm \
        -V fontsize=11pt \
        -o "$OUT_DIR/runary-manual.pdf" \
        "${EXISTING[@]}" || echo "  PDF generation failed (non-fatal)"
else
    echo "  PDF skipped (install typst or texlive-xetex)"
fi

echo "Done."
