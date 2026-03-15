# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21-jammy AS build

WORKDIR /build

# Cache dependency downloads as a separate layer
COPY pom.xml .
RUN mvn dependency:go-offline -Denforcer.skip=true -q

COPY src/ src/
RUN mvn package -DskipTests -Denforcer.skip=true -q

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Non-root user for security
RUN groupadd -r booktower && useradd -r -g booktower -s /sbin/nologin booktower

# Data directories (override paths via BOOKTOWER_*_PATH env vars or mount volumes here)
RUN mkdir -p /data/books /data/covers /data/temp \
    && chown -R booktower:booktower /app /data

COPY --from=build --chown=booktower:booktower /build/target/*-fat.jar app.jar

USER booktower

EXPOSE 9999

# Default storage paths — override by mounting volumes at these paths
ENV BOOKTOWER_BOOKS_PATH=/data/books \
    BOOKTOWER_COVERS_PATH=/data/covers \
    BOOKTOWER_TEMP_PATH=/data/temp

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:9999/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
