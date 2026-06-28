# План: создание модуля Content (`content-service`)

Цель: добавить в проект отдельный модуль для управления учебным контентом,
по образцу существующего `user-service`. Модуль должен собираться, стартовать,
подключаться к БД и быть готовым к наращиванию функционала.

Везде ниже за основу берём `user-service`. Имя нового модуля — **`content-service`**,
базовый пакет — **`com.adventacademy.contentservice`** (единообразно с `userservice`).

---

## Шаг 1. Создать модуль и подключить его к сборке

1. Создать папку модуля в корне: `content-service/`.
2. В корневом `settings.gradle` добавить модуль:
   ```gradle
   rootProject.name = 'advent-academy'
   include 'user-service'
   include 'content-service'
   ```
3. (Опционально, для единообразия с `user-service`) создать `content-service/settings.gradle`.
   Технически он не нужен — при include из корня вложенный settings игнорируется.

**Проверка:** `./gradlew projects` — в списке должен появиться `:content-service`.

---

## Шаг 2. Базовая конфигурация модуля (Gradle)

Создать `content-service/build.gradle`. Взять за основу `user-service/build.gradle`.
На старте модуля контента НЕ нужны: Security, JWT. Нужны: web, data-jpa,
validation, actuator, postgresql, flyway, openapi, lombok, тесты.

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.adventacademy'
version = '0.0.1-SNAPSHOT'
description = 'Content Service'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

configurations {
    compileOnly { extendsFrom annotationProcessor }
}

repositories { mavenCentral() }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'

    runtimeOnly 'org.postgresql:postgresql'

    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    // Flyway
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'

    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:testcontainers:1.19.3'
    testImplementation 'org.testcontainers:postgresql:1.19.3'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
    testRuntimeOnly 'com.h2database:h2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') { useJUnitPlatform() }
```

**Проверка:** `./gradlew :content-service:dependencies` отрабатывает без ошибок.

---

## Шаг 3. Структура пакетов (гексагональная архитектура)

Создать дерево пакетов под `content-service/src/main/java/com/adventacademy/contentservice/`,
повторяя `user-service` (Security опускаем):

```
adapter/
  config/
  rest/
    controller/
    exception/
application/
  dto/
    request/
    response/
  port/
  service/
domain/
  model/
  repository/
  exception/
infrastructure/
  config/
  persistence/
    entity/
    jpa/
    mapper/
    adapter/
```

Чтобы Git сохранил пустые папки — положить в каждую `.gitkeep` (как `git.keep` в корневом `src`).

---

## Шаг 4. Главный класс приложения

`content-service/src/main/java/com/adventacademy/contentservice/ContentServiceApplication.java`:

```java
package com.adventacademy.contentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ContentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
```

**Проверка:** `./gradlew :content-service:compileJava` проходит.

---

## Шаг 5. Конфигурация приложения и подключение к БД

Создать три файла в `content-service/src/main/resources/` по образцу `user-service`.
Важно: порт сервиса отличается от 8080 (его занимает user-service) — берём **8081**.

### `application.yml`
```yaml
spring:
  application:
    name: content-service
  profiles:
    active: docker
  jpa:
    hibernate:
      ddl-auto: validate          # схему ведёт Flyway, не Hibernate
    show-sql: false
    properties:
      hibernate:
        format_sql: false
        dialect: org.hibernate.dialect.PostgreSQLDialect

server:
  port: 8081

springdoc:
  api-docs: { enabled: true, path: /v3/api-docs }
  swagger-ui: { enabled: true, path: /swagger-ui.html }

management:
  endpoints:
    web:
      exposure:
        include: health, prometheus, info, metrics
  endpoint:
    health:
      show-details: always
      probes: { enabled: true }
  metrics:
    tags:
      application: ${spring.application.name}

logging:
  level:
    com.adventacademy: INFO
```

### `application-local.yml` (запуск из IDE на локальной БД)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/${POSTGRES_DB:advent_academy}
    username: ${POSTGRES_USER:advent_user}
    password: ${POSTGRES_PASSWORD:advent_password}
    driver-class-name: org.postgresql.Driver
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

### `application-docker.yml` (запуск в docker-compose)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/${POSTGRES_DB:advent_academy}
    username: ${POSTGRES_USER:advent_user}
    password: ${POSTGRES_PASSWORD:advent_password}
    driver-class-name: org.postgresql.Driver
```

