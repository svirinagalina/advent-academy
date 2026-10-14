# Спринт 1: реальный кабинет ученика

Статус: код этапов AA-01–AA-05 реализован; итоговые проверки приведены ниже. Дизайн существующего кабинета сохраняется. Демо остаётся на `/demo`.

## Правила

- Источник личности — проверенный JWT. Клиент не передаёт userId для чтения/изменения прогресса.
- Администратор, редактор или владелец курса назначает опубликованный курс ученику.
- Каталог публичный; каталог не предоставляет доступ к обучению.
- Ученик читает только назначенные опубликованные курсы и опубликованные материалы.
- Порядок: компоненты по position, затем уроки по position. Следующий урок доступен после предыдущего.
- Завершение идемпотентно, конкурентные записи сериализованы блокировкой назначения в PostgreSQL.
- Обязательное задание запрещает ручное завершение; сдача/проверка — спринт 2.
- Изменение состава опубликованного курса во время обучения не входит в спринт; прогресс связан с UUID уроков.
- Доступ к опубликованным assets остаётся публичным по действующему контракту; приватное видео не обещается.

## API

| Метод | Путь | Назначение |
|---|---|---|
| GET | /api/users/me | Собственный профиль |
| POST | /api/auth/logout | Отзыв переданного refresh-токена; повторный выход успешен |
| POST | /api/lessons/{lessonId}/content/publish | Публикация владельцем/редактором |
| PUT | /api/lessons/{lessonId}/requirements | requiredAssignment; владелец/редактор, до публикации компонента |
| PUT | /api/learning/courses/{courseId}/students/{studentId} | Назначение курса владельцем/редактором |
| GET | /api/learning/courses | Курсы текущего пользователя, дерево и прогресс |
| GET | /api/learning/courses/{courseId} | Свой курс и прогресс |
| GET | /api/learning/courses/{courseId}/lessons/{lessonId} | Доступный материал и метаданные assets |
| POST | /api/learning/courses/{courseId}/lessons/{lessonId}/complete | Завершение и обновлённый прогресс |

Ошибки: 401 — нет сессии, 403 — нет доступа, 404 — объект отсутствует,
409 — урок заблокирован/нужна проверка/неопубликован материал, 400 — неверный запрос.
Назначение хранится в content-service; userId — внешняя ссылка без межсервисного FK.
Назначающий должен использовать UUID действующего ученика из user-service.
Миграция добавляет таблицы и обязательность задания с false для существующих уроков;
старые записи не удаляются, учащиеся автоматически не назначаются.

## Этапы

1. AA-01: контракт, ownership, данные проверки.
2. AA-02: login/MFA, HttpOnly сессия, профиль, refresh, logout.
3. AA-03: публикация, программа, материалы, assets.
4. AA-04: назначения, серверный прогресс, порядок/идемпотентность.
5. AA-05: реальный UI, отрицательные сценарии, сборка, runtime/readiness.

Вне спринта: сдача/проверка заданий, расписание, звонки в новом кабинете,
регистрация/сброс пароля в UI, новые микросервисы.

## Изолированная runtime-проверка

Из корня репозитория, без рабочего PostgreSQL и Camunda:

```bash
./gradlew :user-service:bootJar --no-daemon
docker build -f user-service/Dockerfile -t advent-sprint1-user .
docker build -f content-service/Dockerfile -t advent-sprint1-content .
docker build -f api-gateway/Dockerfile -t advent-sprint1-gateway .
docker build -f frontend/Dockerfile -t advent-sprint1-frontend .
docker compose -p advent-sprint1 -f docs/frontend/runtime-compose.yml up -d --wait
python3 frontend/scripts/provision-runtime.py
cd frontend
npm ci
npm run test:runtime
```

Стенд: http://localhost:13007, gateway: http://localhost:18090.
Fixture-скрипт проверяет имя Compose-проекта и tmpfs перед записью в БД.
Он создаёт синтетические аккаунты через API, подтверждает их в тестовой БД,
создаёт/публикует курс через API и сохраняет параметры в `/tmp/advent-sprint-fixture.json`.
Проверка ожидает свежий прогресс: для повтора пересоздай **только этот тестовый стенд**.
Для завершения из корня:

```bash
docker compose -p advent-sprint1 -f docs/frontend/runtime-compose.yml down
```

Временная БД удаляется при остановке контейнера; рабочие сервисы не затрагиваются.
По умолчанию тест использует `/usr/bin/google-chrome`, путь меняется через `CHROME_PATH`.

## Формат ответа курса

`id`, `title`, `description`, `status` (`new`, `active`, `complete`), `total`,
`completed`, `currentLessonId` и `modules`. В каждом модуле упорядоченные `lessons`:
`id`, `title`, `position`, `assignment`, `completed`, `available`.
Содержимое урока: `id`, `title`, `html`, `assignment`, `assets` с id/filename/contentType/fileSize/url.
HTML очищается на backend; ссылки на assets BFF переводит в `/api/backend/assets/{id}`.

Публикация контента владельцем переводит DRAFT через REVIEW в PUBLISHED в одной
транзакции с аудитом; повторная публикация успешна. Метаданные задания меняются
только до публикации компонента. Редакторский workflow review не расширяется в этом спринте.

## Итоговые проверки (2026-10-14)

- content-service: 267 тестов; user-service: 206; api-gateway: 13. Ошибок и пропусков нет.
- Checkstyle, SpotBugs и повторный securityContract прошли.
- Frontend: Biome/TypeScript, production build (webpack в ограниченном окружении),
  обычный production build внутри Docker — успешно.
- Все четыре Docker-образа собраны штатными Dockerfile; изолированный стек достиг healthy/readiness.
- Playwright через реальные Next.js → gateway → Java → PostgreSQL: вход, профиль,
  чтение материала, завершение, сохранение прогресса, одновременный refresh,
  выход/повторный вход, запрет пропуска/обязательного задания/чужого курса,
  пустые назначения, мобильная ширина, демо, Origin и список разрешённых API — успешно.
- V9 применена в транзакции к копиям заполненных таблиц старой формы: записи сохранены,
  обязательность старых уроков false, неожиданных назначений нет; проверка откачена.
- Нет ключа сессии: /api/health → 503. Остановлен gateway тестового стенда:
  readiness → 503, вход → 502; после восстановления readiness → 200.

Границы: стенд использовал временную PostgreSQL БД и отключённый Camunda,
так как workflow ролей не менялся. Production-среда и несколько экземпляров BFF
не проверялись. Перед несколькими экземплярами нужен общий session/rotation store.
Рабочие данные и существовавшие до проверки контейнеры не изменялись.
