#!/usr/bin/env bash
# Input: local Docker daemon (running containers on ports 3306/6379)
# Output: pass/fail report on whether xiyu project ports are occupied by foreign containers
# Pos: scripts/ - preflight helper for dev-services startup (avoid cross-project port conflicts)
# 维护声明: 若端口分配规范变更（如 nexus 端口段调整），同步更新 CLAUDE.md §多项目端口分配 和本脚本。
# 端口冲突预检脚本 - 检测 xiyu 项目端口是否被其他项目占用
#
# 用途：在启动 dev-services 前运行，提前发现端口冲突
# 调用：bash scripts/check-port-conflicts.sh
# 返回：0=无冲突，1=有冲突
#
# 端口分配规范（见 CLAUDE.md §多项目端口分配）：
#   xiyu 项目：  3306 (MySQL), 6379 (Redis), 18089 (后端), 1323 (前端), 8009 (sidecar)
#   nexus 项目：3307 (MySQL), 26380 (Redis)
#   其他项目：   请使用 3xxxx 段端口，避免与 xiyu 标准端口冲突

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# xiyu 项目专用容器名
XIYU_MYSQL_CONTAINER="xiyu-bid-local-mysql"
XIYU_REDIS_CONTAINER="xiyu-bid-local-redis"

# xiyu 项目专用端口
XIYU_MYSQL_PORT=3306
XIYU_REDIS_PORT=6379

conflicts=0

echo "=== 端口冲突预检 ==="
echo ""

# 检查 3306 端口
check_port_3306() {
    local container_on_port
    container_on_port=$(docker ps --filter "publish=3306" --format "{{.Names}}" 2>/dev/null | head -1)

    if [[ -n "$container_on_port" ]]; then
        if [[ "$container_on_port" == "$XIYU_MYSQL_CONTAINER" ]]; then
            echo -e "${GREEN}✓${NC} 端口 3306: xiyu MySQL 容器正常 ($container_on_port)"
        else
            echo -e "${RED}✗ 端口 3306 被非 xiyu 容器占用: $container_on_port${NC}"
            echo -e "  ${YELLOW}解决方案:${NC}"
            echo -e "    1. 停掉冲突容器: docker stop $container_on_port"
            echo -e "    2. 或让该容器改用其他端口（如 nexus 项目应改用 23306）"
            echo -e "    3. 重启 xiyu 服务: docker compose up -d (在项目根目录)"
            conflicts=$((conflicts + 1))
        fi
    else
        # 端口未被任何容器占用，检查是否有 xiyu 容器已退出
        if docker ps -a --filter "name=$XIYU_MYSQL_CONTAINER" --format "{{.Status}}" | grep -q "Exited"; then
            echo -e "${YELLOW}⚠${NC} 端口 3306 空闲，但 xiyu MySQL 容器已退出"
            echo -e "  启动: docker compose up -d 或 docker start $XIYU_MYSQL_CONTAINER"
        else
            echo -e "${YELLOW}⚠${NC} 端口 3306 空闲，xiyu MySQL 容器未创建"
            echo -e "  启动: docker compose up -d"
        fi
    fi
}

# 检查 6379 端口
check_port_6379() {
    local container_on_port
    container_on_port=$(docker ps --filter "publish=6379" --format "{{.Names}}" 2>/dev/null | head -1)

    if [[ -n "$container_on_port" ]]; then
        if [[ "$container_on_port" == "$XIYU_REDIS_CONTAINER" ]]; then
            echo -e "${GREEN}✓${NC} 端口 6379: xiyu Redis 容器正常 ($container_on_port)"
        else
            echo -e "${RED}✗ 端口 6379 被非 xiyu 容器占用: $container_on_port${NC}"
            echo -e "  ${YELLOW}解决方案:${NC}"
            echo -e "    1. 停掉冲突容器: docker stop $container_on_port"
            echo -e "    2. 或让该容器改用其他端口（如 nexus 项目应改用 26379）"
            echo -e "    3. 重启 xiyu 服务: docker compose up -d (在项目根目录)"
            conflicts=$((conflicts + 1))
        fi
    else
        if docker ps -a --filter "name=$XIYU_REDIS_CONTAINER" --format "{{.Status}}" | grep -q "Exited"; then
            echo -e "${YELLOW}⚠${NC} 端口 6379 空闲，但 xiyu Redis 容器已退出"
            echo -e "  启动: docker compose up -d 或 docker start $XIYU_REDIS_CONTAINER"
        else
            echo -e "${YELLOW}⚠${NC} 端口 6379 空闲，xiyu Redis 容器未创建"
            echo -e "  启动: docker compose up -d"
        fi
    fi
}

# 检查 xiyu 容器健康状态
check_container_health() {
    local mysql_health
    mysql_health=$(docker inspect --format='{{.State.Health.Status}}' $XIYU_MYSQL_CONTAINER 2>/dev/null || echo "not-found")

    if [[ "$mysql_health" == "healthy" ]]; then
        echo -e "${GREEN}✓${NC} MySQL 容器健康"
    elif [[ "$mysql_health" == "not-found" ]]; then
        : # 已在前面的端口检查中提示
    else
        echo -e "${YELLOW}⚠${NC} MySQL 容器健康状态: $mysql_health（等待中...）"
    fi

    # Redis 用 redis-cli ping 检查（容器可能无 healthcheck）
    local redis_ping
    redis_ping=$(docker exec $XIYU_REDIS_CONTAINER redis-cli ping 2>/dev/null || echo "FAIL")
    if [[ "$redis_ping" == "PONG" ]]; then
        echo -e "${GREEN}✓${NC} Redis 容器健康 (PONG)"
    fi
}

check_port_3306
check_port_6379
check_container_health

echo ""
if [[ $conflicts -gt 0 ]]; then
    echo -e "${RED}✗ 发现 $conflicts 个端口冲突，请先解决再启动服务${NC}"
    exit 1
else
    echo -e "${GREEN}✓ 无端口冲突${NC}"
    exit 0
fi
