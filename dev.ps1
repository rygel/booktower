# dev.ps1 – start BookTower in development mode
#
# Kills any instance already running on port 9999, builds, then starts
# the server in the foreground so output is visible immediately.
# Press Ctrl+C to stop. Run from a second terminal to restart.

Set-Location $PSScriptRoot

$Port = 9999
$Url  = "http://localhost:$Port"

# ── Kill any existing instance on the port ────────────────────────────────────
$conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($conn) {
    $conn | ForEach-Object {
        Write-Host "Stopping existing instance (PID $($_.OwningProcess))..." -ForegroundColor Yellow
        taskkill /F /T /PID $_.OwningProcess 2>$null | Out-Null
    }
    Start-Sleep -Seconds 1
}

# ── Build ─────────────────────────────────────────────────────────────────────
Write-Host "Building..." -ForegroundColor Cyan
mvn compile -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed." -ForegroundColor Red
    exit 1
}

# ── Ensure data dirs exist ────────────────────────────────────────────────────
@("data\books", "data\covers") | ForEach-Object {
    if (-not (Test-Path $_)) { New-Item -ItemType Directory -Path $_ -Force | Out-Null }
}

# ── Start server (foreground — output visible immediately) ────────────────────
Write-Host ""
Write-Host "Starting BookTower at $Url" -ForegroundColor Green
Write-Host "Press Ctrl+C to stop." -ForegroundColor Gray
Write-Host ""

mvn exec:java "-Dexec.mainClass=org.booktower.BookTowerAppKt"
