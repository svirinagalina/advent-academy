# Build stage
FROM gradle:8.8-jdk21 AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./

COPY platform/build.gradle platform/build.gradle
COPY security-common security-common
COPY user-service/build.gradle user-service/build.gradle
COPY content-service/build.gradle content-service/build.gradle
COPY meeting-service/build.gradle meeting-service/build.gradle
COPY api-gateway/build.gradle api-gateway/build.gradle
COPY content-service/src content-service/src

RUN gradle :content-service:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/content-service/build/libs/*.jar app.jar

EXPOSE 8081

USER spring:spring

ENTRYPOINT ["java", "-jar", "/app/app.jar"]