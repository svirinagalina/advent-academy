# MyApp Backend - Code Execution Platform

Платформа для практики программирования с автоматической проверкой кода через Judge0.

## 🎯 Возможности

- ✅ **Выполнение кода** на 10+ языках программирования через локальный Judge0
- ✅ **Автоматическая проверка** решений с тест-кейсами
- ✅ **История попыток** - все решения сохраняются в БД
- ✅ **Статистика** по задачам и прогрессу
- ✅ **JWT аутентификация** с ролями USER/ADMIN
- ✅ **REST API** для интеграции с фронтендом

## 🚀 Быстрый старт

### 1. Требования
- Docker Desktop
- Java 17+
- Gradle 8+ (или используйте `./gradlew`)

### 2. Запуск
```bash
# Клонировать репозиторий
cd myapp-backend

# Запустить Docker (PostgreSQL + Judge0 + Redis)
docker-compose up -d

# Проверить Judge0
./test-judge0.sh

# Запустить приложение
./gradlew bootRun
```

Приложение запустится на http://localhost:8050

### 3. Тестирование
```bash
# Регистрация
curl -X POST http://localhost:8050/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123","email":"test@test.com"}'

# Получить токен
TOKEN=$(curl -s -X POST http://localhost:8050/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123"}' | jq -r '.token')

# Отправить решение
curl -X POST http://localhost:8050/api/tasks/1/submit \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"sourceCode":"print(\"Hello World\")","languageId":71}'
```

Или используйте `task.http` файл в IntelliJ IDEA для визуального тестирования!

## 📚 Документация

- **[QUICKSTART.md](QUICKSTART.md)** - Пошаговая инструкция запуска
- **[JUDGE0_SETUP.md](JUDGE0_SETUP.md)** - Полная документация по Judge0
- **[CHANGES_SUMMARY.md](CHANGES_SUMMARY.md)** - Список всех изменений

## 🏗️ Архитектура

```
myapp-backend/
├── src/main/java/ru/adventacademy/myappbackend/
│   ├── controller/      # REST API контроллеры
│   │   ├── AuthController.java
│   │   └── TaskController.java
│   ├── service/         # Бизнес-логика
│   │   ├── CodeExecutionService.java   # Работа с Judge0
│   │   ├── TaskService.java
│   │   ├── UserService.java
│   │   └── SubmissionService.java      # История попыток
│   ├── entity/          # JPA сущности
│   │   ├── User.java
│   │   ├── Task.java
│   │   ├── TestCase.java
│   │   └── Submission.java             # История решений
│   ├── dto/             # Data Transfer Objects
│   ├── repository/      # Spring Data JPA
│   └── security/        # JWT + Spring Security
└── docker-compose.yml   # PostgreSQL + Judge0 + Redis
```

## 🔌 API Endpoints

### Аутентификация
- `POST /api/auth/register` - Регистрация
- `POST /api/auth/login` - Вход (получение JWT)

### Задачи
- `GET /api/tasks` - Список задач
- `GET /api/tasks/{id}` - Задача по ID
- `GET /api/tasks/difficulty/{level}` - Задачи по сложности
- `POST /api/tasks` - Создать задачу (ADMIN)
- `DELETE /api/tasks/{id}` - Удалить задачу (ADMIN)

### Проверка кода
- `POST /api/tasks/{id}/submit` - Отправить решение

### История и статистика (NEW!)
- `GET /api/tasks/{taskId}/submissions` - История попыток для задачи
- `GET /api/tasks/submissions/my` - Все мои попытки
- `GET /api/tasks/{taskId}/stats` - Статистика по задаче

## 💾 Модель данных

### Submission
```java
{
  "id": 1,
  "taskId": 1,
  "taskTitle": "Hello World",
  "sourceCode": "print('Hello World')",
  "languageId": 71,
  "status": "OK",              // OK, FAIL, ERROR
  "message": "✅ Все тесты пройдены!",
  "passedTests": 3,
  "totalTests": 3,
  "executionDetails": "...",
  "createdAt": "2026-02-10T12:00:00"
}
```

### Task Statistics
```json
{
  "totalAttempts": 5,
  "successfulAttempts": 2,
  "solved": true
}
```

## 🔧 Конфигурация

### application.properties
```properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/myappdb
spring.datasource.username=myapp
spring.datasource.password=myapp

# Judge0
judge0.url=http://localhost:2358
judge0.timeout=30

# Server
server.port=8050
```

### docker-compose.yml
Включает:
- PostgreSQL (приложение) - порт 5432
- PostgreSQL (Judge0) - внутренний
- Redis - порт 6379
- Judge0 - порт 2358

## 🌍 Поддерживаемые языки

| Язык       | Language ID | Пример                          |
|------------|-------------|----------------------------------|
| Java       | 62          | `System.out.println("Hello");`  |
| Python 3   | 71          | `print("Hello")`                |
| JavaScript | 63          | `console.log("Hello")`          |
| C++        | 54          | `cout << "Hello";`              |
| C          | 50          | `printf("Hello");`              |
| Go         | 60          | `fmt.Println("Hello")`          |

Полный список: https://github.com/judge0/judge0/blob/master/CHANGELOG.md

## 🐛 Troubleshooting

### Judge0 не запускается
```bash
docker logs myapp-judge0
docker-compose restart judge0
```

### PostgreSQL ошибка подключения
```bash
docker exec -it myapp-postgres psql -U myapp -d myappdb
```

### Порт уже занят
```bash
# Найти процесс на порту
lsof -i :8050
lsof -i :5432
lsof -i :2358

# Остановить Docker
docker-compose down
```

### Judge0 возвращает ошибку
1. Проверьте логи: `docker logs myapp-judge0`
2. Проверьте что Redis работает: `docker logs myapp-redis`
3. Перезапустите: `docker-compose restart`
4. Запустите тест: `./test-judge0.sh`

## 📊 Производительность

- Judge0 обрабатывает ~100 запросов/сек
- PostgreSQL с индексами на user_id, task_id, created_at
- Таймаут выполнения кода: 2 секунды (настраивается)
- Лимит памяти: 128MB (настраивается)

## 🔐 Безопасность

- JWT токены с временем жизни 1 час
- Пароли хешируются через BCrypt
- CORS настроен для фронтенда
- Spring Security защищает эндпоинты
- Judge0 изолирован в контейнере

## 🚧 Roadmap

- [ ] WebSocket для real-time обновлений
- [ ] Leaderboard (таблица лидеров)
- [ ] Code review от других пользователей
- [ ] Hints система для задач
- [ ] IDE интеграция
- [ ] Поддержка командных задач
- [ ] Графики прогресса
- [ ] Экспорт решений в GitHub

## 📝 Лицензия

MIT License

## 👥 Авторы

- Backend: Spring Boot + Judge0
- Judge0: https://judge0.com

## 🤝 Contributing

1. Fork репозиторий
2. Создайте feature branch (`git checkout -b feature/amazing`)
3. Commit изменения (`git commit -m 'Add amazing feature'`)
4. Push в branch (`git push origin feature/amazing`)
5. Создайте Pull Request

## ⭐ Поддержка

Если проект был полезен, поставьте звезду ⭐

---

**Важно**: Не забудьте запустить `docker-compose up -d` перед стартом приложения!
