# ============================================================
# Root Multi-Stage Dockerfile for AI Farm Strategy Engine Backend
# Runtime: Java 21 (Eclipse Temurin) on Alpine Linux
# ============================================================

# Stage 1: Build JAR using Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Cache dependencies
COPY backend/pom.xml ./backend/
WORKDIR /workspace/backend
RUN mvn dependency:go-offline -B

# Copy source code and package application
COPY backend/src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal, secure runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-privileged system user for container security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built artifact from builder stage
COPY --from=builder /workspace/backend/target/*.jar app.jar

# Render injects PORT dynamically (typically 10000)
ENV PORT=8080
EXPOSE 8080

# Tune JVM for Render 512MB RAM free-tier allocation
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
