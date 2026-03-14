# BookTower Development Startup Script
# This script compiles and starts the BookTower application locally
 
param(
    [switch]$SkipBuild,
    [switch]$OpenBrowser,
    [switch]$Clean,
    [string]$Port = "9999"
)
 
$ErrorActionPreference = "Stop"
 
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  BookTower Development Server" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

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
 
# Check if Maven is installed
Write-Host "Checking Maven installation..." -ForegroundColor Yellow
try {
    $mvnVersion = mvn --version 2>&1 | Select-String "Apache Maven"
    if ($mvnVersion) {
        Write-Host "✓ Maven installed: $($mvnVersion.Line.Trim())" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Maven not found. Please install Maven first." -ForegroundColor Red
    exit 1
}
 
# Check if Java is installed
Write-Host "Checking Java installation..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-String "version"
    if ($javaVersion) {
        Write-Host "✓ Java installed" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Java not found. Please install Java 21+ first." -ForegroundColor Red
    exit 1
}
 
Write-Host ""
 
# Clean if requested
if ($Clean) {
    Write-Host "Cleaning project..." -ForegroundColor Yellow
    mvn clean
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Clean complete" -ForegroundColor Green
    } else {
        Write-Host "✗ Clean failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
}
 
# Build project
if (-not $SkipBuild) {
    Write-Host "Building BookTower..." -ForegroundColor Yellow
    Write-Host "This may take a few minutes on first run..." -ForegroundColor Gray
    Write-Host ""
 
    try {
        mvn clean compile -DskipTests
 
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Build successful!" -ForegroundColor Green
        } else {
            Write-Host "✗ Build failed!" -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host "✗ Build error: $_" -ForegroundColor Red
        exit 1
    }
 
    Write-Host ""
}
 
# Create necessary directories
Write-Host "Creating data directories..." -ForegroundColor Yellow
$dataDirs = @("data/books", "data/covers", "data/temp")
foreach ($dir in $dataDirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Write-Host "✓ Created $dir" -ForegroundColor Green
    }
}
 
Write-Host ""
 
# Start application
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  Starting BookTower Server..." -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""
 
Write-Host "Server will start on:" -ForegroundColor Yellow
Write-Host "  → http://localhost:$Port" -ForegroundColor Green
Write-Host ""
Write-Host "Available endpoints:" -ForegroundColor Yellow
Write-Host "  → Home: http://localhost:$Port/" -ForegroundColor Cyan
Write-Host "  → Login: http://localhost:$Port/login" -ForegroundColor Cyan
Write-Host "  → Register: http://localhost:$Port/register" -ForegroundColor Cyan
Write-Host "  → Health: http://localhost:$Port/health" -ForegroundColor Cyan
Write-Host ""
Write-Host "API Endpoints:" -ForegroundColor Yellow
Write-Host "  → POST /auth/register" -ForegroundColor Cyan
Write-Host "  → POST /auth/login" -ForegroundColor Cyan
Write-Host "  → GET /api/libraries" -ForegroundColor Cyan
Write-Host "  → POST /api/libraries" -ForegroundColor Cyan
Write-Host "  → GET /api/books" -ForegroundColor Cyan
Write-Host "  → POST /api/books" -ForegroundColor Cyan
Write-Host "  → GET /api/recent" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press Ctrl+C to stop the server" -ForegroundColor Gray
Write-Host ""
 
# Open browser if requested
if ($OpenBrowser) {
    Write-Host "Opening browser..." -ForegroundColor Yellow
    Start-Process "http://localhost:$Port"
    Start-Sleep -Seconds 2
}
 
Write-Host ""
 
# Start server
try {
    Write-Host "Running: mvn exec:java -Dexec.mainClass=org.booktower.BookTowerAppKt" -ForegroundColor Gray
    mvn exec:java "-Dexec.mainClass=org.booktower.BookTowerAppKt"
} catch [System.Management.Automation.HaltCommandException] {
    Write-Host "`n✓ Server stopped by user" -ForegroundColor Yellow
} catch {
    Write-Host "`n✗ Server error: $_" -ForegroundColor Red
    Write-Host "`nTrying alternative method..." -ForegroundColor Yellow
    Write-Host "Running: java -cp target/classes org.booktower.BookTowerAppKt" -ForegroundColor Gray
    try {
        $classpath = "target/classes;target/dependency/*"
        $env:CLASSPATH = "target/classes"
        java org.booktower.BookTowerAppKt $Port
    } catch {
        Write-Host "`n✗ Alternative method also failed: $_" -ForegroundColor Red
        exit 1
    }
}
        Write-Host ""
    } else {
        Write-Host "✓ No existing instances found" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Error checking for existing instances: $_" -ForegroundColor Red
}
 
