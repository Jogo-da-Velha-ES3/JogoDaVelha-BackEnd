# Build stage
FROM maven:3.9.16-eclipse-temurin-25-alpine AS build

WORKDIR /app

# Copy pom.xml first to leverage Docker cache
COPY pom.xml ./

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/jogodavelha-backend-1.0.0.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
