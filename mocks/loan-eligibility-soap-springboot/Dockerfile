# =============================================================================
# Stage 1: Build the application
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# 1. Copy pom.xml and download dependencies first (optimizes Docker layer caching)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 2. Copy source code and build the fat JAR
COPY src ./src
RUN mvn -B -q clean package -DskipTests \
    && cp "$(find target -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /build/app.jar

# =============================================================================
# Stage 2: Minimal runtime image
# =============================================================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built JAR from the build stage (using absolute paths — no glob ambiguity)
COPY --from=build /build/app.jar /app/app.jar

# Run as non-root user for security
USER 65534:65534

EXPOSE 8088

# Healthcheck uses wget (ships with Alpine) — no need for curl or a custom flag
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8088/health || exit 1

# Start the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]