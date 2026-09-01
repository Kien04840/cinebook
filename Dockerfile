# ==========================================
# Stage 1: Build Java Application
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper & POM
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Pre-fetch dependencies
RUN ./mvnw dependency:go-offline -B

# Copy application source code
COPY src ./src

# Package application jar (skipping unit tests in Docker build as CI handles testing)
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# Stage 2: Runtime Container
# ==========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root system user for security
RUN addgroup -S cinebook && adduser -S cinebook -G cinebook
USER cinebook:cinebook

# Copy compiled jar artifact from builder
COPY --from=builder /app/target/*.jar app.jar

# Configuration environment variables with sane defaults
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

