# BookTower PowerShell Scripts - Fixed and Tested

## Overview
All PowerShell scripts have been fixed, tested, and verified to work correctly.

## Scripts

### 1. start-server.ps1 (2.8K)
**Purpose**: Quick start server (assumes project is already compiled)

**Features**:
- Stops any existing BookTower instances before starting
- Maven installation check
- Java installation check
- Starts server on http://localhost:9999 (default)
- Optional browser opening with `-OpenBrowser` flag
- Clear error handling and status messages

**Usage**:
```powershell
# Start server
.\start-server.ps1

# Start server and open browser
.\start-server.ps1 -OpenBrowser

# Start server on custom port
.\start-server.ps1 -Port 8080
```

### 2. start-dev.ps1 (4.5K)
**Purpose**: Development mode - compile and start server

**Features**:
- Stops any existing BookTower instances before starting
- Maven installation check
- Java installation check
- Optional project clean with `-Clean` flag
- Full compilation with `mvn clean compile`
- Data directory creation (data/books, data/covers, data/temp)
- Optional skip build with `-SkipBuild` flag
- Server startup on http://localhost:9999 (default)
- Optional browser opening

**Usage**:
```powershell
# Full build and start
.\start-dev.ps1

# Build and start with browser
.\start-dev.ps1 -OpenBrowser

# Clean, build, and start
.\start-dev.ps1 -Clean -OpenBrowser

# Skip build and start (if already compiled)
.\start-dev.ps1 -SkipBuild

# Custom port
.\start-dev.ps1 -Port 8080
```

### 3. start-app.ps1 (4.5K)
**Purpose**: Application startup - similar to start-dev.ps1

**Features**:
- Same as start-dev.ps1
- Stops existing instances
- Checks Maven and Java
- Optional clean and build skip
- Data directory creation
- Browser opening support

**Usage**:
```powershell
# Start application
.\start-app.ps1

# Clean build and start
.\start-app.ps1 -Clean -OpenBrowser

# Skip build
.\start-app.ps1 -SkipBuild
```

### 4. stop.ps1 (3.7K)
**Purpose**: Stop all running BookTower instances

**Features**:
- Find all Java processes with "BookTower" in command line
- Graceful stop with Stop-Process
- Verification of stopped processes
- Force kill with taskkill if processes persist
- Optional force mode with `-Force` flag
- Detailed stop summary
- Color-coded output

**Usage**:
```powershell
# Normal stop
.\stop.ps1

# Force stop (for stubborn processes)
.\stop.ps1 -Force
```

### 5. run-tests.ps1 (2.0K)
**Purpose**: Run test suites

**Features**:
- Test suite selection (all, htmx, templates, unit)
- Skip Flyway option with `-SkipFlyway` flag
- Verbose output with `-Verbose` flag
- Clear test result reporting
- Error handling

**Test Suites**:
- `all`: Run all tests
- `htmx`: HtmxHandlerTest, TemplateRenderingTest
- `templates`: TemplateRenderingTest
- `unit`: TemplateRenderingTest

**Usage**:
```powershell
# Run all tests
.\run-tests.ps1

# Run specific test suite
.\run-tests.ps1 -Test htmx

# Skip Flyway migrations
.\run-tests.ps1 -Test all -SkipFlyway

# Verbose output
.\run-tests.ps1 -Test all -Verbose
```

### 6. MANUAL_TEST.ps1 (7.6K)
**Purpose**: Interactive testing guide

**Features**:
- Comprehensive test scenarios
- Helper commands
- Troubleshooting section
- Step-by-step testing instructions

**Usage**:
```powershell
# Show testing guide
.\MANUAL_TEST.ps1
```

## Common Features

### Stop-Before-Start
All start scripts (start-server.ps1, start-dev.ps1, start-app.ps1) include:
- Automatic detection of running BookTower instances
- Multi-stage process termination:
  1. Graceful stop with Stop-Process
  2. 3-second wait period
  3. Force kill with taskkill if processes persist
- Clear status reporting
- Error handling and fallback mechanisms

### Process Detection
```powershell
# Find all Java processes with BookTower in command line
Get-Process -Name java | Where-Object { $_.CommandLine -match "BookTower" }
```

### Force Kill
```powershell
# Force kill with taskkill (last resort)
taskkill /F /PID <process_id>
```

## Maven Configuration

### .mvn/daemon.properties
Maven daemon configuration for faster builds:
- Daemon enabled
- JVM optimization settings
- 3-hour idle timeout
- Parallel thread configuration
- Custom port for daemon communication

### .mvn/maven.config
Removed due to parsing issues. Use daemon.properties instead.

## Testing Results

All scripts have been tested and verified:
- ✅ start-server.ps1: PASSED
- ✅ start-dev.ps1: PASSED
- ✅ start-app.ps1: PASSED
- ✅ stop.ps1: PASSED
- ✅ run-tests.ps1: PASSED
- ✅ MANUAL_TEST.ps1: PASSED

## Error Handling

All scripts include:
- `$ErrorActionPreference = "Stop"` for strict error handling
- Try-catch blocks for critical operations
- Proper exit codes (0 for success, 1 for failure)
- Clear error messages with color coding

## Troubleshooting

### Scripts won't run
```powershell
# Check execution policy
Get-ExecutionPolicy

# If restricted, change it
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### Processes won't stop
```powershell
# Use force mode
.\stop.ps1 -Force

# Or manually stop all Java processes
Get-Process -Name java | Stop-Process -Force
```

### Build errors
```powershell
# Clean and rebuild
mvn clean compile

# Check Java version
java -version

# Check Maven version
mvn --version
```

## Notes

- All scripts use PowerShell 5.1+ syntax
- Scripts are compatible with Windows PowerShell and PowerShell Core
- Color-coded output for better readability
- All scripts have been tested on the current system
- Stop-before-start functionality prevents port conflicts
- Maven daemon configuration improves build performance

## Summary

The PowerShell scripts are now fully functional with:
- ✅ Robust stop-before-start functionality
- ✅ Proper error handling
- ✅ Clear status reporting
- ✅ Multiple fallback mechanisms
- ✅ All scripts tested and working
- ✅ Maven daemon configuration for performance
