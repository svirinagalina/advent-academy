# Spring Cloud Microservices Project

Учебный проект по Spring Cloud, демонстрирующий работу микросервисной архитектуры с Eureka Server, Spring Cloud Gateway и балансировкой нагрузки.

## Архитектура

Проект состоит из трех компонентов:

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

## Технологии

- Java 11+
- Spring Boot
- Spring Cloud Netflix Eureka
- Spring Cloud Gateway
- Maven

## Запуск проекта

### 1. Запустить Eureka Server
```bash
cd eureka-server
mvn spring-boot:run
```

### 2. Запустить Gateway Service
```bash
cd gateway-service
mvn spring-boot:run
```

### 3. Запустить Client Service (оба экземпляра)

**Первый экземпляр (порт 8081):**
```bash
cd client-service
mvn spring-boot:run
```

**Второй экземпляр (порт 8082):**
```bash
cd client-service-2
mvn spring-boot:run
```

## Проверка работы

### Успешный запрос (с заголовком)
```bash
curl -H "spring-cloud-course: test" http://localhost:8080/client/test
```

Ответ (с балансировкой нагрузки):
```
Response from CLIENT-SERVICE on port 8081
```
или
```
Response from CLIENT-SERVICE on port 8082
```

### Запрос без заголовка (403 Forbidden)
```bash
curl -i http://localhost:8080/client/test
```

Ответ:
```
HTTP/1.1 403 Forbidden
```

## Балансировка нагрузки

Gateway автоматически распределяет запросы между двумя экземплярами client-service (8081 и 8082) используя алгоритм Round Robin через Eureka.

## Структура проекта

```
spring-cloud-course/
├── eureka-server/          # Service Discovery
├── gateway-service/        # API Gateway с кастомными фильтрами
├── client-service/         # Микросервис (экземпляр 1, порт 8081)
└── client-service-2/       # Микросервис (экземпляр 2, порт 8082)
```

## Конфигурация

Все сервисы сконфигурированы через `application.yaml`:
- Eureka Server: не регистрируется сам в себе
- Gateway & Client Services: регистрируются в Eureka на `http://localhost:8761/eureka/`

## Особенности реализации

1. **Кастомный фильтр Gateway** - проверка заголовка на уровне API Gateway
2. **Service Discovery** - динамическое обнаружение сервисов через Eureka
3. **Load Balancing** - автоматическая балансировка через `lb://CLIENT-SERVICE`
4. **Декларативная конфигурация** - роуты настроены через YAML вместо Java конфигурации