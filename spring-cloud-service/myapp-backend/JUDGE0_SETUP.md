# Настройка Judge0 для выполнения кода

## Проблема
`api.judge0.com` недоступен или требует платную подписку.

## Решение
Используем локальный Judge0 в Docker.

## Шаги настройки

### 1. Запуск Docker Compose

```bash
cd /Users/galina/Desktop/Pre-ProjectJava/spring-cloud-service/myapp-backend
docker-compose up -d
```

Это запустит:
- PostgreSQL для основного приложения (порт 5432)
- PostgreSQL для Judge0 (внутренний)
- Redis для Judge0 (порт 6379)
- Judge0 API (порт 2358)

### 2. Проверка работы Judge0

```bash
# Проверить что контейнеры запущены
docker ps

# Проверить логи Judge0
docker logs myapp-judge0

# Тестовый запрос к Judge0
curl -X POST http://localhost:2358/submissions?base64_encoded=false&wait=true \
  -H "Content-Type: application/json" \
  -d '{
    "source_code": "print(\"Hello World\")",
    "language_id": 71,
    "stdin": ""
  }'
```

### 3. Language IDs для Judge0

Основные языки программирования:

| Язык       | Language ID |
|------------|-------------|
| Java       | 62          |
| Python 3   | 71          |
| JavaScript | 63          |
| C++        | 54          |
| C          | 50          |
| C#         | 51          |
| Go         | 60          |
| Rust       | 73          |
| Ruby       | 72          |
| PHP        | 68          |

## Альтернативные варианты

### Вариант 1: Использовать Judge0 CE (Community Edition)
```yaml
judge0:
  image: judge0/judge0:latest
  # ... конфигурация
```

### Вариант 2: RapidAPI Judge0
1. Зарегистрироваться на https://rapidapi.com/judge0-official/api/judge0-ce
2. Получить API ключ
3. Обновить `application.properties`:
```properties
judge0.url=https://judge0-ce.p.rapidapi.com
judge0.api-key=ваш-api-ключ
```

### Вариант 3: Другие альтернативы
- **Piston API**: https://github.com/engineer-man/piston
- **Glot.io API**: https://glot.io/api
- **CodeJail**: https://github.com/openedx/codejail

## Настройка DNS (если нужно)

### macOS/Linux
Добавить в `/etc/hosts`:
```
127.0.0.1 judge0.local
```

Затем в `application.properties`:
```properties
judge0.url=http://judge0.local:2358
```

### Windows
Добавить в `C:\Windows\System32\drivers\etc\hosts`:
```
127.0.0.1 judge0.local
```

## Troubleshooting

### Judge0 не запускается
```bash
# Проверить логи всех сервисов
docker-compose logs

# Перезапустить
docker-compose down
docker-compose up -d
```

### Ошибка подключения к PostgreSQL для Judge0
```bash
# Проверить что БД запущена
docker exec -it judge0-postgres psql -U judge0 -d judge0
```

### Таймаут при выполнении кода
Увеличить timeout в `application.properties`:
```properties
judge0.timeout=60
```

## Новые возможности

### 1. Сохранение кода в БД
Все попытки решения автоматически сохраняются в таблице `submissions`.

### 2. История попыток
```http
GET /api/tasks/{taskId}/submissions?page=0&size=10
GET /api/tasks/submissions/my?page=0&size=10
```

### 3. Статистика по задачам
```http
GET /api/tasks/{taskId}/stats
```

Возвращает:
```json
{
  "totalAttempts": 5,
  "successfulAttempts": 2,
  "solved": true
}
```

## Структура Submission

```java
{
  "id": 1,
  "sourceCode": "...",
  "languageId": 62,
  "status": "OK", // OK, FAIL, ERROR
  "message": "✅ Все тесты пройдены!",
  "passedTests": 3,
  "totalTests": 3,
  "executionDetails": "Test 1: PASSED\nTest 2: PASSED\nTest 3: PASSED",
  "createdAt": "2026-02-10T12:00:00"
}
```

## Тестирование

### Пример запроса на проверку решения
```http
POST http://localhost:8050/api/tasks/1/submit
Content-Type: application/json
Authorization: Bearer your-jwt-token

{
  "sourceCode": "public class Solution { public static void main(String[] args) { System.out.println(\"Hello World\"); } }",
  "languageId": 62
}
```

### Пример ответа
```json
{
  "passedTests": 3,
  "totalTests": 3,
  "allPassed": true,
  "message": "✅ Все тесты пройдены!",
  "executionDetails": "Test 1: PASSED\nTest 2: PASSED\nTest 3: PASSED"
}
```
