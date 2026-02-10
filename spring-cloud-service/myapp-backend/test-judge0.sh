#!/bin/bash

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== Проверка Judge0 ===${NC}\n"

# 1. Проверка что Docker запущен
echo -e "${YELLOW}1. Проверка Docker...${NC}"
if ! docker ps &> /dev/null; then
    echo -e "${RED}✗ Docker не запущен${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker запущен${NC}\n"

# 2. Проверка контейнеров
echo -e "${YELLOW}2. Проверка контейнеров...${NC}"
containers=("myapp-judge0" "judge0-postgres" "myapp-redis")
for container in "${containers[@]}"; do
    if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        echo -e "${GREEN}✓ ${container} запущен${NC}"
    else
        echo -e "${RED}✗ ${container} не найден${NC}"
    fi
done
echo ""

# 3. Проверка доступности Judge0 API
echo -e "${YELLOW}3. Проверка Judge0 API...${NC}"
if curl -s http://localhost:2358/about &> /dev/null; then
    echo -e "${GREEN}✓ Judge0 API доступен${NC}\n"
else
    echo -e "${RED}✗ Judge0 API недоступен${NC}"
    echo "Проверьте логи: docker logs myapp-judge0"
    exit 1
fi

# 4. Тест выполнения кода (Python)
echo -e "${YELLOW}4. Тест выполнения Python кода...${NC}"
RESPONSE=$(curl -s -X POST http://localhost:2358/submissions?base64_encoded=false&wait=true \
  -H "Content-Type: application/json" \
  -d '{
    "source_code": "print(\"Hello from Judge0\")",
    "language_id": 71,
    "stdin": ""
  }')

STATUS=$(echo $RESPONSE | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
STDOUT=$(echo $RESPONSE | grep -o '"stdout":"[^"]*"' | sed 's/"stdout":"//;s/"$//')

if [ ! -z "$STDOUT" ]; then
    echo -e "${GREEN}✓ Python код выполнен успешно${NC}"
    echo "Вывод: $STDOUT"
else
    echo -e "${RED}✗ Ошибка выполнения${NC}"
    echo "Ответ: $RESPONSE"
fi
echo ""

# 5. Тест выполнения кода (Java)
echo -e "${YELLOW}5. Тест выполнения Java кода...${NC}"
JAVA_CODE='public class Main { public static void main(String[] args) { System.out.println("Hello from Java"); } }'
RESPONSE=$(curl -s -X POST http://localhost:2358/submissions?base64_encoded=false&wait=true \
  -H "Content-Type: application/json" \
  -d "{
    \"source_code\": \"$JAVA_CODE\",
    \"language_id\": 62,
    \"stdin\": \"\"
  }")

STDOUT=$(echo $RESPONSE | grep -o '"stdout":"[^"]*"' | sed 's/"stdout":"//;s/"$//')

if [ ! -z "$STDOUT" ]; then
    echo -e "${GREEN}✓ Java код выполнен успешно${NC}"
    echo "Вывод: $STDOUT"
else
    echo -e "${RED}✗ Ошибка выполнения${NC}"
    echo "Ответ: $RESPONSE"
fi
echo ""

# 6. Проверка БД
echo -e "${YELLOW}6. Проверка PostgreSQL...${NC}"
if docker exec -it myapp-postgres psql -U myapp -d myappdb -c '\dt' &> /dev/null; then
    echo -e "${GREEN}✓ PostgreSQL доступна${NC}"
else
    echo -e "${RED}✗ PostgreSQL недоступна${NC}"
fi
echo ""

echo -e "${GREEN}=== Проверка завершена ===${NC}"
