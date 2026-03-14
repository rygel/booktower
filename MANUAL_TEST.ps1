# Manual Testing Guide - Interactive testing script for BookTower

param(
    [switch]$Interactive
)

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  BookTower Manual Testing Guide" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "PREPARATION:" -ForegroundColor Yellow
Write-Host "1. Start the server: .\start-dev.ps1 -OpenBrowser" -ForegroundColor Cyan
Write-Host "2. Wait for server to start (check for 'BookTower started successfully!')" -ForegroundColor Cyan
Write-Host "3. Server will be available at http://localhost:9999" -ForegroundColor Cyan
Write-Host ""

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  TEST SCENARIOS" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "SCENARIO 1: Home Page (Not Authenticated)" -ForegroundColor Green
Write-Host "✓ Navigate to http://localhost:9999" -ForegroundColor Cyan
Write-Host "✓ See 'Welcome to BookTower' header" -ForegroundColor Cyan
Write-Host "✓ See Login and Sign Up buttons" -ForegroundColor Cyan
Write-Host "✓ Click 'Theme' dropdown and select different themes" -ForegroundColor Cyan
Write-Host "✓ Click 'Language' dropdown and see options" -ForegroundColor Cyan
Write-Host ""

Write-Host "SCENARIO 2: User Registration" -ForegroundColor Green
Write-Host "✓ Click 'Sign Up' button" -ForegroundColor Cyan
Write-Host "✓ Modal should appear with 'Create Account' form" -ForegroundColor Cyan
Write-Host "✓ Fill in:" -ForegroundColor Cyan
Write-Host "   - Username: testuser" -ForegroundColor Gray
Write-Host "   - Email: test@example.com" -ForegroundColor Gray
Write-Host "   - Password: TestPass123!" -ForegroundColor Gray
Write-Host "✓ Click 'Sign Up' button" -ForegroundColor Cyan
Write-Host "✓ Should redirect to home page with 'Your Libraries' section" -ForegroundColor Cyan
Write-Host "✓ Check browser DevTools > Application > Cookies" -ForegroundColor Cyan
Write-Host "✓ Verify 'token' cookie is set" -ForegroundColor Cyan
Write-Host ""

Write-Host "SCENARIO 3: Login" -ForegroundColor Green
Write-Host "✓ Clear cookies (or open incognito window)" -ForegroundColor Cyan
Write-Host "✓ Navigate to http://localhost:9999/login" -ForegroundColor Cyan
Write-Host "✓ Or click 'Login' button from home page" -ForegroundColor Cyan
Write-Host "✓ Fill in:" -ForegroundColor Cyan
Write-Host "   - Username: testuser" -ForegroundColor Gray
Write-Host "   - Password: TestPass123!" -ForegroundColor Gray
Write-Host "✓ Click 'Login' button" -ForegroundColor Cyan
Write-Host "✓ Should redirect to home page with libraries" -ForegroundColor Cyan
Write-Host "✓ See 'Your Libraries' header" -ForegroundColor Cyan
Write-Host "✓ See theme and language selectors in sidebar" -ForegroundColor Cyan
Write-Host ""

Write-Host "SCENARIO 4: Theme Switching" -ForegroundColor Green
Write-Host "✓ Navigate to http://localhost:9999" -ForegroundColor Cyan
Write-Host "✓ Open Theme dropdown in sidebar" -ForegroundColor Cyan
Write-Host "✓ Select different themes:" -ForegroundColor Cyan
Write-Host "   - Dark (default)" -ForegroundColor Gray
Write-Host "   - Light" -ForegroundColor Gray
Write-Host "   - Nord" -ForegroundColor Gray
Write-Host "   - Dracula" -ForegroundColor Gray
Write-Host "   - Monokai Pro" -ForegroundColor Gray
Write-Host "   - One Dark" -ForegroundColor Gray
Write-Host "   - Catppuccin" -ForegroundColor Gray
Write-Host "✓ Page should reload with new theme" -ForegroundColor Cyan
Write-Host "✓ Check cookies - 'app_theme' should be updated" -ForegroundColor Cyan
Write-Host ""

Write-Host "SCENARIO 5: Language Switching" -ForegroundColor Green
Write-Host "✓ Open Language dropdown" -ForegroundColor Cyan
Write-Host "✓ Select different language:" -ForegroundColor Cyan
Write-Host "   - English (default)" -ForegroundColor Gray
Write-Host "   - Français" -ForegroundColor Gray
Write-Host "✓ Page should reload" -ForegroundColor Cyan
Write-Host "✓ Check cookies - 'app_lang' should be updated" -ForegroundColor Cyan
Write-Host ""

Write-Host "SCENARIO 6: Health Check" -ForegroundColor Green
Write-Host "✓ Run: curl http://localhost:9999/health" -ForegroundColor Cyan
Write-Host "✓ Should return 'OK'" -ForegroundColor Cyan
Write-Host ""

Write-Host "SCENARIO 7: HTMX Features" -ForegroundColor Green
Write-Host "✓ Test theme switching (HTMX request should update notice)" -ForegroundColor Cyan
Write-Host "✓ Test language switching (HTMX request should update notice)" -ForegroundColor Cyan
Write-Host "✓ Click logout button (HTMX POST request)" -ForegroundColor Cyan
Write-Host "✓ Should redirect to home page" -ForegroundColor Cyan
Write-Host ""

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  HELPER COMMANDS" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Check server status:" -ForegroundColor Yellow
Write-Host "  curl http://localhost:9999/health" -ForegroundColor Cyan
Write-Host ""

Write-Host "View all cookies:" -ForegroundColor Yellow
Write-Host "  Open DevTools > Application > Cookies" -ForegroundColor Cyan
Write-Host ""

Write-Host "Clear all cookies:" -ForegroundColor Yellow
Write-Host "  Open DevTools > Application > Cookies > Right-click > Clear" -ForegroundColor Cyan
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
Write-Host "  - Check if port 9999 is already in use" -ForegroundColor Cyan
Write-Host "  - Kill process using port 9999" -ForegroundColor Cyan
Write-Host "  - Try different port: .\start-dev.ps1 -Port 8080" -ForegroundColor Cyan
Write-Host ""

Write-Host "Build errors:" -ForegroundColor Yellow
Write-Host "  - Run: mvn clean compile" -ForegroundColor Cyan
Write-Host "  - Check Java version: java -version (should be 21+)" -ForegroundColor Cyan
Write-Host ""

Write-Host "Tests not running:" -ForegroundColor Yellow
Write-Host "  - Run: .\run-tests.ps1 -Test all -SkipFlyway" -ForegroundColor Cyan
Write-Host "  - Or: mvn test -Dflyway.skip=true" -ForegroundColor Cyan
Write-Host ""

Write-Host "401 Unauthorized:" -ForegroundColor Yellow
Write-Host "  - Check if token cookie is set" -ForegroundColor Cyan
Write-Host "  - Clear cookies and login again" -ForegroundColor Cyan
Write-Host ""

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  NEXT STEPS" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "1. Run automated tests:" -ForegroundColor Yellow
Write-Host "   .\run-tests.ps1 -Test all" -ForegroundColor Cyan
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
