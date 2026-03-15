# BookTower Documentation Index

This directory contains all project documentation for the BookTower application.

## Getting Started

- [README](README.md) - Main project documentation and overview
- [QUICKSTART](QUICKSTART.md) - Quick start guide for new developers
- [HTTP4K_QUICKSTART](HTTP4K_QUICKSTART.md) - HTTP4K framework quick start

## Architecture

- [BACKEND_ARCHITECTURE](BACKEND_ARCHITECTURE.md) - Backend system architecture
- [FRONTEND_ARCHITECTURE](FRONTEND_ARCHITECTURE.md) - Frontend system architecture
- [ARCHITECTURE_IMPROVEMENTS](ARCHITECTURE_IMPROVEMENTS.md) - Proposed architecture improvements

## Planning & Analysis

- [MVP_REWRITE_PLAN](MVP_REWRITE_PLAN.md) - MVP rewrite implementation plan
- [GREENFIELD_IMPLEMENTATION](GREENFIELD_IMPLEMENTATION.md) - Greenfield implementation guide
- [COMPLETE_REWRITE_ANALYSIS](COMPLETE_REWRITE_ANALYSIS.md) - Complete rewrite analysis
- [REFACTORING_VS_GREENFIELD](REFACTORING_VS_GREENFIELD.md) - Refactoring vs greenfield comparison
- [ANGULAR_TO_HTTP4K_MIGRATION_PLAN](ANGULAR_TO_HTTP4K_MIGRATION_PLAN.md) - Angular to HTTP4K migration plan

## Features & Components

- [FRONTEND_SUMMARY](FRONTEND_SUMMARY.md) - Frontend components summary
- [READER_ALTERNATIVES](READER_ALTERNATIVES.md) - PDF reader alternatives analysis
- [SERVER_DRIVEN_READERS](SERVER_DRIVEN_READERS.md) - Server-driven reader architecture

## Migration & Integration

- [CROSS_CHECK_MIGRATION](CROSS_CHECK_MIGRATION.md) - Cross-check migration plan

## Testing

- [HTMX_TEST_COVERAGE](HTMX_TEST_COVERAGE.md) - HTMX testing coverage and documentation

## Releases & Deployment

- [RELEASES](RELEASES.md) - Release process, fat JAR, AOT native binaries, quickstart mode

## Development

- [AGENTS](AGENTS.md) - Development agents and tools configuration

## Quick Reference

### Scripts (Root Directory)
- `start-server.ps1` - Start the BookTower server
- `start-dev.ps1` - Start in development mode
- `start-app.ps1` - Start the application
- `run-tests.ps1` - Run all tests
- `MANUAL_TEST.ps1` - Manual testing script

### Technology Stack
- **Backend**: Kotlin, http4k, JDBI3
- **Frontend**: HTMX, JTE templates, Tailwind CSS
- **Database**: H2 (in-memory)
- **Testing**: JUnit 5, MockK
- **Build**: Maven

### Key Directories
- `src/main/kotlin` - Main application code
- `src/test/kotlin` - Test code
- `src/main/jte` - JTE templates
- `src/main/resources` - Configuration files
- `data/` - Data storage directory

## Project Status

- ✅ Core application implemented
- ✅ HTMX integration complete
- ✅ Comprehensive test coverage (46 tests)
- ✅ Database migrations working
- ✅ Authentication system functional
- ✅ Theme and language switching implemented
