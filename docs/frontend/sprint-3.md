# Спринт 3: расписание и онлайн-занятия

Статус: завершён (2026-10-14). Зависит от спринтов 1 и 2.

| Этап | Зависимость | Результат и критерий готовности |
|---|---|---|
| AA-10 — Постоянное расписание | AA-03, AA-04 | Схема meeting-service, занятия/участники, временные окна и версии; миграции проверены |
| AA-11 [BLOCKED BY AA-10] — Управление и доступ | AA-10 | Создание, перенос, отмена, начало/завершение владельцем курса; чужой ученик/преподаватель получает 403 |
| AA-12 [BLOCKED BY AA-11] — Комната и подключение | AA-11 | Capability вместо browser JWT; отмена/завершение отзывают подключение; новая комната восстанавливается после рестарта |
| AA-13 [BLOCKED BY AA-12] — Экраны и проверки | AA-12 | Ближайшее занятие, списки, управление преподавателя и звонок; реальная граница browser/gateway/services/PostgreSQL проверена |

Расписание хранит meeting-service в собственной схеме PostgreSQL. Право на курс и
назначения учеников проверяются через content-service с исходным проверенным JWT.
Идентификаторы пользователя из тела не становятся личностью вызывающего.
Участники занятия задаются явно из назначенных курсу учеников; преподаватель — создатель
занятия с правом управления курсом. Доступ по одному ID комнаты не предоставляется.

Время хранится как Instant/UTC, отображается в локальном часовом поясе браузера.
Окно входа: после запуска преподавателем, от 10 минут до начала до конца занятия.
Перенос разрешён до запуска; отмена и завершение закрывают комнату после commit.
Повтор создания определяется requestId; мутации защищены version. История занятий
не удаляется. Истечение окна закрывает соединения плановой проверкой.

Принцип запуска: metadata постоянные; WebRTC-сессии/одноразовые capability временные.
После рестарта meeting-service участники переподключаются с новым capability к тому же
активному занятию; отменённое/завершённое занятие восстановить как активное нельзя.
Проверка JOIN держит блокировку записи расписания до регистрации transport-сессии,
поэтому отмена не теряется при одновременном подключении.

Вне спринта: записи звонков, уведомления, интеграции внешних календарей и новые сервисы.
Групповой WebRTC mesh: до 8 участников (преподаватель и до 7 учеников).
Старые двухместные комнаты сохраняют прежний лимит. SFU выходит за рамки спринта.

## API

Все HTTP-запросы проходят JWT-проверку. Управление требует роли
INSTRUCTOR/CONTENT_EDITOR/ADMIN и актуального права на курс в content-service.

| Метод | Путь | Назначение |
|---|---|---|
| GET | /api/teaching/courses | Опубликованные курсы преподавателя |
| GET | /api/teaching/courses/{courseId} | Проверка права управления курсом |
| GET | /api/teaching/courses/{courseId}/students | Назначенные курсу UUID учеников |
| GET | /api/users/teaching/courses/{courseId}/students | Имена/email для выбора, после проверки права на курс |
| GET | /api/meetings/scheduled | Занятия, в которые включён ученик |
| GET | /api/meetings/scheduled/teaching | Управляемые преподавателем занятия |
| GET | /api/meetings/scheduled/{meetingId} | Детали с проверкой доступа |
| POST | /api/meetings/scheduled | Создание: requestId, courseId, title, startsAt, endsAt, participants |
| PUT | /api/meetings/scheduled/{meetingId} | Перенос: version, title, startsAt, endsAt, participants |
| POST | /api/meetings/scheduled/{meetingId}/start | Запуск, тело {version} |
| POST | /api/meetings/scheduled/{meetingId}/finish | Завершение, тело {version} |
| POST | /api/meetings/scheduled/{meetingId}/cancel | Отмена, тело {version} |
| POST | /api/meetings/scheduled/{meetingId}/capabilities | Одноразовый вход: roomId, capability, expiresAt, role |
| GET | /api/meetings/config | ICE-конфигурация; BFF добавляет signalingUrl |
| WS | /ws/meetings | JOIN с capability, затем отдельные OFFER/ANSWER/ICE для каждой пары |

Списки: tab=upcoming/past, page>=0, size=1..100 (по умолчанию 20),
ответ items/total/page/size. Страница и фильтр вычисляются в PostgreSQL.
У ученика список participants пуст; показывается только размер группы.
Версия начинается с 0 и увеличивается при каждом изменении. Точный повтор мутации
с исходной версией возвращает сохранённый результат; конкурирующая другая мутация
возвращает 409. requestId создания нельзя повторить с другими данными, в том числе
после переноса. История сохраняется.

400 — невалидные данные, 401 — сессия, 403 — права, 404 — занятие отсутствует,
409 — версия/состояние/временное окно, 429 — лимиты, 502 — право на курс сейчас
невозможно проверить. Недоступность content-service не открывает доступ к звонку
или списку учеников.

В JOINED добавлен массив peers: participantId/initiator. Для каждой пары offer
создаёт участник, который вошёл раньше. PARTICIPANT_JOINED получают все текущие
участники. Одна личность занимает один слот, повторный вход с другой активной вкладки
отклоняется. Камера/микрофон останавливаются при выходе/закрытии звонка.

