# BookTower Stop Script

param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

Write-Host "====================================" -ForegroundColor Red
Write-Host "  STOPPING BOOKTOWER" -ForegroundColor Red
Write-Host "====================================" -ForegroundColor Red
Write-Host ""

Write-Host "This script will stop all running BookTower instances" -ForegroundColor Yellow
Write-Host ""

if ($Force) {
    Write-Host "FORCE MODE ENABLED - Will stop all Java processes" -ForegroundColor Red
    Write-Host ""
}

try {
    $processes = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match "BookTower" }

    if ($processes) {
        Write-Host "Found $($processes.Count) BookTower instance(s)" -ForegroundColor Red
        Write-Host ""

        $stoppedCount = 0

        foreach ($process in $processes) {
            Write-Host "Stopping process (PID: $($process.Id))..." -ForegroundColor Yellow
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            $stoppedCount++

            if ($?) {
                Write-Host "Stopped PID: $($process.Id)" -ForegroundColor Green
            }
        }

        Write-Host ""
        Write-Host "Waiting for processes to terminate..." -ForegroundColor Yellow
        Start-Sleep -Seconds 3

        $stillRunning = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match "BookTower" }
        if ($stillRunning) {
            Write-Host "$($stillRunning.Count) processes still running" -ForegroundColor Yellow
            Write-Host ""

            if ($Force) {
                Write-Host "Using taskkill to force stop..." -ForegroundColor Red
                foreach ($process in $stillRunning) {
                    taskkill /F /PID $process.Id | Out-Null
                    Write-Host "Force killed PID: $($process.Id)" -ForegroundColor Yellow
                }
            } else {
                Write-Host "Run with -Force flag to force stop" -ForegroundColor Yellow
                Write-Host "Example: .\stop.ps1 -Force" -ForegroundColor Gray
            }
        }

        Write-Host ""
        Write-Host "====================================" -ForegroundColor Cyan
        Write-Host "  STOP SUMMARY" -ForegroundColor Cyan
        Write-Host "====================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Total instances found: $($processes.Count)" -ForegroundColor Yellow
        Write-Host "Successfully stopped: $stoppedCount" -ForegroundColor Green
        Write-Host ""

        if ($stoppedCount -eq $processes.Count) {
            Write-Host "All instances stopped successfully!" -ForegroundColor Green
        } else {
            Write-Host "Some instances may still be running" -ForegroundColor Yellow
            exit 1
        }
    } else {
        Write-Host "No BookTower instances running" -ForegroundColor Green
        Write-Host ""
        Write-Host "BookTower is not currently running." -ForegroundColor Gray
    }
}
catch {
    Write-Host "Error stopping instances: $_" -ForegroundColor Red
    Write-Host ""

    if ($Force) {
        Write-Host "Force killing all Java processes..." -ForegroundColor Red
        Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
        Start-Sleep -Seconds 2
        Write-Host ""
        Write-Host "Force stop completed" -ForegroundColor Yellow
    } else {
        Write-Host "Try running with -Force flag" -ForegroundColor Yellow
        Write-Host "Example: .\stop.ps1 -Force" -ForegroundColor Gray
        exit 1
    }
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
