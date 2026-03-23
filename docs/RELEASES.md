# Releases

## Cutting a release

Tag the commit you want to ship and push the tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The `release` GitHub Actions workflow triggers automatically, builds all artifacts, and publishes a GitHub Release with them attached. Pre-release tags (anything containing a `-`, e.g. `v1.0.0-rc1`) are marked as pre-releases automatically.

---

## Distribution formats

### Fat JAR

Built by `mvn package`. Produces `target/runary-{version}-fat.jar` — a single file containing the application and all dependencies merged together.

**Requires Java 21+ on the host machine.**

```bash
java -jar runary-1.0.0-fat.jar
```

The regular `runary-{version}.jar` only contains application classes; the fat JAR is what you distribute to end users.

### Native binary

Built by `mvn package -Pnative` using GraalVM. Produces a single platform-specific executable (`runary` on Linux/macOS, `runary.exe` on Windows).

**Requires nothing on the host machine — no JVM, no runtime.**

```bash
./runary          # Linux / macOS
runary.exe        # Windows
```

Native binaries are produced for five targets in CI:

| File | Platform |
|------|----------|
| `runary-linux-x64` | Linux x86-64 |
| `runary-linux-arm64` | Linux ARM64 (Raspberry Pi, AWS Graviton) |
| `runary-macos-x64` | macOS Intel |
| `runary-macos-arm64` | macOS Apple Silicon |
| `runary-windows-x64.exe` | Windows x86-64 |

---

## What AOT compilation does

Normal Java/Kotlin runs on the JVM: bytecode is interpreted and JIT-compiled at runtime, which means slow startup and a ~100 MB JVM footprint.

GraalVM's `native-image` tool performs **ahead-of-time (AOT) compilation** at build time:

1. It traces the entire call graph of the application starting from `main()`.
2. It compiles every reachable class to native machine code.
3. It packages the result as a standalone binary that includes a minimal runtime — no JVM installer needed.

The trade-offs compared to the fat JAR:

| | Fat JAR | Native binary |
|---|---|---|
| Startup time | ~2–4 s | ~50–100 ms |
| Memory (idle) | ~150 MB | ~30–50 MB |
| Build time | ~30 s | ~5–10 min |
| Requires JVM on host | Yes (Java 21) | No |
| Dynamic class loading | Yes | No |
| PDFBox cover extraction | Full | Full (via bundled AWT) |

The build time cost is paid once in CI; end users get the fast binary.

### How reflection and resources are handled

AOT compilation cannot see code that is loaded dynamically at runtime (reflection, `Class.forName`, `ServiceLoader`). These must be declared explicitly:

- `src/main/resources/META-INF/native-image/org.runary/runary/reflect-config.json` — classes accessed via reflection (JTE templates, Jackson DTOs, H2 driver, Logback appenders)
- `src/main/resources/META-INF/native-image/org.runary/runary/resource-config.json` — classpath resources bundled into the binary (i18n files, SQL migrations, static assets, PDFBox fonts)
- `src/main/resources/META-INF/native-image/org.runary/runary/native-image.properties` — build flags

Additionally, the native profile activates the **GraalVM Reachability Metadata Repository** — a community-maintained database of pre-written configs for common libraries (PDFBox, H2, Jackson, Logback, Flyway, HikariCP). This is what makes PDFBox work in the native binary without writing hundreds of lines of config manually.

### JTE templates in native builds

JTE templates are compiled to Kotlin/Java source files during `mvn compile` (the `jte:generate` goal). In the `native` profile, the `jte:precompile` goal is used instead: it writes `.class` files directly into `target/classes`, bypassing the Kotlin compiler. Both approaches result in the same precompiled template classes on the classpath; the native profile simply avoids the extra source-generation round-trip.

---

## Quickstart mode

Set `RUNARY_QUICKSTART=true` to seed a demo user on first boot:

```bash
RUNARY_QUICKSTART=true ./runary-linux-x64
# Username: demo  |  Password: demo1234
# Open: http://localhost:9999
```

The demo user and its starter library are created only once; subsequent starts with the flag set are no-ops.

---

## Production deployment

```bash
export RUNARY_ENV=production
export RUNARY_JWT_SECRET=$(openssl rand -hex 32)
./runary-linux-x64
```

`RUNARY_ENV=production` disables the dev user seed, enables secure cookies, and switches the template engine to precompiled mode.
