# dev2.ps1 – like dev.ps1 but always kills whatever is on port 9999,
# even if the PID file is missing or the server was started another way.

Set-Location $PSScriptRoot

$PidFile = Join-Path $PSScriptRoot "data\.pid"
$LogFile = Join-Path $PSScriptRoot "data\booktower.log"
$Url     = "http://localhost:9999"

# ── Kill previous instance ────────────────────────────────────────────────────
# 1. Try PID file first (covers normal restarts)
if (Test-Path $PidFile) {
    $savedPid = (Get-Content $PidFile -Raw).Trim()
    if ($savedPid -match '^\d+$') {
        Write-Host "Stopping previous instance (PID $savedPid)..." -ForegroundColor Yellow
        taskkill /F /T /PID $savedPid 2>$null | Out-Null
    }
    Remove-Item $PidFile -Force
}

# 2. Also kill by port — catches anything started outside this script
$conn = Get-NetTCPConnection -LocalPort 9999 -State Listen -ErrorAction SilentlyContinue
if ($conn) {
    $conn | ForEach-Object {
        Write-Host "Killing process on port 9999 (PID $($_.OwningProcess))..." -ForegroundColor Yellow
        taskkill /F /T /PID $_.OwningProcess 2>$null | Out-Null
    }
}

Start-Sleep -Seconds 1

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

# Rotate previous log
if (Test-Path $LogFile) { Remove-Item $LogFile -Force }

# ── Start in background ───────────────────────────────────────────────────────
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
    Write-Host "  Login: dev / dev12345" -ForegroundColor Yellow
    Write-Host "  Logs:  $LogFile" -ForegroundColor Gray
    Write-Host "  Stop:  .\stop.ps1  (or just run .\dev2.ps1 again to restart)" -ForegroundColor Gray
} else {
    Write-Host "Server did not respond within 30s." -ForegroundColor Red
    Write-Host "Check logs: $LogFile" -ForegroundColor Yellow
    exit 1
}
