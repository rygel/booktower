# GitHub Actions Workflows

This directory contains CI/CD workflows for the Runary project, all using Maven for build and test operations.

## Workflows

### test.yml
Main CI/CD pipeline that runs on every push and pull request to main/develop branches.

**Jobs:**
- **build**: Compiles, runs tests, and performs code quality checks
- **e2e-test**: Runs end-to-end tests against the running application
- **release**: Creates GitHub releases when tags are pushed

**Workflow Triggers:**
- Push to `main` or `develop` branches
- Pull requests to `main` or `develop` branches
- Manual workflow dispatch

### code-quality.yml
Runs code quality checks independently on every push.

**Jobs:**
- **detekt**: Kotlin static analysis
- **checkstyle**: Java code style checking
- **pmd**: Java code analysis
- **spotbugs**: Bytecode analysis for bugs
- **coverage**: JaCoCo code coverage reports
- **all-quality-checks**: Aggregates all quality checks

## Running Locally

To run workflows locally:

1. **Build and test:**
   ```bash
   mvn clean test
   ```

2. **Run code quality checks:**
   ```bash
   mvn detekt:check
   mvn checkstyle:check
   mvn pmd:check
   mvn spotbugs:check
   mvn test jacoco:report
   ```

3. **Run E2E tests:**
   ```bash
   ./e2e-test.sh          # Linux/Mac
   e2e-test.bat           # Windows
   ```

4. **Run full test suite:**
   ```bash
   mvn test -Dtest=EndToEndTest
   ```

## Artifacts

Workflows upload the following artifacts:
- Test results (JUnit Surefire reports)
- Code quality reports (Detekt, Checkstyle, PMD, SpotBugs)
- Code coverage reports (JaCoCo)
- E2E test results and logs

## Maven Goals Used

All workflows use standard Maven goals:
- `clean` - Clean build directory
- `compile` - Compile source code
- `test` - Run JUnit tests
- `checkstyle:check` - Run Checkstyle
- `detekt:check` - Run Detekt
- `pmd:check` - Run PMD
- `spotbugs:check` - Run SpotBugs
- `jacoco:report` - Generate coverage report
- `surefire-report:report` - Generate test report

## Configuration

Workflows use the following Maven configuration from `pom.xml`:
- Java 21 (via actions/setup-java@v4)
- Maven caching (via actions/cache@v4)
- All code quality plugins configured in POM

## Secrets

If you add GPG signing for releases, configure:
- `GPG_PASSPHRASE` - GPG passphrase secret
