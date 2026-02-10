#!/bin/bash

# Цвета
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   MyApp Backend - Automated Startup   ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# Функция для проверки что команда существует
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# 1. Проверка требований
echo -e "${YELLOW}[1/5] Проверка требований...${NC}"

if ! command_exists docker; then
    echo -e "${RED}✗ Docker не установлен${NC}"
    echo "Установите Docker Desktop: https://www.docker.com/products/docker-desktop"
    exit 1
fi

if ! docker ps &> /dev/null; then
    echo -e "${RED}✗ Docker не запущен${NC}"
    echo "Запустите Docker Desktop и попробуйте снова"
    exit 1
fi
echo -e "${GREEN}✓ Docker установлен и запущен${NC}"

if ! command_exists java; then
    echo -e "${RED}✗ Java не установлен${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java установлен${NC}"

if [ ! -f "./gradlew" ]; then
    echo -e "${RED}✗ gradlew не найден${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Gradle wrapper найден${NC}"
echo ""

# 2. Остановка старых контейнеров
echo -e "${YELLOW}[2/5] Остановка старых контейнеров...${NC}"
docker-compose down &> /dev/null
echo -e "${GREEN}✓ Старые контейнеры остановлены${NC}"
echo ""

# 3. Запуск Docker Compose
echo -e "${YELLOW}[3/5] Запуск Docker Compose (PostgreSQL, Judge0, Redis)...${NC}"
docker-compose up -d

# Ждем запуска контейнеров
echo "Ожидание запуска контейнеров..."
sleep 5

# Проверка контейнеров
CONTAINERS=("myapp-postgres" "myapp-judge0" "judge0-postgres" "myapp-redis")
ALL_RUNNING=true

for container in "${CONTAINERS[@]}"; do
    if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        echo -e "${GREEN}✓ ${container}${NC}"
    else
        echo -e "${RED}✗ ${container} не запущен${NC}"
        ALL_RUNNING=false
    fi
done

if [ "$ALL_RUNNING" = false ]; then
    echo -e "${RED}Не все контейнеры запущены. Проверьте логи: docker-compose logs${NC}"
    exit 1
fi
echo ""

# 4. Проверка Judge0
echo -e "${YELLOW}[4/5] Проверка Judge0...${NC}"
MAX_RETRIES=30
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -s http://localhost:2358/about &> /dev/null; then
        echo -e "${GREEN}✓ Judge0 API доступен${NC}"
        break
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo -n "."
    sleep 2
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo ""
    echo -e "${RED}✗ Judge0 не отвечает${NC}"
    echo "Проверьте логи: docker logs myapp-judge0"
    exit 1
fi
echo ""

# 5. Запуск приложения
echo -e "${YELLOW}[5/5] Запуск Spring Boot приложения...${NC}"
echo "Это займет ~30 секунд при первом запуске..."
echo ""

# Даем пользователю выбор
echo -e "${BLUE}Выберите вариант запуска:${NC}"
echo "1) Запустить в фоне (nohup)"
echo "2) Запустить в текущем терминале"
read -p "Введите 1 или 2: " choice

if [ "$choice" = "1" ]; then
    nohup ./gradlew bootRun > app.log 2>&1 &
    APP_PID=$!
    echo $APP_PID > app.pid
    echo -e "${GREEN}✓ Приложение запущено в фоне (PID: $APP_PID)${NC}"
    echo "Логи: tail -f app.log"
    echo "Остановить: kill \$(cat app.pid)"

    # Ждем запуска
    echo "Ожидание запуска приложения..."
    sleep 10

    if lsof -i :8050 &> /dev/null; then
        echo -e "${GREEN}✓ Приложение успешно запущено${NC}"
    else
        echo -e "${YELLOW}⚠ Приложение еще запускается, проверьте логи${NC}"
    fi
else
    echo -e "${GREEN}Запуск в текущем терминале...${NC}"
    echo "Для остановки нажмите Ctrl+C"
    echo ""
    ./gradlew bootRun
    exit 0
fi

echo ""
echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║           Запуск завершен!             ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
echo ""
echo -e "${BLUE}🌐 Приложение:${NC} http://localhost:8050"
echo -e "${BLUE}🔍 Judge0:${NC}     http://localhost:2358"
echo -e "${BLUE}💾 PostgreSQL:${NC} localhost:5432"
echo ""
echo -e "${YELLOW}📝 Следующие шаги:${NC}"
echo "1. Откройте task.http в IntelliJ IDEA"
echo "2. Выполните запрос 'Register new user'"
echo "3. Выполните запрос 'Login'"
echo "4. Скопируйте токен в переменную @token"
echo "5. Попробуйте отправить решение"
echo ""
echo -e "${YELLOW}🧪 Или запустите тест:${NC}"
echo "./test-judge0.sh"
echo ""
echo -e "${YELLOW}📚 Документация:${NC}"
echo "cat README.md"
echo "cat QUICKSTART.md"
echo ""
