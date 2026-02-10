# MyApp Mobile - Обучающая платформа по программированию

Flutter приложение для iOS и Android, которое работает как клиент к Spring Boot бэкенду обучающей платформы.

## Возможности

- 🔐 Регистрация и вход пользователей (JWT аутентификация)
- 📝 Просмотр списка задач по программированию
- 🎯 Фильтрация задач по уровню сложности (Easy, Medium, Hard)
- 💻 Встроенный редактор кода с подсветкой синтаксиса
- ✅ Отправка решений на проверку через Judge0
- 📊 История попыток и статистика
- 👤 Профиль пользователя

## Технологический стек

- **Flutter** - кроссплатформенная разработка
- **Riverpod** - state management
- **Go Router** - навигация
- **Dio** - HTTP клиент
- **Freezed** - immutable модели
- **Flutter Secure Storage** - безопасное хранение JWT токена
- **Flutter Code Editor** - редактор кода с подсветкой синтаксиса

## Предварительные требования

- Flutter SDK 3.10+ (уже установлен в `~/development/flutter`)
- macOS для разработки iOS приложений
- Xcode 15+ (для iOS)
- Android Studio (для Android)
- Запущенный бэкенд на http://localhost:8050

## Установка и запуск

### 1. Установка зависимостей

```bash
cd /Users/galina/Desktop/Pre-ProjectJava/spring-cloud-service/myapp_mobile
~/development/flutter/bin/flutter pub get
```

### 2. Запуск бэкенда

Убедитесь, что Spring Boot бэкенд запущен на порту 8050:

```bash
cd /Users/galina/Desktop/Pre-ProjectJava/spring-cloud-service/myapp-backend
./start.sh
```

### 3. Запуск приложения

#### На macOS (для быстрого тестирования):

```bash
~/development/flutter/bin/flutter run -d macos
```

#### На iOS симуляторе:

Сначала запустите iOS симулятор из Xcode или командой:

```bash
open -a Simulator
```

Затем запустите приложение:

```bash
~/development/flutter/bin/flutter run
```

#### На Android эмуляторе:

```bash
~/development/flutter/bin/flutter run -d emulator-5554
```

### 4. Добавление Flutter в PATH (опционально)

Для удобства добавьте Flutter в PATH:

```bash
export PATH="$PATH:$HOME/development/flutter/bin"
```

Или добавьте эту строку в `~/.zshrc` или `~/.bash_profile` для постоянного использования.

## Структура проекта

```
lib/
├── config/           # Конфигурация API
├── models/           # Модели данных (Task, Submission, User)
├── services/         # API клиент и сервисы
├── providers/        # Riverpod провайдеры
├── screens/          # Экраны приложения
│   ├── auth/        # Авторизация
│   ├── home/        # Главный экран
│   ├── task/        # Детали задачи и редактор кода
│   ├── submissions/ # История попыток
│   └── profile/     # Профиль
├── widgets/          # Переиспользуемые виджеты
├── utils/            # Утилиты (theme, constants)
├── app.dart          # Корневой виджет с навигацией
└── main.dart         # Точка входа
```

## Основные экраны

### 1. Авторизация
- **Login Screen** - вход в систему
- **Register Screen** - регистрация нового пользователя

### 2. Главный экран (Home)
- Список всех задач
- Фильтрация по сложности
- Pull-to-refresh

### 3. Детали задачи
- Описание задачи
- Вкладка "Submissions" - история попыток для задачи
- Вкладка "Statistics" - статистика решения

### 4. Редактор кода
- Редактор с подсветкой синтаксиса
- Кнопка отправки решения
- Отображение результатов проверки

### 5. История попыток
- Все попытки пользователя
- Статус каждой попытки
- Детали выполнения

### 6. Профиль
- Информация о пользователе
- Статистика
- Выход из системы

## API Endpoints

Приложение использует следующие API endpoints бэкенда:

### Аутентификация
- `POST /api/auth/register` - Регистрация
- `POST /api/auth/login` - Вход (получение JWT)

### Задачи
- `GET /api/tasks` - Список всех задач
- `GET /api/tasks/{id}` - Детали задачи
- `GET /api/tasks/difficulty/{difficulty}` - Фильтр по сложности
- `POST /api/tasks/{id}/submit` - Отправка решения

### Попытки
- `GET /api/tasks/{taskId}/submissions` - История попыток для задачи
- `GET /api/tasks/submissions/my` - Все попытки пользователя
- `GET /api/tasks/{taskId}/stats` - Статистика по задаче

## Работа с локальным бэкендом

### Из iOS симулятора
iOS симулятор может обращаться к localhost напрямую, поэтому используется `http://localhost:8050`

### Из реального устройства
Для работы с реальным устройством нужно изменить baseUrl в `lib/config/api_config.dart`:

```dart
static const String baseUrl = 'http://YOUR_LOCAL_IP:8050/api';
```

Найти IP адрес можно командой:
```bash
ifconfig | grep "inet " | grep -v 127.0.0.1
```

## Сборка релизной версии

### iOS
```bash
~/development/flutter/bin/flutter build ios --release
```

### Android
```bash
~/development/flutter/bin/flutter build apk --release
```

## Тестирование

Для проверки работы приложения:

1. ✅ Запустите бэкенд
2. ✅ Запустите приложение
3. ✅ Зарегистрируйте нового пользователя
4. ✅ Войдите в систему
5. ✅ Просмотрите список задач
6. ✅ Выберите задачу и откройте редактор кода
7. ✅ Напишите и отправьте решение
8. ✅ Проверьте результаты и историю попыток
9. ✅ Проверьте статистику в профиле
10. ✅ Выйдите из системы

## Troubleshooting

### Ошибка "Connection refused"
- Убедитесь, что бэкенд запущен на порту 8050
- Проверьте baseUrl в `lib/config/api_config.dart`

### Ошибка при сборке
```bash
~/development/flutter/bin/flutter clean
~/development/flutter/bin/flutter pub get
~/development/flutter/bin/dart run build_runner build --delete-conflicting-outputs
```

### Проблемы с Xcode
```bash
sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer
sudo xcodebuild -runFirstLaunch
```

## Следующие шаги

- [ ] Добавить поддержку нескольких языков программирования
- [ ] Реализовать темную тему
- [ ] Добавить поиск задач
- [ ] Реализовать рейтинг пользователей
- [ ] Добавить push-уведомления
- [ ] Реализовать offline режим

## Контакты

Разработано с использованием Claude Code 🤖
