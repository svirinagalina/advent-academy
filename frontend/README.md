# Advent Academy — frontend

Отдельное приложение Next.js (App Router), React, TypeScript и Tailwind CSS.
На этом этапе содержит стартовую страницу; интеграция с API и авторизация
будут добавлены после согласования дизайна. В Gradle приложение не включается.

## Локальный запуск

Нужен Node.js 24 LTS. Версия зафиксирована в `.nvmrc`; при использовании nvm
выполни `nvm install` и `nvm use` из этой директории.

```bash
cd frontend
npm ci
cp -n .env.example .env.local
npm run dev
```

Открой http://localhost:3000. Если порт занят: `npm run dev -- --port 3001`.

## Проверки и сборка

```bash
npm run check
npm run build
npm run start
```

`check` запускает Biome и TypeScript. `build` создаёт production-сборку.
Браузерные тесты появятся вместе с первыми пользовательскими сценариями.

## Структура

- `src/app/` — страницы, layout и общие стили.
- `src/components/` — переиспользуемые компоненты.
- `src/lib/` — будущие API-клиенты и вспомогательный код.
- `public/` — публичные изображения, шрифты и другие ресурсы сайта.
- `design-reference/` — сюда положи HTML-дизайн и его исходные ресурсы.

## Docker

Из корня репозитория, без запуска остальных сервисов:

```bash
docker compose --profile frontend up -d --build frontend
```

Сайт доступен на http://localhost:3000. Для остановки только фронтенда:
`docker compose stop frontend`.

Самостоятельная сборка: `docker build -f frontend/Dockerfile -t advent-frontend .`.
Контекст — корень репозитория, как в существующем Docker CI.
Контейнер запускает standalone-сборку от непривилегированного пользователя.

## Интеграция

Будущие запросы к Java-сервисам будут идти через `api-gateway`. Для авторизации
планируется серверная прослойка; текущий шаблон ещё не реализует сессии, BFF
или хранение токенов. HTML из `design-reference/` не исполняется приложением.
