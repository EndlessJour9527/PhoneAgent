# PhoneAgent Docker 命令速查表

## 🚀 启动服务

初次使用（完整流程）：
```bash
1. cp .env.docker .env          # 创建配置
2. nano .env                    # 编辑配置（填写 ZHIPU_API_KEY）
3. ./docker-start.sh            # 一键启动
```

快速启动（已配置）：
```bash
docker compose up -d
```

快速停止：
```bash
docker compose down
```

---

## 📊 查看状态

```bash
docker compose ps               # 显示运行状态
docker stats phoneagent-server  # 实时资源使用
docker compose logs             # 查看所有日志
docker compose logs -f          # 实时日志
docker compose logs --tail 50   # 显示最后 50 行
./docker-healthcheck.sh         # 一键检查所有服务
```

---

## 🔧 进入容器

```bash
docker compose exec phoneagent bash              # 进入 shell
docker compose exec phoneagent env | grep WEBSOCKET  # 查看配置
docker compose exec phoneagent tail -100f logs/api.log  # 查看日志
```

---

## 🧹 清理和维护

```bash
docker compose restart          # 重启服务
docker compose down             # 停止（保留数据）
docker compose down -v          # 停止（删除数据）
docker compose build --no-cache # 完全重建
docker system prune -a          # 清理所有未使用的镜像
```

---

## 🌐 访问服务

本地访问：
- API 文档:  `http://localhost:8000/docs`
- WebSocket: `ws://localhost:9999`
- FRP 控制:  `http://localhost:7500` (admin/admin123)

局域网访问（192.168.x.x 替换为宿主机 IP）：
- API 文档:  `http://192.168.x.x:8000/docs`
- WebSocket: `ws://192.168.x.x:9999`
- FRP 控制:  `http://192.168.x.x:7500`

测试连通性：
```bash
curl http://localhost:8000/health
curl http://192.168.x.x:8000/health
```

---

## 🎯 常见问题快速解决

| 问题 | 解决方案 |
|------|---------|
| 容器无法启动 | `docker compose logs phoneagent \| head -50` |
| API 无法连接 | `docker compose logs phoneagent \| grep API` |
| IP 检测失败 | 编辑 `.env` 手动设置 `WEBSOCKET_HOST` 和 `VITE_API_HOST` |
| 设备显示离线 | 在设备上执行 `adb tcpip 5555` |
| 防火墙阻止 | `sudo ufw allow 8000/tcp` 等 |

---

## 📝 配置文件位置

- `.env` - 环境变量配置（必须编辑）
- `docker-compose.yml` - Docker Compose 编排配置
- `docker-entrypoint.sh` - 容器启动脚本
- `Dockerfile` - Docker 镜像配置

容器内路径：
- `/app/data/` - 数据目录
- `/app/logs/` - 日志目录
- `/app/frp/` - FRP 配置

---

## 💡 文档和帮助

- `DOCKER_QUICK_START.md` - 快速开始指南
- `DOCKER.md` - 详细部署文档
- `DOCKER_COMMANDS.txt` - 这个命令速查表
- `./docker-healthcheck.sh` - 一键健康检查
