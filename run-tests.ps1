# Test Runner Script - Run all tests or specific test suites

param(
    [string]$Test = "all",
    [switch]$SkipFlyway,
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  BookTower Test Runner" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

$skipFlywayArg = if ($SkipFlyway) { "-Dflyway.skip=true" } else { "" }
$verboseArg = if ($Verbose) { "-X" } else { "" }

$testSuites = @{
    "all" = ""
    "htmx" = "HtmxHandlerTest,TemplateRenderingTest"
    "templates" = "TemplateRenderingTest"
    "unit" = "TemplateRenderingTest"
}

if (-not $testSuites.ContainsKey($Test)) {
    Write-Host "Available test suites:" -ForegroundColor Yellow
    foreach ($key in $testSuites.Keys) {
        Write-Host "  * $key" -ForegroundColor Cyan
    }
    Write-Host ""
    Write-Host "Usage: .\run-tests.ps1 -Test <suite> [-SkipFlyway] [-Verbose]" -ForegroundColor Gray
    exit 1
}

$testClass = $testSuites[$Test]

Write-Host "Running test suite: $Test" -ForegroundColor Yellow
Write-Host "Skip Flyway: $SkipFlyway" -ForegroundColor Gray
Write-Host ""

try {
    if ($Test -eq "all") {
        Write-Host "Running all tests..." -ForegroundColor Yellow
        if ($SkipFlyway) {
            mvn test "-Dflyway.skip=true" $verboseArg
        } else {
            mvn test $verboseArg
        }
    } elseif ($testClass) {
        Write-Host "Running: $testClass" -ForegroundColor Yellow
        if ($SkipFlyway) {
            mvn test "-Dflyway.skip=true" "-Dtest=$testClass" $verboseArg
        } else {
            mvn test "-Dtest=$testClass" $verboseArg
        }
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "All tests passed!" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "Some tests failed!" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host ""
    Write-Host "Test error: $_" -ForegroundColor Red
    exit 1
}
