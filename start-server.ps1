# BookTower Quick Start Script

param(
    [string]$Port = "9999",
    [switch]$OpenBrowser
)

$ErrorActionPreference = "Stop"

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  BOOKTOWER QUICK START" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Checking Maven installation..." -ForegroundColor Yellow
try {
    $mvnResult = mvn --version 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Maven installed" -ForegroundColor Green
    }
}
catch {
    Write-Host "Maven not found. Please install Maven first." -ForegroundColor Red
    exit 1
}

Write-Host "Checking Java installation..." -ForegroundColor Yellow
try {
    $null = java -version 2>&1
    Write-Host "Java installed" -ForegroundColor Green
}
catch {
    Write-Host "Java not found. Please install Java 21+ first." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Stopping any existing BookTower instances..." -ForegroundColor Yellow

try {
    $processes = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match "BookTower" }
    
    if ($processes) {
        Write-Host "Found $($processes.Count) BookTower instance(s)" -ForegroundColor Red
        
        foreach ($process in $processes) {
            Write-Host "Stopping process (PID: $($process.Id))..." -ForegroundColor Yellow
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped PID: $($process.Id)" -ForegroundColor Green
        }
        
        Start-Sleep -Seconds 3
        
        $stillRunning = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match "BookTower" }
        if ($stillRunning) {
            Write-Host "Force killing remaining processes..." -ForegroundColor Yellow
            foreach ($process in $stillRunning) {
                taskkill /F /PID $process.Id | Out-Null
                Write-Host "Force killed PID: $($process.Id)" -ForegroundColor Yellow
            }
        }
    } else {
        Write-Host "No BookTower instances running" -ForegroundColor Green
    }
}
catch {
    Write-Host "Error checking processes: $_" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Starting BookTower server..." -ForegroundColor Green
Write-Host "Server: http://localhost:$Port" -ForegroundColor Cyan
Write-Host ""

if ($OpenBrowser) {
    Write-Host "Opening browser..." -ForegroundColor Yellow
    Start-Process "http://localhost:$Port"
    Start-Sleep -Seconds 2
}

Write-Host "Press Ctrl+C to stop server" -ForegroundColor Gray
Write-Host ""

mvn exec:java -Dexec.mainClass="org.booktower.BookTowerAppKt"
