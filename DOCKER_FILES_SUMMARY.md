# 📦 Docker 部署文件清单

## ✅ 创建的文件列表

### 核心 Docker 文件

| 文件 | 大小 | 描述 |
|------|------|------|
| `Dockerfile` | 2.1 KB | Docker 镜像配置，包括系统依赖、FRP 下载、Python 环境 |
| `docker-compose.yml` | 1.4 KB | Docker Compose 编排配置，定义服务、端口、卷等 |
| `docker-entrypoint.sh` | 7.3 KB | 容器启动脚本，自动检测局域网 IP，启动 FRP/WS/API |

### 配置文件

| 文件 | 大小 | 描述 |
|------|------|------|
| `.env.docker` | 1.7 KB | 环境变量模板（首次运行时复制为 `.env`） |
| `.env` | - | 实际环境配置（用户需要创建并编辑） |

### 脚本和文档

| 文件 | 大小 | 描述 |
|------|------|------|
| `docker-start.sh` | 4.0 KB | 🚀 一键启动脚本（构建+启动+验证） |
| `docker-healthcheck.sh` | 5.1 KB | 🏥 一键健康检查脚本 |
| `DOCKER_QUICK_START.md` | 2.2 KB | ⚡ 快速开始指南（5 分钟上手） |
| `DOCKER.md` | 4.9 KB | 📖 详细部署文档（完整说明） |
| `DOCKER_COMMANDS.md` | 2.0 KB | 📋 命令速查表（常用命令） |

---

## 🎯 使用流程

### 第 1 步：准备配置

```bash
cp .env.docker .env
nano .env  # 编辑，填写 ZHIPU_API_KEY
```

### 第 2 步：一键启动

```bash
chmod +x docker-start.sh
./docker-start.sh
```

### 第 3 步：验证服务

```bash
./docker-healthcheck.sh
# 或
curl http://localhost:8000/health
```

### 第 4 步：访问服务

```
本地:    http://localhost:8000/docs
局域网:  http://192.168.x.x:8000/docs
```

---

## 📂 文件树

```
PhoneAgent/
├── Dockerfile                    # Docker 镜像配置
├── docker-compose.yml            # Docker Compose 编排
├── docker-entrypoint.sh          # 启动脚本 ⭐
├── docker-start.sh               # 一键启动脚本 ⭐
├── docker-healthcheck.sh         # 健康检查脚本
├── .env.docker                   # 配置模板
├── .env                          # 实际配置（需创建）
├── DOCKER_QUICK_START.md         # 快速开始 ⭐
├── DOCKER.md                     # 详细文档
├── DOCKER_COMMANDS.md            # 命令速查表
└── DOCKER_FILES_SUMMARY.md       # 本文件
```

---

## 🔑 关键特性

✅ **自动检测局域网 IP** - docker-entrypoint.sh 自动检测并配置
✅ **开箱即用** - 无需修改，只需填 API Key
✅ **一键启动** - docker-start.sh 自动构建、启动、验证
✅ **完整日志** - 支持实时日志查看和历史日志
✅ **健康检查** - docker-healthcheck.sh 快速诊断
✅ **端口完整** - 支持 8000(API), 9999(WS), 7001(FRP), 6100-6199(设备)
✅ **数据持久化** - 所有数据都挂载到宿主机
✅ **支持重启** - 容器重启策略自动恢复

---

## 🚀 快速命令

```bash
# 初次使用
cp .env.docker .env && nano .env && ./docker-start.sh

# 启动/停止
docker compose up -d
docker compose down

# 查看日志
docker compose logs -f

# 健康检查
./docker-healthcheck.sh

# 进入容器
docker compose exec phoneagent bash
```

---

## 📊 服务地址

| 服务 | 本地 | 局域网 | 说明 |
|------|------|--------|------|
| API 文档 | http://localhost:8000/docs | http://192.168.x.x:8000/docs | FastAPI Swagger 文档 |
| WebSocket | ws://localhost:9999 | ws://192.168.x.x:9999 | 设备连接点 |
| FRP 服务 | localhost:7001 | 192.168.x.x:7001 | 设备 FRP 客户端连接 |
| FRP 控制 | http://localhost:7500 | http://192.168.x.x:7500 | 监控面板 |

---

## 🔐 安全配置

### 必需修改

1. **编辑 `.env` 中的密钥**
   - `ZHIPU_API_KEY` - 从 https://open.bigmodel.cn/ 获取
   - `FRP_TOKEN` - 修改为复杂密码（默认 `phoneagent_secure_token_2024`）
   - `FRP_DASHBOARD_PWD` - 修改为复杂密码（默认 `admin123`）

2. **防火墙配置**
   ```bash
   sudo ufw allow 8000/tcp
   sudo ufw allow 9999/tcp
   sudo ufw allow 7001/tcp
   sudo ufw allow 6100:6199/tcp
   ```

---

## 🆘 故障排查

### 查看日志
```bash
docker compose logs phoneagent | head -50  # 显示最后 50 行
docker compose logs -f phoneagent         # 实时查看
```

### 进入容器调试
```bash
docker compose exec phoneagent bash
# 在容器内查看
tail -100f logs/api.log
tail -100f logs/websocket.log
tail -100f logs/frps.log
```

### 检查容器状态
```bash
docker compose ps                 # 显示运行状态
docker stats phoneagent-server    # 实时资源使用
docker compose config              # 显示配置
```

---

## 📞 需要帮助？

1. 查看 [DOCKER_QUICK_START.md](DOCKER_QUICK_START.md) 获取快速开始指南
2. 查看 [DOCKER.md](DOCKER.md) 获取详细文档
3. 查看 [DOCKER_COMMANDS.md](DOCKER_COMMANDS.md) 获取常用命令
4. 运行 `./docker-healthcheck.sh` 进行诊断
5. 查看 `docker compose logs` 获取错误信息

---

**祝您使用愉快！🎉**
