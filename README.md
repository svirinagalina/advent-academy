# Pre-ProjectJava

Репозиторий содержит учебные проекты по Java и Spring Framework, выполненные в рамках изучения backend разработки.

## 📂 Структура проекта

Репозиторий включает два основных учебных проекта:

---

### 1️⃣ Spring Boot Security Demo (корневая директория)

**Тип проекта:** Веб-приложение с аутентификацией и авторизацией

**Описание:** Демонстрационное приложение, реализующее систему безопасности на базе Spring Security с пользовательской аутентификацией и управлением ролями.

#### Технологический стек:
- **Spring Boot 2.6.2** - основной фреймворк
- **Spring Security** - аутентификация и авторизация
- **Spring Data JPA / Hibernate** - работа с базой данных
- **Thymeleaf** - шаблонизатор для фронтенда
- **MySQL** - база данных
- **Maven** - система сборки
- **Java 11**

#### Основные компоненты:
- `configs/` - конфигурация Spring Security
- `controller/` - REST контроллеры
- `dao/` - Data Access Objects
- `model/` - модели данных (User, Role)
- `service/` - бизнес-логика

#### Функциональность:
- Регистрация и аутентификация пользователей
- Ролевая модель доступа (USER, ADMIN)
- CRUD операции с пользователями
- Защищенные эндпоинты

---

### 2️⃣ Spring Cloud Microservices (папка `spring-cloud-service/`)

**Тип проекта:** Микросервисная архитектура с Service Discovery и API Gateway

**Описание:** Учебный проект по Spring Cloud, демонстрирующий работу микросервисной архитектуры с Eureka Server, Spring Cloud Gateway и балансировкой нагрузки.

#### Архитектура:

##### 🔹 Eureka Server (порт 8761)
- Service Discovery сервер
- Регистрирует и отслеживает все микросервисы
- Dashboard: http://localhost:8761

##### 🔹 Gateway Service (порт 8080)
- API Gateway для маршрутизации запросов
- Кастомный фильтр `HeaderCheckGatewayFilterFactory`:
  - Проверяет наличие заголовка `spring-cloud-course`
  - Если заголовок отсутствует → 403 Forbidden
  - Если заголовок присутствует → пробрасывает запрос к client-service
- Автоматическая балансировка нагрузки

##### 🔹 Client Service (порты 8081, 8082)
- Два экземпляра одного микросервиса
- REST контроллер с эндпоинтом `/client/test`
- Регистрируются в Eureka под именем `CLIENT-SERVICE`

##### 🔹 User Service / adventAcademy (порт 8080)
- Сервис управления пользователями с RBAC
- JWT аутентификация и авторизация
- Java 21, Spring Boot 4, Gradle
- Swagger UI: http://localhost:8080/swagger-ui.html

##### 🔹 Другие микросервисы:
- **book-service** - сервис управления книгами
- **check-book-service** - сервис проверки книг
- **creation-book-service** - сервис создания книг
- **store-book-service** - сервис хранения книг
- **config-server / config-client** - централизованная конфигурация
- **reactive-web-app** - реактивное веб-приложение

#### Технологии:
- **Java 21**
- **Spring Boot**
- **Spring Cloud Netflix Eureka** - Service Discovery
- **Spring Cloud Gateway** - API Gateway
- **Gradle / Maven** - системы сборки
- **PostgreSQL** - база данных для User Service
- **Docker / Docker Compose**

#### Запуск через Docker Compose:
```bash
cd spring-cloud-service
docker-compose up --build
```

#### Запуск микросервисов вручную:
```bash
# 1. Запустить Eureka Server
cd spring-cloud-service/eureka-server
./mvnw spring-boot:run

# 2. Запустить Gateway Service
cd spring-cloud-service/gateway-service
./mvnw spring-boot:run

# 3. Запустить Client Service (оба экземпляра)
cd spring-cloud-service/client-service
./mvnw spring-boot:run

cd spring-cloud-service/client-service-2
./mvnw spring-boot:run

# 4. Запустить User Service
cd spring-cloud-service/adventAcademy
./gradlew bootRun
```

#### Проверка работы:
```bash
# Успешный запрос (с заголовком)
curl -H "spring-cloud-course: test" http://localhost:8080/client/test

# Запрос без заголовка (403 Forbidden)
curl -i http://localhost:8080/client/test
```

Подробное описание архитектуры и функциональности доступно в [spring-cloud-service/README.md](./spring-cloud-service/README.md)

---

## 🎯 Цели проектов

- Изучение Spring Framework и его экосистемы
- Практика работы с Spring Security
- Освоение микросервисной архитектуры
- Изучение паттернов Service Discovery и API Gateway
- Работа с балансировкой нагрузки
- Практика конфигурирования Spring приложений

## 👨‍💻 Автор

**Galina Svirina** ([@svirinagalina](https://github.com/svirinagalina))

---

*Учебные проекты созданы в рамках изучения backend разработки на Java*
