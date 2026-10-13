# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Advent Academy" \
      org.opencontainers.image.title="user-service" \
      org.opencontainers.image.description="user-service development image (NOT for production use)" \
      environment="development"

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --chown=appuser:appgroup user-service/build/libs/*.jar app.jar

USER appuser

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

ENTRYPOINT ["java", "-jar", "app.jar"]
