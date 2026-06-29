# План (Задача 2): Локальный запуск content-service и работа с БД

Цель: подготовить новый сервис `content-service` к локальному запуску и работе с PostgreSQL.
Сервис должен использовать **отдельную схему** и не обращаться к таблицам `user-service`.

## Зафиксированные решения
- **Изоляция:** отдельная схема `content` в той же БД `advent_academy` (без правок docker-compose/env).
- **Тесты:** профиль `test` на H2 in-memory, Flyway отключён.

## Текущее состояние (что уже сделано)
| Требование | Статус | Где |
|---|---|---|
| Spring Boot 3.4.1 + Java 21 | ✅ | `content-service/build.gradle` (toolchain 21) |
| PostgreSQL (driver + datasource) | ✅ | `build.gradle`, `application-local/docker.yml` |
| Spring Data JPA | ✅ | `build.gradle` |
| Flyway (deps + миграция) | ⚠️ есть, но с багом | `V1__init_content.sql` |
| Профиль `local` | ✅ | `application-local.yml` |
| Профиль `docker` | ✅ | `application-docker.yml` |
| Dockerfile | ✅ | `content-service/Dockerfile` |
| docker-compose | ✅ | сервис `content-service`, порт 8081, depends_on postgres |
| Healthcheck (Actuator) | ✅ | `application.yml` + healthcheck в compose |
| Изоляция схемы `content` | ❗ нет | данные сейчас в `public` рядом с user-service |
| Профиль `test` | ❗ нет | каталога `src/test` нет |
| Тест загрузки контекста | ❗ нет | — |

---

## Шаг 1. Ветка
```bash
git add PLAN_content_module.md && git commit -m "docs: add content module plan"
git checkout dev && git pull origin dev
git checkout -b feature/content-service-db-setup
```
(Либо продолжить в текущей `feature/contentService` — каркас уже там.)

## Шаг 2. Изоляция схемы `content`
Файл: `content-service/src/main/resources/application.yml`. Добавить в блок `spring`:
```yaml
  jpa:
    properties:
      hibernate:
        default_schema: content
  flyway:
    schemas: content
    default-schema: content
    create-schemas: true
    table: flyway_schema_history_content   # уже есть
    locations: classpath:db/migration       # уже есть
```
→ Flyway сам создаёт схему `content`, все таблицы и история — в ней. Таблицы user-service в `public` не затрагиваются.

## Шаг 3. Починить миграцию
Файл: `content-service/src/main/resources/db/migration/V1__init_content.sql`
- Убрать лишний `c` в конце файла (ломает Flyway).
- Выровнять DDL. Схема придёт из `flyway.default-schema`, явный префикс не обязателен.

## Шаг 4. Профиль `test`
Новый файл: `content-service/src/test/resources/application-test.yml`
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
  flyway:
    enabled: false
logging:
  level:
    com.adventacademy: INFO
```

## Шаг 5. Тест загрузки контекста
Новый файл: `content-service/src/test/java/com/adventacademy/contentservice/ContentServiceApplicationTests.java`
```java
package com.adventacademy.contentservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ContentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

## Шаг 6. Проверки (критерии готовности)
```bash
./gradlew :content-service:build                                   # сборка + тест контекста зелёные
./gradlew :content-service:bootRun --args='--spring.profiles.active=local'
curl http://localhost:8081/actuator/health                         # status: UP
# в psql: \dn покажет схему content; \dt content.* покажет courses + flyway_schema_history_content
docker compose up --build postgres content-service                 # старт через Docker, healthcheck OK
```

---

## Соответствие требованиям задачи
| Критерий результата | Закрывается шагом |
|---|---|
| Приложение собирается и запускается | 6 (`build`, `bootRun`) |
| Подключение к PostgreSQL работает | 2, 6 |
| Миграции выполняются автоматически | 2, 3 |
| `/actuator/health` → UP | уже настроено + 6 |
| Запуск локально и через Docker | 6 |
| Тест загрузки Spring-контекста | 4, 5 |
| Отдельная схема, без таблиц user-service | 2 |