> Замечание по БД: и user-service, и content-service ходят в одну БД
> `advent_academy`. Чтобы Flyway-миграции двух сервисов не конфликтовали
> в одной таблице истории, у content-service лучше задать отдельную схему
> или отдельную таблицу истории. Самый простой вариант — отдельная
> `flyway.table`:
> ```yaml
> spring:
>   flyway:
>     table: flyway_schema_history_content
> ```
> (либо в дальнейшем — отдельная схема `content` через `spring.flyway.schemas`).

---

## Шаг 6. Первая Flyway-миграция (заглушка схемы контента)

Создать `content-service/src/main/resources/db/migration/V1__init_content.sql`.
На старте достаточно минимальной таблицы, чтобы `ddl-auto: validate` и Flyway
не падали, и было от чего расти. Пример:

```sql
CREATE TABLE IF NOT EXISTS courses (
    id          UUID         PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);
```

(Конкретные сущности учебного контента — курсы/модули/уроки — это уже
следующая задача. Здесь только готовим точку роста.)

---

## Шаг 7. Dockerfile и docker-compose

1. Создать `content-service/Dockerfile` по образцу `user-service/Dockerfile`,
   заменив `user-service` → `content-service` и порт `8080` → `8081`:
   ```dockerfile
   FROM gradle:8.8-jdk21 AS build
   WORKDIR /app
   COPY settings.gradle build.gradle ./
   COPY content-service/build.gradle content-service/build.gradle
   COPY content-service/src content-service/src
   RUN gradle :content-service:bootJar --no-daemon

   FROM eclipse-temurin:21-jre-alpine
   WORKDIR /app
   COPY --from=build /app/content-service/build/libs/*.jar app.jar
   EXPOSE 8081
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```
   > Учти: в build-stage копируется только `content-service/build.gradle`.
   > Если в корневом `settings.gradle` есть `include 'user-service'`, Gradle
   > при include требует наличия модуля. Вариант — в Dockerfile собирать
   > через `gradle :content-service:bootJar` (как в user-service) и
   > при необходимости скопировать оба `build.gradle`, либо собрать образ
   > из готового jar. Проверить локально сборку образа.

2. Добавить сервис в корневой `docker-compose.yml` (по образцу `user-service`):
   ```yaml
     content-service:
       build:
         context: .
         dockerfile: content-service/Dockerfile
       container_name: advent-content-service
       depends_on:
         postgres:
           condition: service_healthy
       environment:
         SPRING_PROFILES_ACTIVE: docker
         POSTGRES_DB: ${POSTGRES_DB}
         POSTGRES_USER: ${POSTGRES_USER}
         POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
       ports:
         - "8081:8081"
       healthcheck:
         test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8081/actuator/health/readiness"]
         interval: 30s
         timeout: 10s
         retries: 3
         start_period: 40s
       networks:
         - advent-network
   ```

---

## Шаг 8. Проверка результата

1. Сборка модуля:
   ```
   ./gradlew :content-service:build
   ```
2. Локальный запуск (нужна поднятая Postgres — `docker compose up postgres`):
   ```
   ./gradlew :content-service:bootRun --args='--spring.profiles.active=local'
   ```
3. Проверить эндпоинты:
   - `http://localhost:8081/actuator/health` → status UP, БД доступна
   - `http://localhost:8081/swagger-ui.html` открывается
4. Полный стек: `docker compose up --build` — поднимаются postgres, user-service (8080),
   content-service (8081).
5. В логах при старте видно, что Flyway применил `V1__init_content`.

---

## Definition of Done

- [ ] `:content-service` виден в `./gradlew projects`
- [ ] Модуль компилируется и собирается (`build` зелёный)
- [ ] Структура пакетов adapter/application/domain/infrastructure создана
- [ ] Приложение стартует на профиле `local` и `docker`
- [ ] `/actuator/health` показывает UP и подключение к Postgres
- [ ] Flyway применяет миграцию без конфликта с user-service
- [ ] content-service добавлен в docker-compose и поднимается вместе со стеком

---

## Подсказки по подводным камням

- **Конфликт Flyway в общей БД** — главный риск. Используй отдельную
  `spring.flyway.table` (или схему), иначе истории миграций двух сервисов
  перемешаются и оба упадут.
- **Порт 8080 занят** user-service — поэтому content-service на 8081.
- **`ddl-auto: validate`** требует, чтобы под каждую `@Entity` уже была таблица
  из миграции. Пока сущностей нет — валидировать нечего, всё ок.
- **`.gitkeep`** в пустых пакетах, иначе Git их не закоммитит.
