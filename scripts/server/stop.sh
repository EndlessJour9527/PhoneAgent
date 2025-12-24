#!/bin/bash
# Copyright (C) 2025 PhoneAgent Contributors
# Licensed under AGPL-3.0
#############################################################################
# PhoneAgent 服务停止脚本
# 用于停止所有服务（不使用 systemd）
#############################################################################

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

echo -e "${BLUE}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  PhoneAgent 服务停止                                      ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""

# 获取脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# 脚本在 scripts/ 目录下，需要往上一级到达项目根目录
PROJECT_DIR="$( cd "$SCRIPT_DIR/.." && pwd )"

log_info "项目目录: $PROJECT_DIR"
echo ""

# 检查是否有服务在运行（包括systemd服务）
FRPS_RUNNING=false
API_RUNNING=false
WS_RUNNING=false

# 检查systemd服务
if systemctl is-active --quiet autoglm-frps 2>/dev/null; then
    FRPS_RUNNING=true
elif pgrep -f "frps" > /dev/null; then
    FRPS_RUNNING=true
fi

if systemctl is-active --quiet autoglm-api 2>/dev/null; then
    API_RUNNING=true
elif pgrep -f "uvicorn server.api.app:app" > /dev/null; then
    API_RUNNING=true
fi

if systemctl is-active --quiet autoglm-websocket 2>/dev/null; then
    WS_RUNNING=true
elif pgrep -f "server.websocket.server" > /dev/null; then
    WS_RUNNING=true
fi

if [ "$FRPS_RUNNING" = false ] && [ "$API_RUNNING" = false ] && [ "$WS_RUNNING" = false ]; then
    log_warn "没有检测到运行中的服务"
    exit 0
fi

log_info "检测到运行中的服务，正在停止..."
echo ""

# 停止 FRP Server
if [ "$FRPS_RUNNING" = true ]; then
    log_info "[1/3] 停止 FRP Server..."
    
    # 先尝试停止systemd服务
    if systemctl is-active --quiet autoglm-frps 2>/dev/null; then
        systemctl stop autoglm-frps 2>/dev/null || true
        sleep 1
    fi
    
    # 再停止手动启动的进程（多种匹配方式）
    pkill -f "frps.*frps.ini" 2>/dev/null || true
    pkill -f "./frps -c" 2>/dev/null || true
    pkill frps 2>/dev/null || true
    sleep 1
    
    # 如果还在运行，强制停止
    if pgrep -f "frps" > /dev/null; then
        log_warn "  强制停止 FRP Server..."
        pkill -9 -f "frps" 2>/dev/null || true
        
        # 最后手段：通过端口杀死进程
        if command -v lsof &> /dev/null; then
            FRP_PIDS=$(lsof -ti :7000,7500 2>/dev/null)
            if [ ! -z "$FRP_PIDS" ]; then
                echo "$FRP_PIDS" | xargs kill -9 2>/dev/null || true
            fi
        fi
        fuser -k 7000/tcp 2>/dev/null || true
        fuser -k 7500/tcp 2>/dev/null || true
    fi
    
    log_info "  ✅ FRP Server 已停止"
else
    log_info "[1/3] FRP Server 未运行"
fi

# 停止 FastAPI Server
if [ "$API_RUNNING" = true ]; then
    log_info "[2/3] 停止 FastAPI Server..."
    
    # 先尝试停止systemd服务
    if systemctl is-active --quiet autoglm-api 2>/dev/null; then
        systemctl stop autoglm-api 2>/dev/null || true
        sleep 1
    fi
    
    # 再停止手动启动的进程
    pkill -f "uvicorn server.api.app:app" 2>/dev/null || true
    sleep 1
    
    if pgrep -f "uvicorn server.api.app:app" > /dev/null; then
        log_warn "  强制停止 FastAPI Server..."
        pkill -9 -f "uvicorn server.api.app:app" 2>/dev/null || true
    fi
    
    log_info "  ✅ FastAPI Server 已停止"
else
    log_info "[2/3] FastAPI Server 未运行"
fi

# 停止 WebSocket Server
if [ "$WS_RUNNING" = true ]; then
    log_info "[3/3] 停止 WebSocket Server..."
    
    # 先尝试停止systemd服务
    if systemctl is-active --quiet autoglm-websocket 2>/dev/null; then
        systemctl stop autoglm-websocket 2>/dev/null || true
        sleep 1
    fi
    
    # 再停止手动启动的进程
    pkill -f "server.websocket.server" 2>/dev/null || true
    sleep 1
    
    if pgrep -f "server.websocket.server" > /dev/null; then
        log_warn "  强制停止 WebSocket Server..."
        pkill -9 -f "server.websocket.server" 2>/dev/null || true
    fi
    
    log_info "  ✅ WebSocket Server 已停止"
else
    log_info "[3/3] WebSocket Server 未运行"
fi

sleep 2

# 清理端口（确保端口释放）
log_info "清理端口..."
for port in 7000 7500 8000 9999; do
    if command -v lsof &> /dev/null; then
        PORT_PIDS=$(lsof -ti :$port 2>/dev/null)
        if [ ! -z "$PORT_PIDS" ]; then
            echo "$PORT_PIDS" | xargs kill -9 2>/dev/null || true
        fi
    fi
    fuser -k $port/tcp 2>/dev/null || true
done

sleep 1

# 验证所有服务已停止
echo ""
log_info "验证服务状态..."

STILL_RUNNING=false

if pgrep -f "frps" > /dev/null; then
    log_error "  ❌ FRP Server 仍在运行"
    ps aux | grep frps | grep -v grep
    STILL_RUNNING=true
else
    log_info "  ✅ FRP Server 已停止"
fi

if pgrep -f "uvicorn server.api.app:app" > /dev/null; then
    log_error "  ❌ FastAPI Server 仍在运行"
    STILL_RUNNING=true
else
    log_info "  ✅ FastAPI Server 已停止"
fi

if pgrep -f "server.websocket.server" > /dev/null; then
    log_error "  ❌ WebSocket Server 仍在运行"
    STILL_RUNNING=true
else
    log_info "  ✅ WebSocket Server 已停止"
fi

echo ""

if [ "$STILL_RUNNING" = true ]; then
    log_error "部分服务未能完全停止，请手动检查:"
    echo ""
    echo "  查看进程: ps aux | grep -E '(frps|uvicorn|websocket)' | grep -v grep"
    echo "  手动停止: kill -9 <PID>"
    exit 1
else
    echo -e "${GREEN}╔═══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║  ✅ 所有服务已成功停止!                                    ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════════════╝${NC}"
    echo ""
    log_info "重新启动服务: bash scripts/start_server.sh"
fi

echo ""

