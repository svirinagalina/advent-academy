# Исправление восьми пунктов аудита — 16 октября 2026

Ветка: `fix/audit-production-defects`. База: `fa647984144637c6a43490497e167cc3e6129f78`.
Работа выполнена в исходном checkout Advent Academy без копирования проекта,
переключения dev, публикации или изменения пользовательского стенда.

| Пункт | Изменение | Регрессионное доказательство |
| --- | --- | --- |
| 1. Заблокированный пользователь сохранял signaling | Проверка identity перед JOIN и сообщениями, проверка простаивающих соединений каждые 15 секунд; private capability сохраняет исходную авторизацию. Клиент обновляет scheduled capability через REAUTHORIZE | MeetingSignalingHandlerTest; production API: подключённый private socket и ранее выданный capability после block отклонены |
| 2. Replay не отзывал refresh-потомка | Миграция V17: family_id и consumed; ротация сохраняет семейство, replay удаляет его оставшиеся refresh-токены; отзыв коммитится при 401 | AuthSecurityIT, UserAdminIT; production API: R0 → R1, replay R0 → 401, R1 → 401; независимый login сохраняется в PostgreSQL IT |
| 3. Конкурентный JOIN ломал WebRTC | Роль инициатора в уведомлении определяется тем же порядком участников, что в JOINED | ConcurrentJoinTest с барьером двух потоков; две реальные браузерные сессии передают видео/аудио после перезапуска meeting-service и переподключения |
| 4. Архивирование модуля ломало опубликованный курс | Запрет archive при использовании опубликованным курсом; публикация и архивирование сериализуются блокировкой модуля | PublicationRaceIT: 10 конкурентных прогонов в PostgreSQL; API: archive → 409, список ученика → 200 |
| 5. Связь с чужим черновиком раскрывала метаданные | Проверка доступа к DRAFT до добавления связи и повторная проверка при раскрытии дерева, включая старые связи | SecurityIT: отказ связывания и чтения legacy-связи, разрешённое использование опубликованного модуля; production API: foreign draft link → 403 |
| 6. BCrypt игнорировал суффикс длинного пароля | Общая граница 72 байта UTF-8 для encode/matches, согласованные DTO и recovery UI; spring-security-crypto 6.4.4 | BCryptPasswordEncoderAdapterTest: ASCII/кириллица на границе и длинный суффикс; production API: 73 ASCII и 37 кириллических символов → 400 |
| 7. Clone терял обязательное задание | Перенос required_assignment и определения задания на новые lesson ID в той же транзакции | ConstructorApiIT и production API: обязательность, заголовок и условие сохранены; история решений не копируется |
| 8. Не было назначения преподавателя куратором | Scoped lookup активных подтверждённых INSTRUCTOR, версионированная передача опубликованного курса и аудит; UI назначения/снятия; BFF и authorization matrix | LearningIT, TeachingRosterControllerIT; браузер: назначение → список учеников → сдача → проверка → прогресс; stale version → 409, роль → 403; снятие закрывает действующий вызов |

Назначение реализовано сменой владельца опубликованного курса. ADMIN/CONTENT_EDITOR
сохраняют доступ, новый преподаватель получает управление курсом, прежний теряет его.
Новая глобальная роль или модель нескольких преподавателей не вводилась.

## Проверки

- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew check securityContract assemble --no-daemon` — PASS.
  622 теста по XML-отчётам: user 233, content 308, meeting 60, gateway 14, security-common 7;
  failures/errors/skipped = 0. Неизменённые Gradle tasks использовали актуальные локальные результаты.
- `npm run check`, `npm run build` на Node.js 24 — PASS.
- `python3 scripts/user-admin-e2e.py` — PASS: редактор, назначение преподавателя,
  два браузерных media-потока и reconnect, диалоги, ADMIN/SMTP, размеры 1440/390/320,
  темы, увеличение текста и клавиатура.
  Артефакты: `reports/user-admin-e2e/20261016-211645-2711b9b0/`.
- `python3 scripts/production-readiness-smoke.py` — PASS: production-профили, RSA,
  TLS edge, два BFF, backup/restore в новую БД и восстановление после отказа content-service.
  Артефакты: `reports/architecture-readiness/advent-readiness-20261016-211724-3dd2fe/`.
- `bash ci/test-branch-sync.sh`, `bash ci/test-select-modules.sh` — PASS;
  selector: 26 сценариев. Удалённый CI не запускался.
- `graphify update .` — выполнен локально, AST-only; query/path/explain использованы.
  SQL extractor не установлен; предупреждение Gradle parser не влияет на успешную сборку.
  Новый локальный `graphify-out/` не включён в коммиты исправлений.

- `python3 scripts/api-pentest.py --profile production` — PASS: 508 проверок, 0 ошибок,
  `completed=true`. Артефакты: `reports/security-pentest/advent-pentest-20261016-211948-1ec025/`.
  Промежуточные неуспешные запуски сохранены как диагностика исправленных тестовых
  фикстур и не считаются успешными проверками. Все временные стенды этого исправления удалены.

## Обновление и границы результата

Миграция V17 требует согласованного обновления identity без одновременной работы старой
и новой логики consume. Точный порядок и ограничения отката описаны в
[production.md](../operations/production.md). Старые пароли длиннее 72 байт требуют сброса.
Refresh-family revocation не отзывает уже выпущенный access-токен этой семьи до его TTL;
блокировка аккаунта и смена полномочий отзывают access через authorization_version.

Штатный клиент закрывает media при отзыве signaling. Произвольный WebRTC-клиент может
сохранить уже установленный прямой peer-канал. Внешний TURN/NAT, реальные устройства,
нагрузка, HA и удалённые CI/security gates здесь не проверялись. Открытые эксплуатационные
пункты [risk-register.md](risk-register.md) сохраняются; устранение этих восьми пунктов
само по себе не означает готовность к неограниченному публичному запуску.
