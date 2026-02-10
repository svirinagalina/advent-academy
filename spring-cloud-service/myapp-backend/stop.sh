#!/bin/bash

# Цвета
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        Остановка MyApp Backend        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# 1. Остановка Spring Boot приложения
echo -e "${YELLOW}[1/2] Остановка Spring Boot...${NC}"

if [ -f "app.pid" ]; then
    PID=$(cat app.pid)
    if ps -p $PID > /dev/null 2>&1; then
        kill $PID
        echo -e "${GREEN}✓ Приложение остановлено (PID: $PID)${NC}"
        rm app.pid
    else
        echo -e "${YELLOW}⚠ Приложение уже остановлено${NC}"
        rm app.pid
    fi
else
    # Попробуем найти процесс на порту 8050
    if lsof -i :8050 &> /dev/null; then
        PID=$(lsof -ti :8050)
        kill $PID 2>/dev/null
        echo -e "${GREEN}✓ Приложение на порту 8050 остановлено${NC}"
    else
        echo -e "${YELLOW}⚠ Приложение не найдено${NC}"
    fi
fi
echo ""

# 2. Остановка Docker контейнеров
echo -e "${YELLOW}[2/2] Остановка Docker контейнеров...${NC}"
docker-compose down

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Docker контейнеры остановлены${NC}"
else
    echo -e "${RED}✗ Ошибка при остановке контейнеров${NC}"
fi
echo ""

echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║       Все сервисы остановлены!         ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}Для повторного запуска:${NC}"
echo "./start.sh"
echo ""
