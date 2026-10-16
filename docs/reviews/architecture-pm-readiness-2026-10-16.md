# Advent Academy architecture and PM review after remediation

Проверено 2026-10-16 в одной ветке `fix/architecture-readiness`. Исходный аудит — `45d5595`;
свежий `origin/dev` — `9aff3a9`. Эти две точки имеют одинаковое дерево исходников:
админ-панель вошла в dev merge-коммитом. Ветка исправлений основана на новом dev.

**Вердикт:** исходные локальные исправления реализованы и проверены. Возможен сопровождаемый
пилот с управлением курсами через администратора/редактора. Полностью самостоятельная работа
команды преподавателей и публичная эксплуатация ещё требуют задач ниже. Это не production approval.

## Оставшиеся findings

### P1 — Нет полноценного назначения преподавателя на созданный курс

Подтверждено кодом: `CourseAssemblyService.create` назначает владельцем вызывающего;
`CourseController.create` разрешён ADMIN/CONTENT_EDITOR. INSTRUCTOR управляет только своими
курсами, но штатного API/UI передачи курса или назначения ответственного преподавателя нет.
Администратор может создать курс, но затем не может выдать преподавателю ограниченное
управление именно этим курсом. Временная выдача глобального CONTENT_EDITOR не заменяет этот
сценарий. Это существовавший разрыв продукта, уточнённый повторным ревью.

Источники: `content-service/.../application/service/CourseAssemblyService.java`,
`content-service/.../adapter/rest/controller/CourseController.java`,
`frontend/src/components/management.tsx`.

Следующая задача: явное назначение преподавателя либо безопасная передача владения, отдельная
проверка активного INSTRUCTOR, версии/аудит и ограниченные права на общие модули.
Критерий: ADMIN создаёт курс и назначает преподавателя; тот ведёт своих учеников/задания/занятия;
чужой преподаватель получает 403; снятие назначения проверяется сервером.

### P1 — Нет семейного отзыва refresh при обнаруженном повторном использовании

Долговечные одноразовые refresh и общий BFF receipt исправляют рестарты и конкурентную ротацию.
Однако `RefreshTokenService.refresh` отказывает в повторном использовании старого токена,
не отзывая автоматически всю его семью. В сценарии кражи refresh законный клиент получает 401,
а выпущенный злоумышленнику следующий токен сам по себе не становится недействительным.
Это остаток SEC-104, а не регрессия общего хранилища ротаций.

Источники: `user-service/.../application/service/auth/RefreshTokenService.java`,
`user-service/.../application/service/auth/RefreshRotationCoordinator.java`.

Следующая задача: модель семьи refresh, сохранение признака потребления, атомарный отзыв семьи
при подтверждённом replay, исключение легальных BFF retries и конкурентные PostgreSQL-тесты.
Receipt действует 30 секунд; его истечение может потребовать повторного входа и не является
обещанием неограниченного восстановления потерянных ответов.

### P1 перед публичным запуском — Неполные security release gates

Теперь есть npm advisory gate, security contract с корректной инвалидизацией входов и реальный
production browser gate. Java SCA, secret/SBOM/container/IaC/SAST ещё не образуют полного
блокирующего контура. Зелёные 612 функциональных/security-тестов не доказывают отсутствие CVE.
Источники: `.gitlab-ci.yml`, `platform/build.gradle`, `docs/security/risk-register.md` R-012.

Нужны проверяемые результаты сканирования фактических release JAR/images, политика исправления
найденных advisory и запуск опубликованной конфигурации pipeline на реальном GitLab runner.
В этом запуске CI helper image собран и Chrome preflight внутри него прошёл; удалённый pipeline
не запускался и его результат не заявляется.

### P1/P2 эксплуатации — Внешние сети, доступность и операторский контур

Звонки остаются mesh до восьми участников и одним транспортным узлом. Случайный второй узел
теперь отвергается PostgreSQL lease, а потеря lease закрывает приложение. Это предотвращает split
комнат, но не создаёт HA или SFU. Нет измеренной вместимости восьми реальных устройств и проверки
внешнего TURN. Это ограничение среды локальной проверки и выбранной топологии.

Backup/restore проверены на отдельной БД; реальный backup schedule, RPO/RTO, paging, dashboards,
retention и rehearsal переноса существующей установки остаются операторскими release gates.
Production identity schema не переносит старую public schema автоматически. Источники:
`deploy/production/compose.yml`, `docs/operations/production.md`, R-009/R-011.

## Что исправлено относительно исходного аудита