# Check if Maven is installed
Write-Host "Checking Maven installation..." -ForegroundColor Yellow
try {
    $mvnVersion = mvn --version 2>&1 | Select-String "Apache Maven"
    if ($mvnVersion) {
        Write-Host "✓ Maven installed: $($mvnVersion.Line.Trim())" -ForegroundColor Green
     }
}
 
# Check if Java is installed
Write-Host "Checking Java installation..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-String "version"
    if ($javaVersion) {
        Write-Host "✓ Java installed" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Java not found. Please install Java 21+ first." -ForegroundColor Red
    exit 1
}
 
Write-Host ""
 
# Clean if requested
if ($Clean) {
    Write-Host "Cleaning project..." -ForegroundColor Yellow
    mvn clean
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Clean complete" -ForegroundColor Green
    } else {
        Write-Host "✗ Clean failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
}
 
# Build project
if (-not $SkipBuild) {
    Write-Host "Building BookTower..." -ForegroundColor Yellow
    Write-Host "This may take a few minutes on first run..." -ForegroundColor Gray
    Write-Host ""
 
    try {
        mvn clean compile -DskipTests
 
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Build successful!" -ForegroundColor Green
        } else {
            Write-Host "✗ Build failed!" -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host "✗ Build error: $_" -ForegroundColor Red
        exit 1
    }
 
    Write-Host ""
}
 
# Create necessary directories
Write-Host "Creating data directories..." -ForegroundColor Yellow
$dataDirs = @("data/books", "data/covers", "data/temp")
foreach ($dir in $dataDirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Write-Host "✓ Created $dir" -ForegroundColor Green
    }
}

Write-Host ""
 
# Start application
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  Starting BookTower Server..." -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""
 
Write-Host "Server will start on:" -ForegroundColor Yellow
Write-Host "  → http://localhost:$Port" -ForegroundColor Green
Write-Host ""
Write-Host "Available endpoints:" -ForegroundColor Yellow
Write-Host "  → Home: http://localhost:$Port/" -ForegroundColor Cyan
Write-Host "  → Login: http://localhost:$Port/login" -ForegroundColor Cyan
Write-Host "  → Register: http://localhost:$Port/register" -ForegroundColor Cyan
Write-Host "  → Health: http://localhost:$Port/health" -ForegroundColor Cyan
Write-Host ""
Write-Host "API Endpoints:" -ForegroundColor Yellow
Write-Host "  → POST /auth/register" -ForegroundColor Cyan
Write-Host "  → POST /auth/login" -ForegroundColor Cyan
Write-Host "  → GET /api/libraries" -ForegroundColor Cyan
Write-Host "  → POST /api/libraries" -ForegroundColor Cyan
Write-Host "  → GET /api/books" -ForegroundColor Cyan
Write-Host "  → POST /api/books" -ForegroundColor Cyan
Write-Host "  → GET /api/recent" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press Ctrl+C to stop the server" -ForegroundColor Gray
Write-Host ""
 
# Open browser if requested
if ($OpenBrowser) {
    Write-Host "Opening browser..." -ForegroundColor Yellow
    Start-Process "http://localhost:$Port"
    Start-Sleep -Seconds 2
}
 
Write-Host ""
 
