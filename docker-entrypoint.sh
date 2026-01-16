#!/bin/bash
set -e

# ============================================
# PhoneAgent Docker Entrypoint
# 支持局域网访问
# ============================================

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  PhoneAgent Docker Startup${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# ============================================
# 0. 自动检测本机局域网 IP
# ============================================
detect_local_ip() {
    # 尝试多种方法检测本机 IP
    
    # 方法1：通过 ip 命令获取主机网络（Docker 内外都适用）
    if command -v ip &> /dev/null; then
        # 获取非 loopback 的第一个 IP
        local_ip=$(ip route get 1 2>/dev/null | awk '{print $NF;exit}')
        if [ ! -z "$local_ip" ] && [ "$local_ip" != "127.0.0.1" ]; then
            echo "$local_ip"
            return 0
        fi
    fi
    
    # 方法2：通过 hostname 命令
    if command -v hostname &> /dev/null; then
        local_ip=$(hostname -I 2>/dev/null | awk '{print $1}')
        if [ ! -z "$local_ip" ] && [ "$local_ip" != "127.0.0.1" ]; then
            echo "$local_ip"
            return 0
        fi
    fi
    
    # 方法3：通过 ifconfig（需要安装）
    if command -v ifconfig &> /dev/null; then
        local_ip=$(ifconfig 2>/dev/null | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}' | head -1)
        if [ ! -z "$local_ip" ]; then
            echo "$local_ip"
            return 0
        fi
    fi
    
    # 默认回退
    echo "localhost"
}

LOCAL_IP=$(detect_local_ip)
echo -e "${BLUE}🌐 检测到本机 IP: ${LOCAL_IP}${NC}"
echo ""

# ============================================
# 1. 检查 .env 配置
# ============================================
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}⚠️  .env 文件不存在，复制 env.example${NC}"
    cp env.example .env
fi

# 检查必需的 API Key
if ! grep -q "ZHIPU_API_KEY\|CUSTOM_API_KEY" .env || \
   (grep "^ZHIPU_API_KEY=" .env | grep -q "=$" && grep "^CUSTOM_API_KEY=" .env | grep -q "=$"); then
    echo -e "${RED}❌ 错误：必须设置 ZHIPU_API_KEY 或 CUSTOM_API_KEY${NC}"
    echo -e "${YELLOW}   编辑 .env 文件，访问 https://open.bigmodel.cn/ 获取密钥${NC}"
    exit 1
fi

# 加载环境变量
set -a
source .env
set +a

# ============================================
# 2. 自动配置局域网相关变量
# ============================================
echo "⚙️  配置局域网访问..."

# 如果没有设置 WEBSOCKET_HOST，自动使用检测到的 IP
if [ -z "$WEBSOCKET_HOST" ] || [ "$WEBSOCKET_HOST" = "127.0.0.1" ] || [ "$WEBSOCKET_HOST" = "localhost" ]; then
    export WEBSOCKET_HOST="$LOCAL_IP"
    echo -e "   ${GREEN}✓ WEBSOCKET_HOST=$WEBSOCKET_HOST${NC}"
fi

# 如果没有设置 VITE_API_HOST，自动使用检测到的 IP
if [ -z "$VITE_API_HOST" ] || [ "$VITE_API_HOST" = "localhost" ]; then
    export VITE_API_HOST="$LOCAL_IP"
    echo -e "   ${GREEN}✓ VITE_API_HOST=$VITE_API_HOST${NC}"
fi

# 配置 CORS_ORIGINS 包含本机 IP
if ! echo "$CORS_ORIGINS" | grep -q "$LOCAL_IP"; then
    export CORS_ORIGINS="${CORS_ORIGINS},http://${LOCAL_IP}:5173,http://${LOCAL_IP}:8000"
    echo -e "   ${GREEN}✓ CORS_ORIGINS 已添加 $LOCAL_IP${NC}"
fi

echo -e "${GREEN}✅ 配置已加载${NC}"
echo ""

# ============================================
# 3. 创建必要目录
# ============================================
echo "📁 创建目录结构..."
mkdir -p data/screenshots logs frp

