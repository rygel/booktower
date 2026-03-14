#!/usr/bin/env pwsh
# Manual Testing Guide - Interactive testing script for BookTower frontend

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  BookTower Manual Testing Guide" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "PREPARATION:" -ForegroundColor Yellow
Write-Host "1. Start the server: .\start-dev.ps1 -OpenBrowser"
Write-Host "2. Wait for server to start (check for 'BookTower started successfully!')"
Write-Host "3. Server will be available at http://localhost:9999"
Write-Host ""

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  TEST SCENARIOS" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "SCENARIO 1: Home Page (Not Authenticated)" -ForegroundColor Green
Write-Host "✓ Navigate to http://localhost:9999"
Write-Host "✓ See 'Welcome to BookTower' header"
Write-Host "✓ See Login and Sign Up buttons"
Write-Host "✓ See feature cards (Organize, Read, Track)"
Write-Host "✓ Click 'Theme' dropdown and select different themes"
Write-Host "✓ Click 'Language' dropdown and see options"
Write-Host ""

Write-Host "SCENARIO 2: User Registration" -ForegroundColor Green
Write-Host "✓ Click 'Sign Up' button"
Write-Host "✓ Modal should appear with 'Create Account' form"
Write-Host "✓ Fill in:"
Write-Host "   - Username: testuser"
Write-Host "   - Email: test@example.com"
Write-Host "   - Password: password123"
Write-Host "✓ Click 'Sign Up' button"
Write-Host "✓ Should redirect to home page with 'Your Libraries' section"
Write-Host "✓ Check browser DevTools > Application > Cookies"
Write-Host "✓ Verify 'token' cookie is set"
Write-Host ""

Write-Host "SCENARIO 3: Login" -ForegroundColor Green
Write-Host "✓ Clear cookies (or open incognito window)"
Write-Host "✓ Navigate to http://localhost:9999/login"
Write-Host "✓ Or click 'Login' button from home page"
Write-Host "✓ Fill in:"
Write-Host "   - Username: testuser"
Write-Host "   - Password: password123"
Write-Host "✓ Click 'Login' button"
Write-Host "✓ Should redirect to home page with libraries"
Write-Host "✓ See 'Your Libraries' header"
Write-Host "✓ See library dropdown (empty initially)"
Write-Host "✓ See books container"
Write-Host ""

Write-Host "SCENARIO 4: Create Library" -ForegroundColor Green
Write-Host "✓ After login, open DevTools Console"
Write-Host "✓ Run this command to create a library:"
Write-Host ""
Write-Host "fetch('/api/libraries', {" -ForegroundColor Cyan
Write-Host "  method: 'POST'," -ForegroundColor Cyan
Write-Host "  headers: {" -ForegroundColor Cyan
Write-Host "    'Content-Type': 'application/json'" -ForegroundColor Cyan
Write-Host "  }," -ForegroundColor Cyan
Write-Host "  body: JSON.stringify({" -ForegroundColor Cyan
Write-Host "    name: 'My First Library'," -ForegroundColor Cyan
Write-Host "    path: './data/libraries/my-library'" -ForegroundColor Cyan
Write-Host "  })" -ForegroundColor Cyan
Write-Host "}).then(r => r.json()).then(console.log);" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ Should see response with library data"
Write-Host "✓ Refresh page"
Write-Host "✓ Library should appear in dropdown"
Write-Host ""

Write-Host "SCENARIO 5: List Libraries" -ForegroundColor Green
Write-Host "✓ After login, run this command:"
Write-Host ""
Write-Host "fetch('/api/libraries').then(r => r.json()).then(console.log);" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ Should see array of libraries"
Write-Host "✓ Each library should have: id, name, path, bookCount, createdAt"
Write-Host ""

