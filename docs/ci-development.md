# CI во время активной разработки

## Обычный pipeline

- `branch-sync-check` остаётся обязательным для MR: исходная ветка содержит актуальную
  целевую ветку и не содержит новых merge-коммитов. Исключение: `dev`/`develop`
  → `main`/`master` допускает merge-историю; актуальная целевая ветка по-прежнему
  обязательна. Общую `dev` синхронизируй через merge целевой ветки, без rebase.
  Этот лёгкий job также вычисляет изменённые модули и передаёт список остальным jobs
  артефактом; Java-образу не требуется установка Git. На обычных push проверка
  ancestry не выполняется, только выбор модулей.
- `backend-check` одним вызовом Gradle выполняет `check` и `assemble` выбранных сервисов,
  плюс корневой Checkstyle. Стандартные PostgreSQL/Testcontainers и security-тесты
  выбранного сервиса не отключаются. Прежние отдельные build/test/static jobs заменены.
- `security-contract` запускается параллельно при изменениях исходников backend,
  конфигурации, контрактов безопасности или общих файлов сборки.
- `docker-build` доступен вручную при изменениях backend, Dockerfile, Compose
  или CI и не блокирует обычный MR, включая `dev` → `main`. В полном режиме,
  scheduled и UI pipeline сборка также ручная. Только для тегов она запускается
  автоматически и обязательна. Собираются выбранные backend-образы.
- Docker-in-Docker для Testcontainers сохраняется: тесты продолжают использовать
  реальные PostgreSQL-контейнеры независимо от запуска `docker-build`.
- Новый push отменяет устаревшие jobs с `interruptible: true`.

`ci/select-modules.sh` используется и для Gradle, и для Docker: миграции/тесты/внутренняя
логика обычно выбирают свой сервис; изменения JWT и публичного user API выбирают все
сервисы. Изменения общих Gradle-файлов, security policy и Compose также выбирают все.
Изменения публичных content/meeting контроллеров дополнительно проверяют gateway.
При добавлении Java-сервиса обнови список модулей в selector и пути `rules:changes`.

Обычная документация не запускает backend или Docker.
`frontend-check` использует Node.js 24 и выполняет `npm ci`, `npm run check` и
`npm run build`. Запускается при изменениях `frontend/`, CI, `.dockerignore`,
Compose, а также в полном режиме, scheduled/UI pipeline и для тегов.

`frontend-docker-build` зависит от `frontend-check` и собирает образ через
`frontend/Dockerfile` с контекстом корня репозитория. Для обычного MR и полного
режима запускается вручную и не блокирует MR; для тегов автоматический и
обязательный. Backend Docker job сохраняет собственный выбор четырёх сервисов:
frontend не передаётся в selector Gradle-модулей. Изменение только frontend
запускает его проверку и даёт отдельную ручную сборку его образа.

`.next/` и `*.tsbuildinfo` — локальные результаты сборки, в Git не хранятся.

## Полная проверка

`full-check` выполняет `./gradlew check securityContract assemble` для всего backend,
включая подключённые Checkstyle и SpotBugs. Сборка всех четырёх backend Docker-образов
доступна вручную, а для тегов обязательна и запускается автоматически после проверок.
Ускоренные jobs в этом режиме не дублируются.

Полный режим включается:

- для MR `dev`/`develop` → `main`/`master` (автоматически);
- при запуске pipeline из GitLab UI (Run pipeline);
- для scheduled pipeline;
- для тегов (перед релизом);
- при переменной `FULL_CI=true`.

Для ночных проверок создай в GitLab Pipeline Schedule на `dev`. Конфигурация умеет
обрабатывать расписание, но сама расписание в GitLab не создаёт.

Полный режим обязателен перед релизом. Он использует стандартные Gradle suites;
отдельные `camundaSmokeTest` (требует внешнего брокера) и `performanceRegressionTest`
не включены, как и в прежнем CI.

## Локальная проверка изменения CI

```bash
bash ci/test-select-modules.sh
bash ci/test-branch-sync.sh
FULL_CI=true bash ci/check-backend.sh
./gradlew check securityContract assemble --no-daemon
```

Для воспроизведения выбора модулей в MR установи `CI_PIPELINE_SOURCE=merge_request_event`,
`CI_MERGE_REQUEST_DIFF_BASE_SHA` и `CI_COMMIT_SHA`. Для push используются
`CI_COMMIT_BEFORE_SHA` и `CI_COMMIT_SHA`. При отсутствующей/нулевой базе выбираются все
сервисы; ошибка Git завершает job, а не превращается в пропуск тестов.
