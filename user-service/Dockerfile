# Build stage
FROM gradle:8.8-jdk21 AS build
WORKDIR /app

COPY settings.gradle build.gradle ./
COPY user-service/build.gradle user-service/build.gradle
COPY user-service/src user-service/src


RUN gradle :user-service:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/user-service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