Write-Host "SCENARIO 6: Create Book" -ForegroundColor Green
Write-Host "✓ First, get a library ID from previous command"
Write-Host "✓ Run this command (replace LIBRARY_ID with actual ID):"
Write-Host ""
Write-Host "fetch('/api/books', {" -ForegroundColor Cyan
Write-Host "  method: 'POST'," -ForegroundColor Cyan
Write-Host "  headers: {" -ForegroundColor Cyan
Write-Host "    'Content-Type': 'application/json'" -ForegroundColor Cyan
Write-Host "  }," -ForegroundColor Cyan
Write-Host "  body: JSON.stringify({" -ForegroundColor Cyan
Write-Host "    title: 'Test Book'," -ForegroundColor Cyan
Write-Host "    author: 'Test Author'," -ForegroundColor Cyan
Write-Host "    libraryId: 'LIBRARY_ID'" -ForegroundColor Cyan
Write-Host "  })" -ForegroundColor Cyan
Write-Host "}).then(r => r.json()).then(console.log);" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ Should see response with book data"
Write-Host ""

Write-Host "SCENARIO 7: List Books" -ForegroundColor Green
Write-Host "✓ Run this command:"
Write-Host ""
Write-Host "fetch('/api/books').then(r => r.json()).then(console.log);" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ Should see array of books"
Write-Host "✓ Each book should have: id, title, author, fileSize, addedAt, progress"
Write-Host ""

Write-Host "SCENARIO 8: Pagination" -ForegroundColor Green
Write-Host "✓ Test page 1:"
Write-Host "fetch('/api/books?page=1').then(r => r.json()).then(console.log);" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ Test page 2:"
Write-Host "fetch('/api/books?page=2').then(r => r.json()).then(console.log);" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ Test custom page size:"
Write-Host "fetch('/api/books?page=1&pageSize=5').then(r => r.json()).then(console.log);" -ForegroundColor Cyan
Write-Host ""

Write-Host "SCENARIO 9: Filter by Library" -ForegroundColor Green
Write-Host "✓ Get a library ID"
Write-Host "✓ Run this command (replace LIBRARY_ID):"
Write-Host ""
Write-Host "fetch('/api/books?libraryId=LIBRARY_ID').then(r => r.json()).then(console.log);" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ Should see only books from that library"
Write-Host ""

Write-Host "SCENARIO 10: Recent Books" -ForegroundColor Green
Write-Host "✓ Run this command:"
Write-Host ""
Write-Host "fetch('/api/recent').then(r => r.json()).then(console.log);" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ Should see recent books based on reading progress"
Write-Host ""

Write-Host "SCENARIO 11: Logout" -ForegroundColor Green
Write-Host "✓ Click 'Logout' button in sidebar"
Write-Host "✓ Should redirect to home page"
Write-Host "✓ See 'Welcome to BookTower' again"
Write-Host "✓ Check cookies - 'token' should be cleared or expired"
Write-Host ""

Write-Host "SCENARIO 12: Protected Routes (No Auth)" -ForegroundColor Green
Write-Host "✓ Open incognito window"
Write-Host "✓ Try accessing:"
Write-Host "   - http://localhost:9999/api/libraries"
Write-Host "   - http://localhost:9999/api/books"
Write-Host "   - http://localhost:9999/api/recent"
Write-Host "✓ Should get 401 Unauthorized error"
Write-Host ""

Write-Host "SCENARIO 13: Theme Switching" -ForegroundColor Green
Write-Host "✓ Navigate to http://localhost:9999"
Write-Host "✓ Open Theme dropdown"
Write-Host "✓ Select different themes:"
Write-Host "   - Dark (default)"
Write-Host "   - Light"
Write-Host "   - Nord"
Write-Host "   - Dracula"
Write-Host "   - Monokai Pro"
Write-Host "   - One Dark"
Write-Host "   - Catppuccin"
Write-Host "✓ Page should reload with new theme"
Write-Host "✓ Check cookies - 'app_theme' should be updated"
Write-Host ""

Write-Host "SCENARIO 14: Language Switching" -ForegroundColor Green
Write-Host "✓ Open Language dropdown"
Write-Host "✓ Select different language:"
Write-Host "   - English (default)"
Write-Host "   - Français"
Write-Host "✓ Page should reload"
Write-Host "✓ Check cookies - 'app_lang' should be updated"
Write-Host ""

