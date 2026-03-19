# BookTower Development Startup Script (background mode)
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

# ── Kill previous instance (3 strategies) ───────────────────────────────────
$killed = $false

# Strategy 1: Kill by saved PID file
if (Test-Path $PidFile) {
    $savedPid = (Get-Content $PidFile -Raw).Trim()
    if ($savedPid -match '^\d+$') {
        Write-Host "Stopping previous instance (saved PID $savedPid)..." -ForegroundColor Yellow
        taskkill /F /T /PID $savedPid 2>$null | Out-Null
        $killed = $true
    }
    Remove-Item $PidFile -Force
}

# Strategy 2: Kill by port — catches processes the PID file missed
$conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($conn) {
    $pids = $conn | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($pid in $pids) {
        Write-Host "Stopping process on port $Port (PID $pid)..." -ForegroundColor Yellow
        taskkill /F /T /PID $pid 2>$null | Out-Null
        $killed = $true
    }
}

# Strategy 3: Kill orphaned java processes running BookTower
Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object {
    try {
        $cmdline = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)").CommandLine
        $cmdline -match "booktower|BookTowerApp"
    } catch { $false }
} | ForEach-Object {
    Write-Host "Stopping orphaned BookTower Java process (PID $($_.Id))..." -ForegroundColor Yellow
    taskkill /F /T /PID $_.Id 2>$null | Out-Null
    $killed = $true
}

if ($killed) {
    Start-Sleep -Seconds 2
    Write-Host "Previous instance stopped." -ForegroundColor Green
} else {
    Write-Host "No previous instance found." -ForegroundColor Green
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

# ── Clear stale JTE caches ────────────────────────────────────────────────────
if (Test-Path "target\jte-dynamic") {
    Remove-Item -Recurse -Force "target\jte-dynamic" 2>$null
    Write-Host "Cleared JTE dynamic cache" -ForegroundColor DarkGray
}

# ── Build ──────────────────────────────────────────────────────────────────────
if (-not $SkipBuild) {
    Write-Host "Building BookTower..." -ForegroundColor Cyan
    mvn compile -q -DskipTests -Dflyway.skip=true
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

# ── Start in background ───────────────────────────────────────────────────────
Write-Host "Starting BookTower..." -ForegroundColor Cyan

$proc = Start-Process `
    -FilePath "mvn" `
    -ArgumentList "exec:java -q -Dflyway.skip=true" `
    -WorkingDirectory $PSScriptRoot `
    -WindowStyle Hidden `
    -PassThru

# Save the actual process ID
$proc.Id | Out-File -FilePath $PidFile -Encoding ascii -NoNewline

# Wait a moment then find the Java child process and save that PID too
Start-Sleep -Seconds 3
$javaProc = Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object {
    try {
        $cmdline = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)").CommandLine
        $cmdline -match "BookTowerApp"
    } catch { $false }
} | Select-Object -First 1

if ($javaProc) {
    # Overwrite PID file with the Java process ID (the one that holds the port)
    $javaProc.Id | Out-File -FilePath $PidFile -Encoding ascii -NoNewline
}

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
    Write-Host "  Username: dev  |  Password: dev12345" -ForegroundColor Gray
    Write-Host "  Logs: $LogFile" -ForegroundColor Gray
    Write-Host "  Run this script again to restart." -ForegroundColor Gray

    if ($OpenBrowser) { Start-Process $Url }
} else {
    Write-Host "Server did not respond within 30s." -ForegroundColor Red
    Write-Host "Check logs: $LogFile" -ForegroundColor Yellow
    exit 1
}
