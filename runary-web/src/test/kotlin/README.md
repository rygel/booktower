# Runary End-to-End Tests

This directory contains comprehensive end-to-end tests for the Runary application.

## Prerequisites

1. Runary application must be running on `http://localhost:9999` (or configured port)
2. Java 21 or later installed
3. curl command available
4. Maven installed

## Quick Start

### Option 1: Run automated E2E test script

```bash
# Make the script executable (Linux/Mac)
chmod +x e2e-test.sh

# Run all E2E tests
./e2e-test.sh
```

### Option 2: Run E2E tests via Maven

```bash
# Run the end-to-end test suite
mvn test -Dtest=EndToEndTest
```

### Option 3: Manual testing

1. Start the application:
   ```bash
   mvn compile exec:java -Dexec.mainClass="org.runary.RunaryAppKt"
   ```

2. Open browser to `http://localhost:9999`

3. Manually test:
   - Register a new account
   - Login
   - Create a library
   - Add books
   - View recent books
   - Logout

## Test Coverage

The test suite covers:

### Authentication Tests
- ✓ Register new user
- ✓ Login with valid credentials
- ✓ Logout
- ✗ Duplicate username registration (should fail)
- ✗ Invalid login (should fail)

### API Tests
- ✓ Create library
- ✓ List libraries
- ✓ Create book
- ✓ List books
- ✓ Get recent books
- ✗ Unauthorized access (should fail)

### Page Access Tests
- ✓ Home page returns 200 and contains HTML
- ✗ Invalid endpoints (should fail)
- ✓ Server error handling

## Running Individual Tests

### Using Maven:

```bash
# Run specific test
mvn test -Dtest=EndToEndTest\#Should register new user

# Run all tests in class
mvn test -Dtest=EndToEndTest
```

### Using curl (manual):

```bash
# Test home page
curl -i http://localhost:9999/

# Test register
curl -X POST http://localhost:9999/auth/register \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=testuser&email=testuser@example.com&password=TestPass123!"

# Test login
curl -X POST http://localhost:9999/auth/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=testuser&password=TestPass123!"

# Test API with token
curl http://localhost:9999/api/libraries \
  -H "Cookie: token=YOUR_TOKEN_HERE"
```

## Test Results

The automated test script provides:
- ✅ Colored pass/fail indicators
- ⏱️ Execution time for each test
- 📊 Summary report at the end
- 🎉 Success/failure exit codes

## Cleanup

The test script automatically stops the application on exit. If the app is already running, it will test against the running instance without restarting.

## Configuration

To change the base URL for testing:

```bash
export BASE_URL=http://localhost:8080
./e2e-test.sh
```

## Troubleshooting

**Application won't start:**
- Check if port 9999 is already in use
- Check application logs for errors

**Tests failing:**
- Make sure the application is fully started before running tests
- Check if the database migrations ran successfully
- Verify API endpoints match the test expectations

**Connection refused:**
- Start the Runary application first
- Wait 15-20 seconds for the app to initialize
- Check if the correct port is configured in `application.conf`

## Integration with CI/CD

To add these tests to GitHub Actions (when you're ready):

1. Create `.github/workflows/e2e-tests.yml`
2. The workflow should:
   - Start the application in background
   - Wait for startup
   - Run the test script
   - Upload test results as artifacts
   - Fail the workflow if any tests fail

Example workflow structure:
```yaml
name: E2E Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Build
        run: mvn compile
      - name: Start Application
        run: mvn exec:java -Dexec.mainClass="org.runary.RunaryAppKt" &
        shell: bash
      - name: Wait for startup
        run: sleep 20
      - name: Run E2E Tests
        run: bash e2e-test.sh
```