Write-Host "SCENARIO 15: Validation Errors" -ForegroundColor Green
Write-Host "✓ Try to register with empty fields:"
Write-Host ""
Write-Host "fetch('/auth/register', {" -ForegroundColor Cyan
Write-Host "  method: 'POST'," -ForegroundColor Cyan
Write-Host "  headers: {" -ForegroundColor Cyan
Write-Host "    'Content-Type': 'application/json'" -ForegroundColor Cyan
Write-Host "  }," -ForegroundColor Cyan
Write-Host "  body: JSON.stringify({" -ForegroundColor Cyan
Write-Host "    username: ''," -ForegroundColor Cyan
Write-Host "    email: ''," -ForegroundColor Cyan
Write-Host "    password: ''" -ForegroundColor Cyan
Write-Host "  })" -ForegroundColor Cyan
Write-Host "}).then(r => r.json()).then(console.log);" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ Should get 400 Bad Request with validation error"
Write-Host "✓ Try with short password (< 8 chars)"
Write-Host "✓ Try with invalid email format"
Write-Host ""

Write-Host "SCENARIO 16: Duplicate User" -ForegroundColor Green
Write-Host "✓ Try to register with same username twice"
Write-Host "✓ Should get 409 Conflict error"
Write-Host "✓ Error message: 'Username already exists' or 'USER_EXISTS'"
Write-Host ""

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  HELPER COMMANDS" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Check server status:" -ForegroundColor Yellow
Write-Host "  curl http://localhost:9999/health" -ForegroundColor Cyan
Write-Host ""
Write-Host "View all cookies:" -ForegroundColor Yellow
Write-Host "  Open DevTools > Application > Cookies > localhost:9999" -ForegroundColor Cyan
Write-Host ""
Write-Host "Clear all cookies:" -ForegroundColor Yellow
Write-Host "  Open DevTools > Application > Cookies > localhost:9999 > Right-click > Clear" -ForegroundColor Cyan
Write-Host ""
Write-Host "View network requests:" -ForegroundColor Yellow
Write-Host "  Open DevTools > Network Tab" -ForegroundColor Cyan
Write-Host ""
Write-Host "View console logs:" -ForegroundColor Yellow
Write-Host "  Open DevTools > Console Tab" -ForegroundColor Cyan
Write-Host ""

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  TROUBLESHOOTING" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Server won't start:" -ForegroundColor Yellow
Write-Host "  - Check if port 9999 is already in use: netstat -ano | findstr :9999"
Write-Host "  - Kill process using port: taskkill /PID <PID> /F"
Write-Host "  - Try different port: .\start-dev.ps1 -Port 8080"
Write-Host ""
Write-Host "Build errors:" -ForegroundColor Yellow
Write-Host "  - Run: mvn clean compile"
Write-Host "  - Check Java version: java -version (should be 21+)"
Write-Host ""
Write-Host "Tests not running:" -ForegroundColor Yellow
Write-Host "  - Run: .\run-tests.ps1 -Test all -SkipFlyway"
Write-Host "  - Or: mvn test -Dflyway.skip=true"
Write-Host ""
Write-Host "401 Unauthorized:" -ForegroundColor Yellow
Write-Host "  - Check if token cookie is set"
Write-Host "  - Clear cookies and login again"
Write-Host "  - Check token expiry (default: 7 days)"
Write-Host ""

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  NEXT STEPS" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Run automated tests:" -ForegroundColor Yellow
Write-Host "   .\run-tests.ps1 -Test frontend" -ForegroundColor Cyan
Write-Host ""
Write-Host "2. Start server:" -ForegroundColor Yellow
Write-Host "   .\start-dev.ps1 -OpenBrowser" -ForegroundColor Cyan
Write-Host ""
Write-Host "3. Run through all test scenarios above" -ForegroundColor Yellow
Write-Host ""
Write-Host "4. Check logs for any errors" -ForegroundColor Yellow
Write-Host ""
Write-Host "5. Test on different browsers:" -ForegroundColor Yellow
Write-Host "   - Chrome, Firefox, Edge" -ForegroundColor Cyan
Write-Host ""
Write-Host "Happy testing! 🚀" -ForegroundColor Green
Write-Host ""
