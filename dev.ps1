# dev.ps1 – start BookTower in development mode
#
# Kills any Java instance already running on port 9999, rebuilds, then starts
# the server in the foreground so output is visible immediately.
# Press Ctrl+C to stop. Run again to restart.

Set-Location $PSScriptRoot

$Port = 9999
$Url  = "http://localhost:$Port"

# ── Kill any existing instance on the port ────────────────────────────────────
# Find ALL processes listening on the port (catches both mvn and java)
$conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($conn) {
    @($conn | Select-Object -ExpandProperty OwningProcess -Unique) | ForEach-Object {
        Write-Host "Stopping process on port $Port (PID $_)..." -ForegroundColor Yellow
        Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
}

# Also kill any orphaned java processes running BookTower
Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object {
    try {
        $cmdline = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)").CommandLine
        $cmdline -match "booktower|BookTowerApp"
    } catch { $false }
} | ForEach-Object {
    Write-Host "Stopping orphaned BookTower process (PID $($_.Id))..." -ForegroundColor Yellow
    Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
}

# ── Clean stale JTE caches ────────────────────────────────────────────────────
if (Test-Path "target\jte-dynamic") {
    Remove-Item -Recurse -Force "target\jte-dynamic" 2>$null
    Write-Host "Cleared JTE dynamic cache" -ForegroundColor DarkGray
}

# ── Build ─────────────────────────────────────────────────────────────────────
Write-Host "Building..." -ForegroundColor Cyan
mvn compile -q "-Dflyway.skip=true"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed." -ForegroundColor Red
    exit 1
}

# ── Ensure data dirs exist ────────────────────────────────────────────────────
@("data\books", "data\covers", "data\temp") | ForEach-Object {
    if (-not (Test-Path $_)) { New-Item -ItemType Directory -Path $_ -Force | Out-Null }
}

# ── Start server (foreground — output visible immediately) ────────────────────
Write-Host ""
Write-Host "Starting BookTower at $Url" -ForegroundColor Green
Write-Host "  Username: dev  |  Password: dev12345" -ForegroundColor Gray
Write-Host "  Press Ctrl+C to stop." -ForegroundColor Gray
Write-Host ""

mvn exec:java "-Dexec.mainClass=org.booktower.BookTowerAppKt" "-Dflyway.skip=true"
