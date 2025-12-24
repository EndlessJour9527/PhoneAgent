#!/bin/bash
# Copyright (C) 2025 PhoneAgent Contributors
# Licensed under AGPL-3.0
#############################################################################
# 重启PhoneAgent服务器
#############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🔄 重启PhoneAgent服务器..."
echo ""

bash "$SCRIPT_DIR/stop.sh"
echo ""
echo "⏳ 等待2秒..."
sleep 2
echo ""
bash "$SCRIPT_DIR/start.sh"

echo ""
echo "✅ 服务器重启完成！"

