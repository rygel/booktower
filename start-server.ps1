# Quick Start Script - Assumes project is already compiled
# Use start-dev.ps1 for full build and start
 
param(
    [string]$Port = "9999",
    [switch]$OpenBrowser
)
 
$ErrorActionPreference = "Stop"
 
# Kill existing BookTower instances
Write-Host "Checking for existing BookTower instances..." -ForegroundColor Yellow
try {
    $existingProcesses = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match "org.booktower.BookTowerAppKt" }
    if ($existingProcesses) {
        Write-Host "Found $($existingProcesses.Count) existing BookTower instance(s). Stopping..." -ForegroundColor Yellow
        foreach ($process in $existingProcesses) {
            $process.Kill()
            Write-Host "✓ Stopped existing instance (PID: $($process.Id))" -ForegroundColor Green
        }
        Write-Host ""
    } else {
        Write-Host "✓ No existing instances found" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Error checking for existing instances: $_" -ForegroundColor Red
}
 
Write-Host "Starting BookTower server..." -ForegroundColor Green
Write-Host "Server: http://localhost:$Port" -ForegroundColor Cyan
Write-Host ""
 
if ($OpenBrowser) {
    Write-Host "Opening browser..." -ForegroundColor Yellow
    Start-Process "http://localhost:$Port"
    Start-Sleep -Seconds 2
}
 
Write-Host "Running: mvn exec:java -Dexec.mainClass=org.booktower.BookTowerAppKt" -ForegroundColor Gray
try {
    mvn exec:java "-Dexec.mainClass=org.booktower.BookTowerAppKt"
} catch [System.Management.Automation.HaltCommandException] {
    Write-Host "`n✓ Server stopped by user" -ForegroundColor Yellow
} catch {
    Write-Host "`n✗ Server error: $_" -ForegroundColor Red
    Write-Host "`nTrying alternative method..." -ForegroundColor Yellow
    try {
        $env:CLASSPATH = "target/classes"
        java org.booktower.BookTowerAppKt
    } catch {
        Write-Host "`n✗ Alternative method also failed: $_" -ForegroundColor Red
        exit 1
    }
}
