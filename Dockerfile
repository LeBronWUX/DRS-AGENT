# ===========================================
# DRS Agent Backend Dockerfile
# Multi-stage build for Spring Boot Application
# ===========================================

# Stage 1: Build stage
FROM eclipse-temurin:17-jdk-alpine as build

WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S drsapp && adduser -S drsapp -G drsapp

# Copy the JAR file from build stage
COPY --from=build /app/target/drs-agent-backend-*.jar app.jar

# Change ownership to non-root user
RUN chown -drsapp:drsapp app.jar

# Switch to non-root user
USER drsapp

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]