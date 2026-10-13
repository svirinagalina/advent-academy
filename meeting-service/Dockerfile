# Build stage
FROM gradle:9.3.0-jdk21 AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
COPY config config
COPY platform/build.gradle platform/build.gradle
COPY user-service/build.gradle user-service/build.gradle
COPY content-service/build.gradle content-service/build.gradle
COPY api-gateway/build.gradle api-gateway/build.gradle
COPY meeting-service/build.gradle meeting-service/build.gradle
COPY api-gateway/build.gradle api-gateway/build.gradle
COPY meeting-service/src meeting-service/src

RUN gradle :meeting-service:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/meeting-service/build/libs/*.jar app.jar

EXPOSE 8084

USER spring:spring

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
