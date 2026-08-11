# Build stage 1: frontend
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY ui/package.json ui/package-lock.json ./
RUN npm ci
COPY ui/ ./
RUN npm run build

# Build stage 2: backend (static/ populated with the built frontend)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY server/pom.xml .
RUN mvn dependency:go-offline -B
COPY server/src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx350m", "-Xss512k", "-XX:MaxMetaspaceSize=100m", "-jar", "app.jar"]