# ============================================
# 4. 准备 FRP 配置
# ============================================
echo "🔧 准备 FRP 配置..."
cp -r /app-frp/* frp/ 2>/dev/null || true

if [ ! -f "frp/frps.ini" ]; then
    echo "   生成 frps.ini..."
    FRP_TOKEN=${FRP_TOKEN:-changeme}
    cat > frp/frps.ini <<EOF
[common]
bind_port = ${FRP_PORT:-7000}
dashboard_port = 7500
dashboard_user = admin
dashboard_pwd = ${FRP_DASHBOARD_PWD:-admin123}
token = ${FRP_TOKEN}
log_file = ./frps.log
log_level = info
allow_ports = 6100-6199
max_pool_count = 50
tcp_mux = true
EOF
    echo -e "${YELLOW}⚠️  FRP Token: ${FRP_TOKEN}${NC}"
fi

# ============================================
# 5. 初始化数据库
# ============================================
echo ""
echo "🗄️  初始化数据库..."
python -c "
from server.database import init_database
init_database()
print('✅ 数据库初始化完成')
" || echo -e "${YELLOW}⚠️  数据库初始化跳过${NC}"

# ============================================
# 6. 启动服务
# ============================================
echo ""
echo -e "${GREEN}🚀 启动服务...${NC}"
echo ""

# 6.1 启动 FRP Server
echo "  1️⃣  启动 FRP Server (端口: ${FRP_PORT:-7000})..."
cd frp
./frps -c frps.ini > ../logs/frps.log 2>&1 &
FRP_PID=$!
echo -e "     ${GREEN}✓ FRP PID: $FRP_PID${NC}"
cd ..

sleep 1

# 6.2 启动 WebSocket Server
echo "  2️⃣  启动 WebSocket Server (端口: ${WEBSOCKET_PORT:-9999})..."
python -m server.websocket.server > logs/websocket.log 2>&1 &
WS_PID=$!
echo -e "     ${GREEN}✓ WebSocket PID: $WS_PID${NC}"

echo "     等待 WebSocket 就绪..."
for i in {1..30}; do
    if curl -s http://127.0.0.1:${WEBSOCKET_PORT:-9999}/health > /dev/null 2>&1; then
        echo -e "     ${GREEN}✓ WebSocket 已就绪${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "     ${YELLOW}⚠️  WebSocket 启动超时，继续...${NC}"
    fi
    sleep 1
done

# 6.3 启动 API Server
echo "  3️⃣  启动 API Server (端口: 8000)..."
uvicorn server.api.app:app \
    --host 0.0.0.0 \
    --port 8000 \
    --log-level info > logs/api.log 2>&1 &
API_PID=$!
echo -e "     ${GREEN}✓ API PID: $API_PID${NC}"

sleep 2

# ============================================
# 7. 启动成功提示
# ============================================
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  ✅ PhoneAgent 启动完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "🌐 本地访问 (localhost):"
echo "   API 文档:     http://localhost:8000/docs"
echo "   WebSocket:    ws://localhost:9999"
echo ""
echo -e "${BLUE}🌐 局域网访问 ($LOCAL_IP):${NC}"
echo "   API 文档:     http://${LOCAL_IP}:8000/docs"
echo "   WebSocket:    ws://${LOCAL_IP}:9999"
echo "   FRP 服务器:   ${LOCAL_IP}:7001  (容器内部:7000)"
echo "   FRP Dashboard: http://${LOCAL_IP}:7500"
echo ""
echo "📋 日志位置:"
echo "   API:       logs/api.log"
echo "   WebSocket: logs/websocket.log"
echo "   FRP:       logs/frps.log"
echo ""
echo "🎯 下一步:"
echo "   1️⃣  查看 logs/api.log 验证服务启动"
echo "   2️⃣  访问 http://${LOCAL_IP}:8000/docs 查看 API 文档"
echo "   3️⃣  配置 Android 客户端:"
echo "       • 后端服务器 IP: ${LOCAL_IP}"
echo "       • FRP 服务器: ${LOCAL_IP}"
echo "       • FRP Token: $(grep '^token = ' frp/frps.ini | cut -d'=' -f2 | xargs)"
echo ""
echo -e "${YELLOW}⚠️  重要提醒:${NC}"
echo "   • 每台设备需执行: adb tcpip 5555"
echo "   • 确保设备和宿主在同一局域网"
echo "   • 如果无法连接，检查防火墙是否开放端口"
echo ""

# ============================================
# 8. 保持容器运行
# ============================================
wait $FRP_PID $WS_PID $API_PID
