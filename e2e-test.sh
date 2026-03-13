#!/bin/bash

# BookTower End-to-End Test Script
# This script starts the application and runs comprehensive E2E tests

set -e

BASE_URL="${BASE_URL:-http://localhost:9999}"
APP_PID=""

cleanup() {
    echo "Cleaning up..."
    if [ -n "$APP_PID" ]; then
        kill $APP_PID 2>/dev/null || true
        echo "Stopped application (PID: $APP_PID)"
    fi
}

trap cleanup EXIT INT TERM

echo "=========================================="
echo "BookTower End-to-End Test"
echo "=========================================="
echo ""

# Check if the app is already running
if curl -s "$BASE_URL/" >/dev/null 2>&1; then
    echo "⚠️  BookTower is already running on $BASE_URL"
    echo "Testing against running instance..."
else
    echo "Starting BookTower application..."
    echo ""
    
    # Start the application in background
    cd "$(dirname "$0")"
    mvn compile exec:java -Dexec.mainClass="org.booktower.BookTowerAppKt" &
    APP_PID=$!
    
    echo "Started application with PID: $APP_PID"
    echo ""
    
    # Wait for the application to start
    echo "Waiting for application to start (15s)..."
    sleep 15
    
    # Check if app is running
    if ! kill -0 $APP_PID 2>/dev/null; then
        echo "❌ Application failed to start"
        exit 1
    fi
    
    echo "✓ Application started successfully"
    echo ""
fi

echo "=========================================="
echo "Running End-to-End Tests"
echo "=========================================="
echo ""

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0

# Function to run a test
run_test() {
    local test_name="$1"
    local test_method="$2"
    local test_url="$3"
    local expected_status="$4"
    local should_contain="$5"
    
    echo "[$test_name] Running..."
    
    TESTS_TOTAL=$((TESTS_TOTAL + 1))
    
    local start_time=$(date +%s)
    
    local response
    local status_code
    
    if [ -n "$test_method" ]; then
        response=$(curl -s -X POST -H "Content-Type: application/x-www-form-urlencoded" \
            -d "username=testuser&password=TestPass123!" \
            -w "%{http_code}" -o /dev/stdout "$test_url")
        status_code=$response
    else
        response=$(curl -s -w "%{http_code}" -o /dev/stdout "$test_url")
        status_code=$response
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    if [ "$status_code" -eq "$expected_status" ]; then
        if [ -n "$should_contain" ]; then
            if echo "$response" | grep -qi "$should_contain"; then
                echo "✓ PASSED ($duration s)"
                TESTS_PASSED=$((TESTS_PASSED + 1))
            else
                echo "✗ FAILED - Response doesn't contain expected content ($duration s)"
                TESTS_FAILED=$((TESTS_FAILED + 1))
            fi
        else
            echo "✓ PASSED ($duration s)"
            TESTS_PASSED=$((TESTS_PASSED + 1))
        fi
    else
        echo "✗ FAILED - Expected status $expected_status, got $status_code ($duration s)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
    
    echo ""
}

echo "--- Authentication Tests ---"

# Register test
run_test "Register User" "POST" "$BASE_URL/auth/register" "201" "user"

# Login test (get token)
echo "Getting authentication token..."
TOKEN_RESPONSE=$(curl -s -X POST -H "Content-Type: application/x-www-form-urlencoded" \
    -d "username=testuser&password=TestPass123!" \
    -c "Set-Cookie:" "$BASE_URL/auth/login")

if echo "$TOKEN_RESPONSE" | grep -qi "Set-Cookie:"; then
    TOKEN=$(echo "$TOKEN_RESPONSE" | grep "Set-Cookie:" | sed 's/.*Set-Cookie: token=\([^;]*).*/\1/')
    echo "✓ Retrieved token: ${TOKEN:0:50}..."
else
    echo "✗ Failed to get token"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi

echo ""
echo "--- API Tests ---"

# Create library
run_test "Create Library" "POST" "$BASE_URL/api/libraries" "201"

# List libraries
run_test "" "GET" "$BASE_URL/api/libraries" "200" "name"

# Create book
run_test "Create Book" "POST" "$BASE_URL/api/books" "201" "title"

# List books
run_test "List Books" "GET" "$BASE_URL/api/books" "200"

# Get recent books
run_test "Get Recent Books" "GET" "$BASE_URL/api/recent" "200"

echo ""
echo "--- Validation Tests ---"

# Test home page
run_test "Home Page" "" "GET" "$BASE_URL/" "200" "html"

# Test invalid login
run_test "Invalid Login" "POST" "$BASE_URL/auth/login" "401"

# Test unauthorized access
run_test "Unauthorized Access" "GET" "$BASE_URL/api/libraries" "401"

echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo ""
echo "Total Tests:  $TESTS_TOTAL"
echo "Passed:        $TESTS_PASSED"
echo "Failed:        $TESTS_FAILED"

if [ $TESTS_FAILED -eq 0 ]; then
    echo ""
    echo "🎉 All tests passed!"
    EXIT_CODE=0
else
    echo ""
    echo "❌ Some tests failed"
    EXIT_CODE=1
fi

echo ""
echo "=========================================="

exit $EXIT_CODE
