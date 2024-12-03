FROM maven:3.9-eclipse-temurin-11-alpine AS builder

WORKDIR /build

# Copy entire project
COPY . .

# Build all modules
RUN mvn clean install -DskipTests

# Runtime stage
FROM eclipse-temurin:11-jre
WORKDIR /app

# Add non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Copy the entire project to scan
COPY --from=builder /build /app/build

# Copy Maven dependencies to include them in scan
COPY --from=builder /root/.m2 /app/.m2

RUN chown -R appuser:appgroup /app

USER appuser