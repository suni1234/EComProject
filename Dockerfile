# Stage 1 — Build
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2 — Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy jar from builder stage
COPY --from=builder /app/target/fgw-service-1.0.0.jar app.jar

# Expose FGW port
EXPOSE 8082

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]


