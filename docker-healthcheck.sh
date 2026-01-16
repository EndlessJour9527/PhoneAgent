#!/bin/bash

# ============================================
# PhoneAgent Docker 健康检查脚本
# ============================================

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  PhoneAgent Docker 健康检查${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""

# ============================================
# 1. 检查 Docker 和容器
# ============================================
echo -e "${BLUE}1️⃣  检查 Docker 环境${NC}"
echo ""

if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker 未安装${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker 已安装${NC}"

if ! docker ps > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker 守护进程未运行${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker 守护进程运行中${NC}"

# 检查容器是否存在
if ! docker ps -a --format '{{.Names}}' | grep -q '^phoneagent-server$'; then
    echo -e "${YELLOW}⚠️  phoneagent-server 容器不存在，请先运行 ./docker-start.sh${NC}"
    exit 1
fi
echo -e "${GREEN}✓ phoneagent-server 容器存在${NC}"

# 检查容器是否运行
if ! docker ps --format '{{.Names}}' | grep -q '^phoneagent-server$'; then
    echo -e "${RED}❌ phoneagent-server 容器未运行${NC}"
    docker compose up -d
    echo -e "${YELLOW}   容器已启动，请等待 15 秒重试${NC}"
    exit 1
fi
echo -e "${GREEN}✓ phoneagent-server 容器运行中${NC}"
echo ""

# ============================================
# 2. 检查服务健康状态
# ============================================
echo -e "${BLUE}2️⃣  检查服务健康状态${NC}"
echo ""

# API Server
echo -n "检查 API Server... "
if curl -s -m 2 http://localhost:8000/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 正常${NC}"
else
    echo -e "${RED}✗ 异常${NC}"
fi

# WebSocket Server
echo -n "检查 WebSocket Server... "
if curl -s -m 2 http://localhost:9999/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 正常${NC}"
else
    echo -e "${RED}✗ 异常${NC}"
fi

# FRP Server
echo -n "检查 FRP Server... "
if nc -z -w 1 localhost 7001 > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 正常${NC}"
else
    echo -e "${YELLOW}⚠️  无法连接（可能在容器内运行）${NC}"
fi

echo ""

# ============================================
# 3. 检查端口映射
# ============================================
echo -e "${BLUE}3️⃣  检查端口映射${NC}"
echo ""

docker compose ps | grep phoneagent-server || true
echo ""

# ============================================
# 4. 检查环境变量
# ============================================
echo -e "${BLUE}4️⃣  检查环境变量配置${NC}"
echo ""

if [ -f ".env" ]; then
    echo "✓ .env 文件存在"
    
    # 检查 API Key
    if grep -q "ZHIPU_API_KEY=" .env; then
        API_KEY=$(grep "^ZHIPU_API_KEY=" .env | cut -d'=' -f2)
        if [ "$API_KEY" != "" ] && [ "$API_KEY" != "your_api_key_here" ]; then
            echo -e "${GREEN}✓ ZHIPU_API_KEY 已配置${NC}"
        else
            echo -e "${YELLOW}⚠️  ZHIPU_API_KEY 未配置${NC}"
        fi
    fi
    
    # 检查 FRP Token
    FRP_TOKEN=$(grep "^FRP_TOKEN=" .env | cut -d'=' -f2)
    echo "  FRP Token: $FRP_TOKEN"
else
    echo -e "${YELLOW}⚠️  .env 文件不存在${NC}"
fi

echo ""

# ============================================
# 5. 检查容器日志
# ============================================
echo -e "${BLUE}5️⃣  检查容器日志${NC}"
echo ""

# 检查最近的错误
echo "📋 最近日志（最后 10 行）:"
docker compose logs --tail 10 phoneagent 2>&1 | tail -10

echo ""

# ============================================
# 6. 网络配置
# ============================================
echo -e "${BLUE}6️⃣  网络配置${NC}"
echo ""

# 获取容器 IP
CONTAINER_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' phoneagent-server 2>/dev/null || echo "unknown")
echo "容器 IP: $CONTAINER_IP"

# 获取宿主机 IP（如果可能）
if command -v hostname &> /dev/null; then
    HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "unknown")
    if [ "$HOST_IP" != "unknown" ]; then
        echo "宿主机 IP: $HOST_IP"
        echo ""
        echo "局域网访问地址:"
        echo "  API 文档:     http://${HOST_IP}:8000/docs"
        echo "  WebSocket:    ws://${HOST_IP}:9999"
    fi
fi

echo ""

# ============================================
# 7. 总结
# ============================================
echo -e "${GREEN}✅ 健康检查完成！${NC}"
echo ""
echo "后续步骤:"
echo "  1. 访问 API 文档: http://localhost:8000/docs"
echo "  2. 查看实时日志: docker compose logs -f"
echo "  3. 配置 Android 客户端"
echo "  4. 在设备上执行: adb tcpip 5555"
echo ""
