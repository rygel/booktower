# BookTower Development Startup Script
# Run again at any time to kill the old server and start fresh.
# Logs go to data\booktower.log.

param(
    [switch]$SkipBuild,
    [switch]$OpenBrowser,
    [switch]$Clean,
    [string]$Port = "9999"
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$PidFile = Join-Path $PSScriptRoot "data\.pid"
$LogFile = Join-Path $PSScriptRoot "data\booktower.log"
$Url     = "http://localhost:$Port"

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  BOOKTOWER DEVELOPMENT SERVER" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

# ── Kill previous instance ────────────────────────────────────────────────────
if (Test-Path $PidFile) {
    $savedPid = (Get-Content $PidFile -Raw).Trim()
    if ($savedPid -match '^\d+$') {
        Write-Host "Stopping previous instance (PID $savedPid)..." -ForegroundColor Yellow
        taskkill /F /T /PID $savedPid 2>$null | Out-Null
        Start-Sleep -Seconds 1
    }
    Remove-Item $PidFile -Force
} else {
    Write-Host "No previous instance found" -ForegroundColor Green
}

Write-Host ""

# ── Prerequisites ──────────────────────────────────────────────────────────────
try { $null = mvn --version 2>&1; if ($LASTEXITCODE -ne 0) { throw } }
catch { Write-Host "Maven not found. Please install Maven first." -ForegroundColor Red; exit 1 }

try { $null = java -version 2>&1 }
catch { Write-Host "Java not found. Please install Java 21+ first." -ForegroundColor Red; exit 1 }

# ── Clean ──────────────────────────────────────────────────────────────────────
if ($Clean) {
    Write-Host "Cleaning project..." -ForegroundColor Yellow
    mvn clean -q
    if ($LASTEXITCODE -ne 0) { Write-Host "Clean failed!" -ForegroundColor Red; exit 1 }
    Write-Host "Clean complete" -ForegroundColor Green
    Write-Host ""
}

# ── Build ──────────────────────────────────────────────────────────────────────
if (-not $SkipBuild) {
    Write-Host "Building BookTower..." -ForegroundColor Cyan
    mvn compile -q -DskipTests
    if ($LASTEXITCODE -ne 0) { Write-Host "Build failed!" -ForegroundColor Red; exit 1 }
    Write-Host "Build successful!" -ForegroundColor Green
    Write-Host ""
}

# ── Data dirs ──────────────────────────────────────────────────────────────────
@("data\books", "data\covers", "data\temp") | ForEach-Object {
    if (-not (Test-Path $_)) { New-Item -ItemType Directory -Path $_ -Force | Out-Null }
}

# Rotate previous log
if (Test-Path $LogFile) { Remove-Item $LogFile -Force }

# ── Start in background (hidden window) ───────────────────────────────────────
Write-Host "Starting BookTower..." -ForegroundColor Cyan

$proc = Start-Process `
    -FilePath "cmd.exe" `
    -ArgumentList "/c mvn exec:java -q >> ""$LogFile"" 2>&1" `
    -WorkingDirectory $PSScriptRoot `
    -WindowStyle Hidden `
    -PassThru

$proc.Id | Out-File -FilePath $PidFile -Encoding ascii -NoNewline

# ── Wait for ready ────────────────────────────────────────────────────────────
Write-Host "Waiting for server" -ForegroundColor Yellow -NoNewline
$ready = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Milliseconds 800
    Write-Host "." -NoNewline -ForegroundColor DarkGray
    try {
        $r = Invoke-WebRequest -Uri "$Url/health" -UseBasicParsing -TimeoutSec 1 -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
}
Write-Host ""

if ($ready) {
    Write-Host ""
    Write-Host "BookTower is running at $Url" -ForegroundColor Green
    Write-Host "  Logs: $LogFile" -ForegroundColor Gray
    Write-Host "  Run this script again to restart." -ForegroundColor Gray

    if ($OpenBrowser) { Start-Process $Url }
} else {
    Write-Host "Server did not respond within 30s." -ForegroundColor Red
    Write-Host "Check logs: $LogFile" -ForegroundColor Yellow
    exit 1
}
