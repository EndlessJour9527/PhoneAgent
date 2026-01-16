# 🚀 PhoneAgent Docker 快速参考

## ⚡ 5 秒钟启动

```bash
# 1. 准备配置
cp .env.docker .env
# 编辑 .env，填写 ZHIPU_API_KEY

# 2. 启动
chmod +x docker-start.sh
./docker-start.sh

# 3. 访问
curl http://localhost:8000/health  ✅ 本地
curl http://192.168.x.x:8000/health ✅ 局域网
```

---

## 📍 局域网访问

| 用途 | 地址 | 说明 |
|------|------|------|
| **API 文档** | `http://192.168.x.x:8000/docs` | 自动检测的局域网 IP |
| **WebSocket** | `ws://192.168.x.x:9999` | 设备连接点 |
| **FRP Server** | `192.168.x.x:7001` | 设备 FRP 客户端连接 |
| **FRP Dashboard** | `http://192.168.x.x:7500` | 监控面板 (admin/admin123) |

---

## 🎯 Android 客户端配置

```
后端服务器 IP:     192.168.x.x
FRP Token:         看 .env 中的 FRP_TOKEN
FRP 远程端口:      6100
WebSocket 连接方式: 直连 IP 模式
```

---

## 🔧 常用命令

```bash
# 查看状态
docker compose ps

# 查看日志
docker compose logs -f

# 重启
docker compose restart

# 停止
docker compose down

# 进入容器
docker compose exec phoneagent bash
```

---

## ⚠️ 关键步骤

```bash
# ✅ 设备上执行（首次和重启后都需要）
adb tcpip 5555

# ✅ 检查防火墙
sudo ufw allow 8000/tcp
sudo ufw allow 9999/tcp
sudo ufw allow 7001/tcp
sudo ufw allow 6100:6199/tcp

# ✅ 验证网络连通
ping 192.168.x.x
```

---

## 📋 文件说明

| 文件 | 说明 |
|------|------|
| `Dockerfile` | 镜像配置 |
| `docker-compose.yml` | 编排配置 |
| `docker-entrypoint.sh` | 启动脚本（自动检测 IP） |
| `.env.docker` | 配置模板 |
| `.env` | 实际配置（首次从 .env.docker 复制） |
| `docker-start.sh` | 一键启动脚本 |
| `DOCKER.md` | 详细文档 |

---

## 🆘 常见问题

### 🔴 问题：IP 检测失败

**解决**：编辑 `.env`，手动设置：
```bash
WEBSOCKET_HOST=192.168.1.100
VITE_API_HOST=192.168.1.100
```

### 🔴 问题：设备显示离线

**解决**：在设备上执行
```bash
adb tcpip 5555
```

### 🔴 问题：防火墙阻止

**解决**：开放端口
```bash
sudo ufw allow 8000/tcp
sudo ufw allow 9999/tcp  
sudo ufw allow 7001/tcp
sudo ufw allow 6100:6199/tcp
```

---

详细文档见 [DOCKER.md](DOCKER.md)
