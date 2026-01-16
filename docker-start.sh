#!/bin/bash

# ============================================
# PhoneAgent Docker 快速启动脚本
# ============================================

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  PhoneAgent Docker 快速启动${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""

# ============================================
# 1. 检查前置条件
# ============================================
echo "📋 检查前置条件..."

if ! command -v docker &> /dev/null; then
    echo "❌ Docker 未安装，请先安装 Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose 未安装"
    exit 1
fi

echo "✅ Docker 和 Docker Compose 已安装"
echo ""

# ============================================
# 2. 准备 .env 文件
# ============================================
echo "📝 准备配置文件..."

if [ ! -f ".env" ]; then
    echo "   复制 .env.docker 为 .env..."
    cp .env.docker .env
    echo -e "${YELLOW}⚠️  请编辑 .env 文件，至少填写 ZHIPU_API_KEY${NC}"
    echo "   访问: https://open.bigmodel.cn/ 获取 API Key"
    echo ""
    read -p "按 Enter 继续或 Ctrl+C 取消..."
    echo ""
fi

# 检查 API Key
if grep -q "ZHIPU_API_KEY=your_api_key_here" .env; then
    echo -e "${YELLOW}⚠️  警告：ZHIPU_API_KEY 未配置，使用默认值${NC}"
fi

# ============================================
# 3. 构建镜像
# ============================================
echo -e "${GREEN}🔨 构建 Docker 镜像...${NC}"
docker compose build --no-cache

# ============================================
# 4. 启动容器
# ============================================
echo ""
echo -e "${GREEN}🚀 启动容器...${NC}"
docker compose up -d

# ============================================
# 5. 等待服务启动
# ============================================
echo ""
echo "⏳ 等待服务启动（15秒）..."
sleep 15

# ============================================
# 6. 验证服务
# ============================================
echo ""
echo -e "${GREEN}✅ 验证服务状态...${NC}"

# 获取容器 IP（用于局域网访问）
CONTAINER_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' phoneagent-server 2>/dev/null || echo "localhost")

echo ""
echo "📊 服务状态:"
if curl -s http://localhost:8000/health > /dev/null 2>&1; then
    echo -e "   ${GREEN}✓ API Server${NC}        http://localhost:8000"
else
    echo -e "   ❌ API Server        http://localhost:8000"
fi

if curl -s http://localhost:9999/health > /dev/null 2>&1; then
    echo -e "   ${GREEN}✓ WebSocket Server${NC}  ws://localhost:9999"
else
    echo -e "   ❌ WebSocket Server  ws://localhost:9999"
fi

echo ""
echo -e "${BLUE}🌐 访问地址:${NC}"
echo "   本地:   http://localhost:8000/docs"
echo "   局域网: http://${CONTAINER_IP}:8000/docs"
echo ""

# 尝试获取宿主机 IP
if command -v hostname &> /dev/null; then
    HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "")
    if [ ! -z "$HOST_IP" ]; then
        echo "   宿主机 IP: ${HOST_IP}"
    fi
fi

echo ""
echo -e "${GREEN}📋 日志查看:${NC}"
echo "   docker compose logs -f phoneagent"
echo ""
echo -e "${GREEN}🎯 下一步:${NC}"
echo "   1. 查看 API 文档: http://localhost:8000/docs"
echo "   2. 配置 Android 客户端"
echo "   3. 查看日志: docker compose logs -f"
echo ""
echo -e "${YELLOW}⚠️  重要提醒:${NC}"
echo "   • 每台设备需执行: adb tcpip 5555"
echo "   • 使用 FRP Token: $(grep '^FRP_TOKEN=' .env | cut -d'=' -f2)"
echo "   • 防火墙需开放端口: 8000, 9999, 7000, 6100-6199"
echo ""