# Start server
try {
    Write-Host "Running: mvn exec:java -Dexec.mainClass=org.booktower.BookTowerAppKt" -ForegroundColor Gray
    mvn exec:java "-Dexec.mainClass=org.booktower.BookTowerAppKt"
} catch [System.Management.Automation.HaltCommandException] {
    Write-Host "`n✓ Server stopped by user" -ForegroundColor Yellow
} catch {
    Write-Host "`n✗ Server error: $_" -ForegroundColor Red
    Write-Host "`nTrying alternative method..." -ForegroundColor Yellow
    Write-Host "Running: java -cp target/classes org.booktower.BookTowerAppKt" -ForegroundColor Gray
    try {
        $classpath = "target/classes;target/dependency/*"
        $env:CLASSPATH = "target/classes"
        java org.booktower.BookTowerAppKt $Port
    } catch {
        Write-Host "`n✗ Alternative method also failed: $_" -ForegroundColor Red
        exit 1
    }
}
        }
    } catch {
        Write-Host "✗ Build error: $_" -ForegroundColor Red
        exit 1
    }

# Check if Java is installed
Write-Host "Checking Java installation..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-String "version"
    if ($javaVersion) {
        Write-Host "✓ Java installed" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Java not found. Please install Java 21+ first." -ForegroundColor Red
    exit 1
}

Write-Host ""

# Clean if requested
if ($Clean) {
    Write-Host "Cleaning project..." -ForegroundColor Yellow
    mvn clean
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Clean complete" -ForegroundColor Green
    } else {
        Write-Host "✗ Clean failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
}

# Build project
if (-not $SkipBuild) {
    Write-Host "Building BookTower..." -ForegroundColor Yellow
    Write-Host "This may take a few minutes on first run..." -ForegroundColor Gray
    Write-Host ""

    try {
        mvn clean compile -DskipTests

        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Build successful!" -ForegroundColor Green
        } else {
            Write-Host "✗ Build failed!" -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host "✗ Build error: $_" -ForegroundColor Red
        exit 1
    }

    Write-Host ""
}

# Create necessary directories
Write-Host "Creating data directories..." -ForegroundColor Yellow
$dataDirs = @("data/books", "data/covers", "data/temp")
foreach ($dir in $dataDirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Write-Host "✓ Created $dir" -ForegroundColor Green
    }
}
Write-Host ""

# Start application
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "  Starting BookTower Server..." -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Server will start on:" -ForegroundColor Yellow
Write-Host "  → http://localhost:$Port" -ForegroundColor Green
Write-Host ""
Write-Host "Available endpoints:" -ForegroundColor Yellow
Write-Host "  → Home: http://localhost:$Port/" -ForegroundColor Cyan
Write-Host "  → Login: http://localhost:$Port/login" -ForegroundColor Cyan
Write-Host "  → Register: http://localhost:$Port/register" -ForegroundColor Cyan
Write-Host "  → Health: http://localhost:$Port/health" -ForegroundColor Cyan
Write-Host ""
Write-Host "API Endpoints:" -ForegroundColor Yellow
Write-Host "  → POST /auth/register" -ForegroundColor Cyan
Write-Host "  → POST /auth/login" -ForegroundColor Cyan
Write-Host "  → GET  /api/libraries" -ForegroundColor Cyan
Write-Host "  → POST /api/libraries" -ForegroundColor Cyan
Write-Host "  → GET  /api/books" -ForegroundColor Cyan
Write-Host "  → POST /api/books" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press Ctrl+C to stop the server" -ForegroundColor Gray
Write-Host ""

# Open browser if requested
if ($OpenBrowser) {
    Write-Host "Opening browser..." -ForegroundColor Yellow
    Start-Process "http://localhost:$Port"
    Start-Sleep -Seconds 2
}

# Start the server
try {
    Write-Host "Running: mvn exec:java -Dexec.mainClass=org.booktower.BookTowerAppKt" -ForegroundColor Gray
    mvn exec:java "-Dexec.mainClass=org.booktower.BookTowerAppKt"
} catch [System.Management.Automation.HaltCommandException] {
    Write-Host "`n✓ Server stopped by user" -ForegroundColor Yellow
} catch {
    Write-Host "`n✗ Server error: $_" -ForegroundColor Red
    Write-Host "`nTrying alternative method..." -ForegroundColor Yellow
    Write-Host "Running: java -cp target/classes org.booktower.BookTowerAppKt" -ForegroundColor Gray
    try {
        $classpath = "target/classes;target/dependency/*"
        $env:CLASSPATH = "target/classes"
        java org.booktower.BookTowerAppKt $Port
    } catch {
        Write-Host "`n✗ Alternative method also failed: $_" -ForegroundColor Red
        exit 1
    }
}
