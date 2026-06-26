# ==========================================
# BUILD STAGE
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy POM and download dependencies to optimize caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy code and build JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# RUN STAGE
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy build artifact
COPY --from=build /app/target/attendance-0.0.1-SNAPSHOT.jar app.jar

# Render injects PORT env variable, application binds to it
EXPOSE 8080

# Run Spring Boot app
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
