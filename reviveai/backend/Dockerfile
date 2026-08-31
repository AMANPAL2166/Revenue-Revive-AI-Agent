# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies separately from source for faster rebuilds.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Matches <finalName>reviveai-backend</finalName> in pom.xml.
COPY --from=build /app/target/reviveai-backend.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
