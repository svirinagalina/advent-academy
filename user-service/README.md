# Spring Cloud Microservices Project

Учебный проект по Spring Cloud, демонстрирующий работу микросервисной архитектуры с Eureka Server, Spring Cloud Gateway, балансировкой нагрузки, а также User Service с RBAC.

## Архитектура

Проект состоит из нескольких компонентов:

### 1. Eureka Server (порт 8761)
- Service Discovery сервер
- Регистрирует и отслеживает все микросервисы
- Dashboard доступен по адресу: http://localhost:8761

### 2. Gateway Service (порт 8080)
- API Gateway для маршрутизации запросов
- Кастомный фильтр `HeaderCheckGatewayFilterFactory`:
  - Проверяет наличие заголовка `spring-cloud-course`
  - Если заголовок отсутствует → возвращает 403 Forbidden
  - Если заголовок присутствует → пробрасывает запрос к client-service
- Автоматическая балансировка нагрузки между экземплярами client-service

### 3. Client Service (порты 8081, 8082)
- Два экземпляра одного микросервиса
- REST контроллер с эндпоинтом `/client/test`
- Регистрируются в Eureka под именем `CLIENT-SERVICE`

### 4. User Service / adventAcademy (порт 8080)
- Сервис управления пользователями с RBAC
- JWT аутентификация и авторизация
- Роли и права доступа
- Swagger UI: http://localhost:8080/swagger-ui.html

## Технологии

- Java 21
- Spring Boot 4
- Spring Cloud Netflix Eureka
- Spring Cloud Gateway
- Gradle
- PostgreSQL
- Docker / Docker Compose

## Запуск проекта

### Запуск через Docker Compose (рекомендуется)

```bash
docker-compose up --build
```

Запустит PostgreSQL и User Service автоматически.

### Ручной запуск микросервисов Eureka/Gateway/Client

#### 1. Запустить Eureka Server
```bash
cd eureka-server
./mvnw spring-boot:run
```

#### 2. Запустить Gateway Service
```bash
cd gateway-service
./mvnw spring-boot:run
```

#### 3. Запустить Client Service (оба экземпляра)

**Первый экземпляр (порт 8081):**
```bash
cd client-service
./mvnw spring-boot:run
```

**Второй экземпляр (порт 8082):**
```bash
cd client-service-2
./mvnw spring-boot:run
```

#### 4. Запустить User Service локально
```bash
cd adventAcademy
./gradlew bootRun
```

> Требует локально запущенный PostgreSQL на порту 5432 с базой `advent_academy`.

## Проверка работы Gateway

### Успешный запрос (с заголовком)
```bash
curl -H "spring-cloud-course: test" http://localhost:8080/client/test
```

### Запрос без заголовка (403 Forbidden)
```bash
curl -i http://localhost:8080/client/test
```

## Структура проекта

```
spring-cloud-service/
├── eureka-server/          # Service Discovery
├── gateway-service/        # API Gateway с кастомными фильтрами
├── client-service/         # Микросервис (экземпляр 1, порт 8081)
├── client-service-2/       # Микросервис (экземпляр 2, порт 8082)
├── adventAcademy/          # User Service с RBAC (Gradle, Java 21)
└── docker-compose.yml      # Общий файл для запуска всех сервисов через Docker
```

## Конфигурация

- Eureka Server: не регистрируется сам в себе
- Gateway & Client Services: регистрируются в Eureka на `http://localhost:8761/eureka/`
- User Service: при запуске через Docker использует профиль `docker` (datasource указывает на контейнер postgres)
