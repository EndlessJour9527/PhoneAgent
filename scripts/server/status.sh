#!/bin/bash
# Copyright (C) 2025 PhoneAgent Contributors
# Licensed under AGPL-3.0
#############################################################################
# 查看PhoneAgent服务状态
#############################################################################

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "   PhoneAgent 服务状态"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# API服务器
echo "📡 API服务器 (FastAPI)"
API_PID=$(pgrep -f "uvicorn.*server.api.app:app" | head -1)
if [ -n "$API_PID" ]; then
    echo -e "  ${GREEN}● 运行中${NC} (PID: $API_PID)"
else
    echo -e "  ${RED}● 未运行${NC}"
fi

# WebSocket
echo "🔌 WebSocket服务器"
WS_PID=$(pgrep -f "python.*server.websocket.server" | head -1)
if [ -n "$WS_PID" ]; then
    echo -e "  ${GREEN}● 运行中${NC} (PID: $WS_PID)"
else
    echo -e "  ${RED}● 未运行${NC}"
fi

# FRP
echo "🌐 FRP服务器"
FRP_PID=$(pgrep -f "frps" | head -1)
if [ -n "$FRP_PID" ]; then
    echo -e "  ${GREEN}● 运行中${NC} (PID: $FRP_PID)"
else
    echo -e "  ${RED}● 未运行${NC}"
fi

echo ""

