# Docker 部署指南

## 📦 快速开始

### 第1步：准备配置

```bash
# 复制配置文件（首次）
cp .env.docker .env

# 编辑 .env，填写 ZHIPU_API_KEY
# 访问 https://open.bigmodel.cn/ 获取 API Key
nano .env
```

### 第2步：使用快速启动脚本

```bash
# 赋予执行权限
chmod +x docker-start.sh

# 运行启动脚本（自动构建、启动、验证）
./docker-start.sh
```

### 第3步：验证服务

```bash
# 查看所有容器
docker compose ps

# 查看实时日志
docker compose logs -f

# 测试 API 健康检查
curl http://localhost:8000/health

# 测试 WebSocket 健康检查
curl http://localhost:9999/health

# 访问 API 文档
# 浏览器打开: http://localhost:8000/docs
```

---

## 🌐 局域网访问

### 自动检测（推荐）

Docker 启动脚本会自动检测您的局域网 IP，在启动日志中显示：

```
🌐 本机 IP: 192.168.1.100
🌐 局域网访问: http://192.168.1.100:8000/docs
```

### 手动配置

如果自动检测失败，编辑 `.env` 文件：

```bash
# 设置您的宿主机局域网 IP
WEBSOCKET_HOST=192.168.1.100
VITE_API_HOST=192.168.1.100

# 重启容器
docker compose restart
```

---

## 📱 Android 客户端配置

在 PhoneAgent Remote 应用中填写：

| 字段 | 值 |
|------|-----|
| 后端服务器 IP | `192.168.1.100`（您的宿主机 IP） |
| FRP 服务器 | `192.168.1.100` |
| FRP Token | 查看 `.env` 中的 `FRP_TOKEN` |
| FRP 远程端口 | `6100`（每台设备唯一） |
| WebSocket 连接方式 | 直连 IP 模式 |

---

## 🛠️ 常用命令

```bash
# 启动容器
docker compose up -d

# 停止容器
docker compose down

# 重启容器
docker compose restart

# 查看日志
docker compose logs -f

# 进入容器
docker compose exec phoneagent bash

# 查看容器状态
docker compose ps

# 查看容器资源使用
docker stats phoneagent-server

# 完全重建（删除缓存）
docker compose down
docker compose build --no-cache
docker compose up -d
```

---

## 📊 监控日志

```bash
# 查看所有日志
docker compose logs phoneagent

# 实时查看
docker compose logs -f phoneagent

# 查看最后 100 行
docker compose logs --tail 100 phoneagent

# 查看特定日期
docker compose logs --since 2024-01-14 phoneagent
```

---

## 🔧 调试

### 进入容器调试

```bash
docker compose exec phoneagent bash

# 容器内查看配置
echo $WEBSOCKET_HOST
echo $CORS_ORIGINS

# 查看日志文件
tail -100f logs/api.log
tail -100f logs/websocket.log
tail -100f logs/frps.log

# 测试 ADB
adb devices

# 测试网络连接
curl http://localhost:8000/health
curl http://localhost:9999/health
```

### 问题排查

```bash
# 问题1：容器无法启动
docker compose logs phoneagent

# 问题2：API 无法连接
curl -v http://localhost:8000/health

# 问题3：防火墙阻止
sudo ufw allow 8000/tcp
sudo ufw allow 9999/tcp
sudo ufw allow 7000/tcp
sudo ufw allow 6100:6199/tcp

# 问题4：IP 检测失败
# 手动编辑 .env 设置 WEBSOCKET_HOST 和 VITE_API_HOST

# 问题5：重建镜像
docker compose build --no-cache --pull
```

---

## 📦 文件说明

| 文件 | 说明 |
|------|------|
| `Dockerfile` | Docker 镜像构建配置 |
| `docker-compose.yml` | Docker Compose 编排配置 |
| `docker-entrypoint.sh` | 容器启动脚本（自动检测 IP） |
| `.env.docker` | 环境变量模板 |
| `.env` | 实际使用的环境变量（由 .env.docker 复制） |
| `docker-start.sh` | 快速启动脚本 |

---

## 🔐 安全建议

1. **修改默认密码**
   ```bash
   # .env 中修改 FRP Dashboard 密码
   FRP_DASHBOARD_PWD=your_strong_password
   ```

2. **设置强 FRP Token**
   ```bash
   # .env 中设置复杂的 Token
   FRP_TOKEN=your_complex_token_string
   ```

3. **限制 CORS 来源**
   ```bash
   # 只允许特定域名
   CORS_ORIGINS=http://192.168.1.100:5173,https://your-domain.com
   ```

4. **防火墙配置**
   ```bash
   # 只开放必要的端口给信任的网络
   sudo ufw allow from 192.168.1.0/24 to any port 8000
   ```

---

## 📈 性能优化

### 资源限制

编辑 `docker-compose.yml`，取消注释 `deploy` 部分：

```yaml
deploy:
  resources:
    limits:
      cpus: '2'
      memory: 4G
    reservations:
      cpus: '1'
      memory: 2G
```

### 日志清理

容器会自动清理 7 天前的截图和 30 天前的日志。

---

## 🆘 获取帮助

```bash
# 查看完整日志
docker compose logs phoneagent > debug.log

# 导出容器配置
docker compose config > docker-compose-resolved.yml

# 查看容器资源
docker stats phoneagent-server

# 检查网络
docker network inspect phoneagent-server_phoneagent-network
```

---

## ✅ 验证清单

- [ ] Docker 和 Docker Compose 已安装
- [ ] `.env` 文件已创建并填写 ZHIPU_API_KEY
- [ ] `docker-start.sh` 已执行
- [ ] API 可在 `http://localhost:8000/docs` 访问
- [ ] 局域网 IP 已正确检测
- [ ] 防火墙已开放必要端口
- [ ] Android 客户端已配置
- [ ] 设备已执行 `adb tcpip 5555`

---

祝您使用愉快！🎉
