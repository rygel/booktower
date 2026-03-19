#!/usr/bin/env bash
# ── BookTower E2E smoke tests ─────────────────────────────────────────────────
# Runs against a live application instance. Expects BASE_URL env var
# (defaults to http://localhost:9999).
#
# Exit codes: 0 = all passed, 1 = at least one failure
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:9999}"
PASSED=0
FAILED=0
LOG_FILE="e2e-test.log"

# Unique suffix to avoid collisions with previous runs
TS=$(date +%s%N 2>/dev/null || date +%s)

# ── Helpers ───────────────────────────────────────────────────────────────────

pass() { PASSED=$((PASSED + 1)); echo "  ✓ $1"; echo "PASS: $1" >> "$LOG_FILE"; }
fail() { FAILED=$((FAILED + 1)); echo "  ✗ $1"; echo "FAIL: $1 — $2" >> "$LOG_FILE"; }

# GET and assert status code
assert_get() {
    local path="$1" expected="$2" label="$3"
    local status
    status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL$path")
    if [ "$status" = "$expected" ]; then
        pass "$label (GET $path → $status)"
    else
        fail "$label" "expected $expected, got $status"
    fi
}

# POST JSON and assert status code; captures response body in $BODY
assert_post_json() {
    local path="$1" data="$2" expected="$3" label="$4"
    local status
    BODY=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL$path" \
        -H 'Content-Type: application/json' -d "$data" ${COOKIE:+-H "Cookie: $COOKIE"})
    status=$(echo "$BODY" | tail -1)
    BODY=$(echo "$BODY" | sed '$d')
    if [ "$status" = "$expected" ]; then
        pass "$label (POST $path → $status)"
    else
        fail "$label" "expected $expected, got $status (body: $(echo "$BODY" | head -c 200))"
    fi
}

# Extract JSON field (simple grep, no jq dependency)
json_field() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

> "$LOG_FILE"
echo "BookTower E2E Tests — $BASE_URL"
echo "================================================="

# ── 1. Health & Version ───────────────────────────────────────────────────────
echo ""
echo "Health & infrastructure"

assert_get "/health" "200" "Health endpoint"
assert_get "/api/version" "200" "Version endpoint"
assert_get "/manifest.json" "200" "PWA manifest"

# ── 2. Static Assets ─────────────────────────────────────────────────────────
echo ""
echo "Static assets"

assert_get "/static/css/app.css" "200" "CSS served"

# ── 3. Public Pages ──────────────────────────────────────────────────────────
echo ""
echo "Public pages"

assert_get "/" "200" "Index page"
assert_get "/login" "200" "Login page"
assert_get "/register" "200" "Register page"
assert_get "/forgot-password" "200" "Forgot password page"

# ── 4. Auth: Register + Login ────────────────────────────────────────────────
echo ""
echo "Authentication"

USERNAME="e2e_${TS}"
EMAIL="${USERNAME}@test.local"

COOKIE=""
assert_post_json "/auth/register" \
    "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"e2epassword123\"}" \
    "201" "Register new user"

TOKEN=$(json_field "$BODY" "token")
if [ -n "$TOKEN" ]; then
    pass "Registration returned token"
    COOKIE="token=$TOKEN"
else
    fail "Registration returned token" "no token in response"
    # Can't continue without auth
    echo ""
    echo "FATAL: Cannot continue without authentication token."
    echo "Results: $PASSED passed, $FAILED failed"
    exit 1
fi

# Login with same credentials
assert_post_json "/auth/login" \
    "{\"username\":\"$USERNAME\",\"password\":\"e2epassword123\"}" \
    "200" "Login with credentials"

# ── 5. Protected Endpoints Require Auth ──────────────────────────────────────
echo ""
echo "Auth enforcement"

assert_get "/api/libraries" "401" "Libraries without auth → 401"
assert_get "/api/books" "401" "Books without auth → 401"
assert_get "/api/settings" "401" "Settings without auth → 401"

# ── 6. Library CRUD ──────────────────────────────────────────────────────────
echo ""
echo "Library CRUD"

# Create library
BODY=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/api/libraries" \
    -H "Cookie: $COOKIE" -H 'Content-Type: application/json' \
    -d "{\"name\":\"E2E Library $TS\",\"path\":\"./data/e2e-$TS\"}")
LIB_STATUS=$(echo "$BODY" | tail -1)
BODY=$(echo "$BODY" | sed '$d')
if [ "$LIB_STATUS" = "201" ]; then
    pass "Create library (201)"
