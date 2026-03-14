# dev.ps1 - start BookTower in development mode
#
# JTE templates reload automatically on every request (DirectoryCodeResolver).
# Kotlin/Java source changes require a restart - Ctrl+C then run this again.

Set-Location $PSScriptRoot

Write-Host 'Building BookTower...' -ForegroundColor Cyan
mvn compile -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ''
Write-Host 'Starting BookTower on http://localhost:9999' -ForegroundColor Green
Write-Host '  Dev login -> username: dev  password: dev12345' -ForegroundColor Yellow
Write-Host '  JTE templates hot-reload on every request (no restart needed)' -ForegroundColor Yellow
Write-Host '  Press Ctrl+C to stop'
Write-Host ''

mvn exec:java -q
