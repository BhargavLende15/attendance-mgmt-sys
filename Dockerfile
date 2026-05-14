# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9.5-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copy pom first for layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy artifact
COPY --from=builder /app/target/attendance-management-system.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

# Expose port 8086 (not 8080/8081-8085)
EXPOSE 8086

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8086/attendance/status || exit 1

# JVM tuning for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
