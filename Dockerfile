# ── Build stage ───────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

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
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r booktower && useradd -r -g booktower booktower

# Copy fat jar
COPY --from=build /build/target/booktower-*.jar app.jar

# Data directories (override with volumes in docker-compose)
RUN mkdir -p /data/books /data/covers /data/db && \
    chown -R booktower:booktower /data /app

USER booktower

EXPOSE 8080

ENV BOOKTOWER_ENV=production \
    BOOKTOWER_PORT=8080

ENTRYPOINT ["java", "-Xms128m", "-Xmx512m", "-jar", "app.jar"]
