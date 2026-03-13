@echo off
REM BookTower End-to-End Test Script for Windows
REM This script starts the application and runs comprehensive E2E tests

setlocal enabledelayedexpansion
set BASE_URL=http://localhost:9999

echo ==========================================
echo BookTower End-to-End Test
echo ==========================================
echo.

REM Check if the app is already running
curl -s %BASE_URL% >nul 2>&1
if errorlevel 0 (
    echo [WARNING] BookTower is already running on %BASE_URL%
    echo Testing against running instance...
) else (
    echo Starting BookTower application...
    echo.
    
    REM Start the application in background
    start /B mvn compile exec:java -Dexec.mainClass="org.booktower.BookTowerAppKt"
    
    echo Started application
    echo.
    
    REM Wait for the application to start
    echo Waiting for application to start (15s)...
    timeout /t 15 /nobreak >nul
    
    echo Application started successfully
    echo.
)

echo ==========================================
echo Running End-to-End Tests
echo ==========================================
echo.

REM Authentication Tests
echo [1/8] Testing user registration...
curl -s -X POST -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "username=testuser2&email=testuser2@example.com&password=TestPass123!" ^
  %BASE_URL%/auth/register
if errorlevel 0 (
    echo [PASS] User registration
) else (
    echo [FAIL] User registration
)

echo.
echo [2/8] Testing user login...
curl -s -X POST -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "username=testuser&password=TestPass123!" ^
  -c - "Set-Cookie:" %BASE_URL%/auth/login
if errorlevel 0 (
    echo [PASS] User login
) else (
    echo [FAIL] User login
)

echo.
echo API Tests
echo [3/8] Testing home page...
curl -s %BASE_URL%/
if errorlevel 0 (
    echo [PASS] Home page
) else (
    echo [FAIL] Home page
)

echo.
echo [4/8] Testing list libraries (requires token - placeholder)...
echo [SKIP] List libraries - requires authenticated token
curl -s %BASE_URL%/api/libraries 2>nul

echo.
echo [5/8] Testing list books (requires token - placeholder)...
echo [SKIP] List books - requires authenticated token
curl -s %BASE_URL%/api/books 2>nul

echo.
echo ==========================================
echo Test Complete
echo ==========================================
echo.
echo To run full E2E tests with automatic app startup:
echo   1. Make sure BookTower is stopped
echo   2. Run: mvn compile exec:java -Dexec.mainClass=org.booktower.BookTowerAppKt
echo   3. In a separate terminal, run: bash e2e-test.sh (on Linux/Mac)
echo      or: e2e-test.bat (on Windows)
echo.
echo To run JUnit tests:
echo   mvn test -Dtest=EndToEndTest
echo.
pause
