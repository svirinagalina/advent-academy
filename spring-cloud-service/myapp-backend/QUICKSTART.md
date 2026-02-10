# 🚀 Быстрый старт

## 1. Запуск инфраструктуры (1 минута)

```bash
# Перейти в папку проекта
cd /Users/galina/Desktop/Pre-ProjectJava/spring-cloud-service/myapp-backend

# Запустить Docker Compose (PostgreSQL, Judge0, Redis)
docker-compose up -d

# Проверить что все запустилось
docker ps
```

Должны быть запущены:
- ✅ myapp-postgres (порт 5432)
- ✅ myapp-judge0 (порт 2358)
- ✅ judge0-postgres
- ✅ myapp-redis (порт 6379)

## 2. Проверка Judge0 (30 секунд)

```bash
# Запустить тестовый скрипт
./test-judge0.sh
```

Если всё зелёное ✅ - можно продолжать!

## 3. Запуск приложения (1 минута)

```bash
# Запуск через Gradle
./gradlew bootRun

# Или через IDE (IntelliJ IDEA)
# Открыть MyappBackendApplication.java и нажать Run
```

Приложение запустится на http://localhost:8050

## 4. Тестирование API (2 минуты)

### Регистрация пользователя
```bash
curl -X POST http://localhost:8050/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "test123",
    "email": "test@example.com"
  }'
```

### Вход
```bash
curl -X POST http://localhost:8050/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "test123"
  }'
```

Сохраните полученный токен в переменную:
```bash
TOKEN="ваш_токен_здесь"
```

### Получить список задач
```bash
curl http://localhost:8050/api/tasks \
  -H "Authorization: Bearer $TOKEN"
```

### Отправить решение
```bash
curl -X POST http://localhost:8050/api/tasks/1/submit \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "sourceCode": "print(\"Hello World\")",
    "languageId": 71
  }'
```

### Посмотреть историю попыток
```bash
curl http://localhost:8050/api/tasks/1/submissions \
  -H "Authorization: Bearer $TOKEN"
```

### Получить статистику
```bash
curl http://localhost:8050/api/tasks/1/stats \
  -H "Authorization: Bearer $TOKEN"
```

## 5. HTTP тестирование (IntelliJ IDEA)

Откройте файл `src/main/java/ru/adventacademy/myappbackend/security/task.http`

Выполните запросы прямо из IDE!

## 📚 Дополнительно

- Полная документация: [JUDGE0_SETUP.md](JUDGE0_SETUP.md)
- Список изменений: [CHANGES_SUMMARY.md](CHANGES_SUMMARY.md)
- Language IDs: Java=62, Python=71, JS=63, C++=54

## 🔧 Проблемы?

### Judge0 не отвечает
```bash
docker logs myapp-judge0
docker-compose restart judge0
```

### PostgreSQL не подключается
```bash
docker logs myapp-postgres
docker exec -it myapp-postgres psql -U myapp -d myappdb
```

### Приложение не стартует
Проверьте что порты 8050, 5432, 2358, 6379 свободны:
```bash
lsof -i :8050
lsof -i :5432
```

## ✅ Готово!

Теперь у вас работает:
- ✅ Локальный Judge0 для выполнения кода
- ✅ Сохранение кода в PostgreSQL
- ✅ История всех попыток
- ✅ Статистика по задачам
- ✅ JWT аутентификация