## Воспроизводимая проверка

Из корня проекта, отдельная БД в tmpfs, порты 13011/18092:

```bash
./gradlew :user-service:bootJar --no-daemon
docker build -f user-service/Dockerfile -t advent-sprint3-user .
docker build -f content-service/Dockerfile -t advent-sprint3-content .
docker build -f meeting-service/Dockerfile -t advent-sprint3-meeting .
docker build -f api-gateway/Dockerfile -t advent-sprint3-gateway .
docker build -f frontend/Dockerfile -t advent-sprint3-frontend .
docker compose -p advent-sprint3 -f docs/frontend/runtime-compose-sprint3.yml up -d --wait
ADVENT_TEST_PROJECT=advent-sprint3 python3 frontend/scripts/provision-runtime.py
cd frontend
npm ci
npm run test:meetings
ADVENT_TEST_ORIGIN=http://localhost:13011 ADVENT_TEST_FIXTURE=/tmp/advent-sprint3-fixture.json npm run test:runtime
ADVENT_TEST_ORIGIN=http://localhost:13011 ADVENT_TEST_FIXTURE=/tmp/advent-sprint3-fixture.json npm run test:assignments
```

Скрипт проверяет Compose label и tmpfs до изменений. Использует синтетическую
камеру/микрофон и Chrome (`CHROME_PATH`). Перезапускает только свой meeting-service;
проверяет отказ admission/roster при остановленном своём content-service и запускает
его обратно в finally. Проверку миграции рядом с заполненными user/content схемами
выполняет в отдельной схеме и откатывает. Прогресс меняют регрессионные скрипты,
поэтому для полного повтора нужен свежий стенд.

После пересоздания контейнеров с новыми IP может потребоваться перезапуск тестового
gateway для обновления DNS-кэша; обычный restart meeting-service сохраняет адрес.

Остановка из корня:

```bash
docker compose -p advent-sprint3 -f docs/frontend/runtime-compose-sprint3.yml down
```

## Границы запуска

WebRTC mesh ограничен 8 участниками; браузерная проверка использует 3 участника,
лимит комнаты и все 28 пар для 8 участников проверяются тестом сигнализации.
Пропускная способность/нагрузка на настоящих устройствах и внешние сети не измерены.
Для публичного запуска нужны HTTPS/WSS и проверенный ICE/TURN; параметры
meeting.ice-servers настраиваются конфигурацией сервиса (например,
MEETING_ICE_SERVERS_0_URLS / MEETING_ICE_SERVERS_0_USERNAME /
MEETING_ICE_SERVERS_0_CREDENTIAL). Проверка здесь идёт в одной локальной сети.
Camunda на тестовом стенде отключён. Один meeting-service хранит транспортные
сессии; горизонтальное масштабирование требует общей сигнализации или привязки
комнаты к экземпляру. Ограничение одного standalone BFF из предыдущего спринта
сохраняется. Старый статический двухместный клиент не используется для групповых
занятий; групповой клиент находится в frontend.


## Результаты (2026-10-14)

- Этапы AA-10–AA-13 выполнены.
- meeting-service: 56 тестов, включая 9 сценариев расписания на PostgreSQL и
  проверку 8 участников/28 пар; content-service: 275, user-service: 206,
  api-gateway: 13. Ошибок и пропусков нет. SecurityContract: 10, успешно.
- `./gradlew check securityContract assemble --no-daemon` прошёл, включая
  Checkstyle/SpotBugs. Biome/TypeScript и production Docker build frontend прошли.
  Образы user/content/meeting собраны; все шесть сервисов стенда healthy.
- Реальный INSTRUCTOR JWT прошёл HTTP → security → service → PostgreSQL;
  ученик и чужой преподаватель не получили права управления. Имена учеников
  раскрываются только после авторизации курса; ученику список других UUID скрыт.
- Конкурирующее создание сохранило одно занятие со всеми членами группы.
  Переносы защищены версией; повтор создания после переноса возвращает ту же запись.
  JOIN удержал блокировку до регистрации; отмена дождалась его завершения.
  Отменённый admission и запрещённые переходы отвергнуты, истечение сохраняет историю.
- Playwright на итоговом frontend: три контекста, входящие видео кадры и аудио RTP
  у каждого, фактическое выключение camera/audio tracks, восстановление трёх
  соединений после настоящего restart meeting-service. После отмены видео/медиа
  очищены у всех, старый ключ не принят. History/mobile/CSRF проверены.
- При остановленном тестовом content-service admission и roster вернули 502;
  восстановление сервиса и завершение занятия затем прошли.
- Миграция V1 проверена на чистом PostgreSQL и рядом с заполненными user/content
  схемами; существующие счётчики сохранены, пробный replay откатан.
- Регрессии спринтов 1 и 2 прошли: вход/refresh, прогресс,
  закрытые уроки, чужой курс, сдача/доработка/принятие, потерянный ответ и повтор,
  история, мобильные экраны и отдельная демоверсия.
- `git diff --check` прошёл. Временный стенд удалён; демо на порту 3107 сохранено.

Подтверждены локальные сценарии на синтетических данных. Подключение через
внешний TURN, нагрузка восьми устройств, production и несколько экземпляров
BFF/meeting-service не проверялись; условия запуска приведены выше.