| Задача | Итог и проверка |
| --- | --- |
| AR-01 — Файлы и активный контент | SVG и подмена MIME отвергаются; legacy SVG скачивается как octet-stream attachment с sandbox CSP/nosniff; unsafe HTML из имитированной регрессии санитайзера не исполняется в iframe |
| AR-02 — Лимиты входа | Edge заменяет недоверенные адресные заголовки, BFF подписывает route-bound bucket; два клиента имеют разные квоты, поддельная подпись отвергается |
| AR-03 — JWT | Общая проверка RSA/kid/algorithm/claims; private key только у issuer, публичные ключи у ресурсов; active/previous overlap, JWKS, fail-closed production |
| AR-04 — BFF refresh | Зашифрованные receipts в PostgreSQL; восемь параллельных запросов через два BFF проходят одну ротацию; logout старой cookie сериализуется с дальнейшей ротацией и отзывает её потомков |
| AR-05 — Связность и транзакции | Назначение ученика не держит SQL-транзакцию во время HTTP; доверенная scoped проверка не вызывает content обратно; состояния курса перепроверяются при записи; сетевые проверки meeting вынесены перед блокировками |
| AR-06 — Чтение | Metadata pagination курсов, пакетный прогресс/задания/очередь проверки, минимальные meeting access projections; legacy array API сохранён для совместимости |
| AR-07 — Самообслуживание | Атомарная версия обязательности/условия задания и UI; восстановление пароля/подтверждение email через BFF, SMTP, single-use токены, HttpOnly/Secure cookie |
| AR-08 — Workflow | Долговечная очередь в role_requests, lease/повторы; первый bind фиксирует канонический процесс, дубли не достигают согласования/выдачи роли; PostgreSQL и embedded Camunda проверки |
| AR-09 — Эксплуатация | Отдельные production profiles, TLS, файловые секреты, отдельные DB identities, защищённые audit/history grants, bootstrap администратора, проверенный restore в новую БД и операционная инструкция |
| AR-10 — Файлы и память | До 10 MiB на файл/100 MiB на урок; metadata перед авторизацией без blob; ограничение параллельных тяжёлых операций; BFF stream; production enrollment/progression gate на опубликованные вложения |
| AR-11 — Звонки | Явная singleton placement; второй transport отвергается; внешняя capacity остаётся явно открытым критерием |
| AR-12 — Регрессия и ревью | Свежие проверки, новый CI browser gate, npm audit, актуальные ADR/risk register/task board и это повторное ревью |

## Фактические проверки

- `./gradlew check securityContract assemble --rerun-tasks --no-daemon`: полный свежий прогон;
  после последующих исправлений повторены затронутые suites и полный `check securityContract assemble`.
  В итоговых XML — 612 тестов, 0 failures/errors/skipped: gateway 14, content 303, meeting 56,
  user 225, security-common 4, security contract 10.
- Frontend: Biome/TypeScript, production build и браузерные проверки.
- `python3 scripts/user-admin-e2e.py`: редактор, ZIP/изображения, изоляция HTML,
  конфликты/сеть, диалоги, SMTP/admin/invitations, роли, мобильные размеры и темы.
- `python3 scripts/production-readiness-smoke.py`: реальные production profiles на новой
  локальной БД, RSA/TLS, bootstrap, SMTP, UI задания/reset, приватные и закрытые по прогрессу
  assets, legacy download, пагинация, два BFF/refresh, независимые квоты, отказ второго meeting,
  backup/restore и 503/recovery при остановке собственного content-service.
- CI helper Docker image собран; Chrome preflight внутри него прошёл. Module selector — 26
  сценариев; branch-sync self-tests и 3 теста изолированного runner прошли.
- npm audit frontend: 0 advisories по полученному registry response. Java/container scans не заявляются.
- `git diff --check`, Python compilation и JS syntax checks прошли.

Последние локальные evidence сохраняются в `reports/architecture-readiness/` и
`reports/user-admin-e2e/`; финальная сводка связывает их с коммитом в verification.json.
Все стенды использовали собственные синтетические данные и удалены. Существующий
`advent-user-admin-stand` сохранён. Удалённого развёртывания и публикации ветки не было.

## Оценка как архитектор и PM

Архитектура стала пригоднее для ограниченного пилота: меньше доверия между сервисами,
нет зависимости BFF-сессии от одного процесса, короче транзакции, ограничены тяжёлые запросы,
появились проверяемый production entry point и восстановление. PostgreSQL blob storage,
legacy unpaginated API, крупные старые UI-компоненты и Spring/JDBC coupling остаются техническим
долгом; без нагрузки они не объявляются доказанным исчерпанием производительности.

Сотрудник ADMIN/CONTENT_EDITOR теперь может создать материал с обязательной работой,
пригласить ученика и вести учебный процесс через интерфейс. Пользователь восстанавливает
пароль без помощи разработчика. Для обычного INSTRUCTOR назначение курса ещё не завершено.
Оператор получает воспроизводимый запуск и восстановление, но его реальные графики,
уведомления и ответственность не создаются локальным успешным тестом.

**Критерий следующего релиза:** закрыть назначение преподавателя и security replay/gates,
пройти внешние device/TURN и эксплуатационные проверки либо оформить конкретное ограничение
пилота с ответственным и сроком. Утверждать готовность к самостоятельному публичному
production по текущим данным нельзя.
