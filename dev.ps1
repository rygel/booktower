# dev.ps1 – start BookTower in development mode
#
# Kills any Java instance already running on port 9999, rebuilds, then starts
# the server in the foreground so output is visible immediately.
# Press Ctrl+C to stop. Run again to restart.

Set-Location $PSScriptRoot

$Port = 9999
$Url  = "http://localhost:$Port"

Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  BOOKTOWER DEV SERVER" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# ── Step 1: Kill previous instances ──────────────────────────────────────────
Write-Host "[1/5] Checking for running instances on port $Port..." -ForegroundColor White

$conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($conn) {
    @($conn | Select-Object -ExpandProperty OwningProcess -Unique) | ForEach-Object {
        $procName = (Get-Process -Id $_ -ErrorAction SilentlyContinue).ProcessName
        Write-Host "  Killing $procName (PID $_) on port $Port" -ForegroundColor Yellow
        Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
    Write-Host "  Previous instance stopped." -ForegroundColor Green
} else {
    Write-Host "  Port $Port is free." -ForegroundColor DarkGray
}

# Check for orphaned java processes
$orphans = Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object {
    try {
        $cmdline = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)").CommandLine
        $cmdline -match "booktower|BookTowerApp"
    } catch { $false }
}
if ($orphans) {
    $orphans | ForEach-Object {
        Write-Host "  Killing orphaned BookTower java process (PID $($_.Id))" -ForegroundColor Yellow
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    }
}

# ── Step 2: Clean JTE cache ──────────────────────────────────────────────────
Write-Host ""
Write-Host "[2/5] Clearing template cache..." -ForegroundColor White

if (Test-Path "target\jte-dynamic") {
    Remove-Item -Recurse -Force "target\jte-dynamic" 2>$null
    Write-Host "  Deleted target\jte-dynamic\" -ForegroundColor DarkGray
} else {
    Write-Host "  No stale cache found." -ForegroundColor DarkGray
}

# ── Step 3: Compile ──────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[3/5] Compiling Kotlin + JTE templates..." -ForegroundColor White

$sw = [System.Diagnostics.Stopwatch]::StartNew()
mvn compile "-Dflyway.skip=true" 2>&1 | ForEach-Object {
    $line = $_
    # Show key Maven phases, skip noise
    if ($line -match '^\[INFO\] --- (\S+)') {
        Write-Host "  > $($Matches[1])" -ForegroundColor DarkCyan
    } elseif ($line -match '^\[ERROR\]') {
        Write-Host "  $line" -ForegroundColor Red
    } elseif ($line -match '^\[WARNING\]') {
        Write-Host "  $line" -ForegroundColor DarkYellow
    }
}
$sw.Stop()

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "  BUILD FAILED" -ForegroundColor Red
    Write-Host "  Run 'mvn compile' manually to see full output." -ForegroundColor Gray
    exit 1
}
$elapsed = [math]::Round($sw.Elapsed.TotalSeconds, 1)
Write-Host "  Build OK (${elapsed}s)" -ForegroundColor Green

# ── Step 4: Ensure data directories ──────────────────────────────────────────
Write-Host ""
Write-Host "[4/5] Checking data directories..." -ForegroundColor White

@("data\books", "data\covers", "data\temp") | ForEach-Object {
    if (-not (Test-Path $_)) {
        New-Item -ItemType Directory -Path $_ -Force | Out-Null
        Write-Host "  Created $_" -ForegroundColor DarkGray
    }
}
Write-Host "  OK" -ForegroundColor DarkGray

# ── Step 5: Start server ─────────────────────────────────────────────────────
Write-Host ""
Write-Host "[5/5] Starting server..." -ForegroundColor White
Write-Host ""
Write-Host "  URL:       $Url" -ForegroundColor White
Write-Host "  Username:  dev" -ForegroundColor White
Write-Host "  Password:  dev12345" -ForegroundColor White
Write-Host "  Templates: dynamic (live-reload from src/main/jte/)" -ForegroundColor White
Write-Host "  Database:  H2 file (data/booktower)" -ForegroundColor White
Write-Host ""
Write-Host "  Press Ctrl+C to stop." -ForegroundColor DarkGray
Write-Host "  ---------------------------------------------" -ForegroundColor DarkGray
Write-Host ""

mvn exec:java "-Dexec.mainClass=org.booktower.BookTowerAppKt" "-Dflyway.skip=true"