else
    fail "Create library" "expected 201, got $LIB_STATUS"
fi

LIB_ID=$(json_field "$BODY" "id")

# List libraries
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/libraries" -H "Cookie: $COOKIE")
if [ "$STATUS" = "200" ]; then
    pass "List libraries (200)"
else
    fail "List libraries" "expected 200, got $STATUS"
fi

# ── 7. Book CRUD ─────────────────────────────────────────────────────────────
echo ""
echo "Book CRUD"

if [ -n "$LIB_ID" ]; then
    # Create book
    BODY=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/api/books" \
        -H "Cookie: $COOKIE" -H 'Content-Type: application/json' \
        -d "{\"title\":\"E2E Book $TS\",\"author\":\"Test Author\",\"description\":null,\"libraryId\":\"$LIB_ID\"}")
    BOOK_STATUS=$(echo "$BODY" | tail -1)
    BODY=$(echo "$BODY" | sed '$d')
    if [ "$BOOK_STATUS" = "201" ]; then
        pass "Create book (201)"
    else
        fail "Create book" "expected 201, got $BOOK_STATUS"
    fi

    BOOK_ID=$(json_field "$BODY" "id")

    # Get book
    if [ -n "$BOOK_ID" ]; then
        assert_status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/books/$BOOK_ID" -H "Cookie: $COOKIE")
        if [ "$assert_status" = "200" ]; then
            pass "Get book by ID (200)"
        else
            fail "Get book by ID" "expected 200, got $assert_status"
        fi

        # Update book
        UPDATE_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$BASE_URL/api/books/$BOOK_ID" \
            -H "Cookie: $COOKIE" -H 'Content-Type: application/json' \
            -d "{\"title\":\"Updated E2E Book\",\"author\":\"Updated Author\"}")
        if [ "$UPDATE_STATUS" = "200" ]; then
            pass "Update book (200)"
        else
            fail "Update book" "expected 200, got $UPDATE_STATUS"
        fi

        # Delete book
        DEL_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE_URL/api/books/$BOOK_ID" \
            -H "Cookie: $COOKIE")
        if [ "$DEL_STATUS" = "200" ]; then
            pass "Delete book (200)"
        else
            fail "Delete book" "expected 200, got $DEL_STATUS"
        fi
    fi

    # Delete library
    DEL_LIB_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE_URL/api/libraries/$LIB_ID" \
        -H "Cookie: $COOKIE")
    if [ "$DEL_LIB_STATUS" = "200" ]; then
        pass "Delete library (200)"
    else
        fail "Delete library" "expected 200, got $DEL_LIB_STATUS"
    fi
fi

# ── 8. Search ────────────────────────────────────────────────────────────────
echo ""
echo "Search"

SEARCH_STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/search?q=test" -H "Cookie: $COOKIE")
if [ "$SEARCH_STATUS" = "200" ]; then
    pass "Search endpoint (200)"
else
    fail "Search endpoint" "expected 200, got $SEARCH_STATUS"
fi

# ── 9. User Settings ────────────────────────────────────────────────────────
echo ""
echo "User settings"

SETTINGS_STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/settings" -H "Cookie: $COOKIE")
if [ "$SETTINGS_STATUS" = "200" ]; then
    pass "Get settings (200)"
else
    fail "Get settings" "expected 200, got $SETTINGS_STATUS"
fi

# ── 10. OPDS Catalog ────────────────────────────────────────────────────────
echo ""
echo "OPDS"

# OPDS uses HTTP Basic Auth, not cookies — expect 401 without credentials
OPDS_STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/opds/catalog")
if [ "$OPDS_STATUS" = "401" ]; then
    pass "OPDS catalog requires auth (401)"
else
    fail "OPDS catalog auth" "expected 401, got $OPDS_STATUS"
fi

# ── 11. Logout ───────────────────────────────────────────────────────────────
echo ""
echo "Logout"

# Logout returns 303 redirect (to /login) for non-HTMX requests
LOGOUT_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/auth/logout" -H "Cookie: $COOKIE")
if [ "$LOGOUT_STATUS" = "303" ] || [ "$LOGOUT_STATUS" = "200" ]; then
    pass "Logout ($LOGOUT_STATUS)"
else
    fail "Logout" "expected 303 or 200, got $LOGOUT_STATUS"
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "================================================="
echo "Results: $PASSED passed, $FAILED failed"
echo "Log: $LOG_FILE"

if [ "$FAILED" -gt 0 ]; then
    exit 1
fi
