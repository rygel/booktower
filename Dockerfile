# ── Build stage ───────────────────────────────────────────────────────────────
FROM maven:3.9.12-eclipse-temurin-21 AS build

WORKDIR /build

# Cache dependencies first
COPY pom.xml .
# go-offline pre-warms the dependency cache; || true so a transient repo
# failure (e.g. sonar-maven-plugin not yet in Central) never breaks the build.
RUN mvn dependency:go-offline -q || true

# Copy source and build fat jar
COPY src ./src
RUN mvn package -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21.0.9_10-jre-jammy

WORKDIR /app

# Install curl for healthcheck and create non-root user
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && \
    groupadd -r runary && useradd -r -g runary runary

# Copy fat jar (the -fat.jar variant has the main manifest)
COPY --from=build /build/target/runary-*-fat.jar app.jar

# Data directories (override with volumes in docker-compose)
RUN mkdir -p /data/books /data/covers /data/db && \
    chown -R runary:runary /data /app

USER runary

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

ENV RUNARY_ENV=production \
    RUNARY_PORT=8080

ENTRYPOINT ["java", "-Xms128m", "-Xmx512m", "-jar", "app.jar"]
