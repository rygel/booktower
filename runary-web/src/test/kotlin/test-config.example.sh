# BookTower E2E Test Configuration
# Copy this file to test-config.sh and modify as needed

# Base URL for the BookTower application
BASE_URL="http://localhost:9999"

# Test user credentials
TEST_USERNAME="testuser"
TEST_PASSWORD="TestPass123!"
TEST_EMAIL="testuser@example.com"

# Test library data
TEST_LIBRARY_NAME="Test Library"
TEST_LIBRARY_PATH="/tmp/test-library"

# Test book data
TEST_BOOK_TITLE="Test Book"
TEST_BOOK_AUTHOR="Test Author"
TEST_BOOK_DESCRIPTION="A test book for E2E testing"

# Test timeout in seconds
TEST_TIMEOUT=30

# Skip starting the app (true = app must already be running)
SKIP_STARTUP=false

# Enable/disable specific tests
TEST_AUTHENTICATION=true
TEST_API_TESTS=true
TEST_VALIDATION_TESTS=true
