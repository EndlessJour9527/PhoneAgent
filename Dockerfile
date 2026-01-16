# Dockerfile
FROM python:3.10-slim

# ============================================
# 安装系统依赖
# ============================================
RUN apt-get update && apt-get install -y --no-install-recommends \
    android-tools-adb \
    ffmpeg \
    curl \
    procps \
    netcat-openbsd \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# ============================================
# 下载 FRP 二进制（根据架构自动选择）
# ============================================
ARG FRP_VERSION=0.52.0
RUN if [ "$(uname -m)" = "aarch64" ]; then \
      FRP_ARCH=arm64; \
    else \
      FRP_ARCH=amd64; \
    fi && \
    mkdir -p /tmp/frp && \
    cd /tmp/frp && \
    curl -fsSL "https://github.com/fatedier/frp/releases/download/v${FRP_VERSION}/frp_${FRP_VERSION}_linux_${FRP_ARCH}.tar.gz" \
    | tar -xz && \
    mkdir -p /app-frp && \
    cp frp_${FRP_VERSION}_linux_${FRP_ARCH}/* /app-frp/ && \
    chmod +x /app-frp/frps && \
    rm -rf /tmp/frp

# ============================================
# Python 应用设置
# ============================================
WORKDIR /app

# 复制依赖文件
COPY requirements.txt ./

# 创建虚拟环境并安装依赖
RUN python -m venv /venv && \
    . /venv/bin/activate && \
    pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# 复制项目代码
COPY . .

# ============================================
# 环境变量
# ============================================
ENV PATH="/venv/bin:${PATH}" \
    SERVER_HOST=0.0.0.0 \
    PYTHONUNBUFFERED=1

# ============================================
# 暴露端口
# ============================================
# 8000: API Server + Frontend WebSocket + Scrcpy WebSocket
# 9999: Device WebSocket
# 7000: FRP Server
# 7500: FRP Dashboard
# 6100-6199: FRP Client Ports (devices)
EXPOSE 8000 9999 7000 7500 6100-6199

# ============================================
# 启动脚本
# ============================================
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

ENTRYPOINT ["/app/docker-entrypoint.sh"]
