# FRP 端口连接失败 - 完整诊断报告

## 问题症状

```
设备端日志:
2026-01-15 16:29:55.583 [W] [client/service.go:328] connect to server error: connection write timeout
```

- ✗ 设备尝试连接: `10.91.1.207:7000`
- ✗ 连接失败: `connection write timeout`
- ✓ WebSocket 连接正常 (验证了设备能到达服务器)

## 根本原因分析

### 架构概览
```
┌──────────────────────────────────────────────────────────┐
│ macOS Host (10.91.1.207)                                 │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Docker Container                                    │ │
│ │ ┌────────────────────────────────────────────────┐ │ │
│ │ │ FRP Server: frps.ini                           │ │ │
│ │ │   bind_port = 7000 ✓                           │ │ │
│ │ │   (内部监听)                                    │ │ │
│ │ └────────────────────────────────────────────────┘ │ │
│ │ Port Mapping:                                     │ │ │
│ │ 7001:7000 (Host:Container)                        │ │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                          │
│ 外部网络                                                 │
│ Port 7001 ← FRP 实际可达入口                           │
└──────────────────────────────────────────────────────────┘
         ↓
┌──────────────────────────────────────────────────────────┐
│ Android Device (Nothing A142)                            │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ frpc.ini                                            │ │
│ │   server_addr = 10.91.1.207                         │ │
│ │   server_port = 7000 ← 问题在这里!                  │ │
│ │   (应该是 7001)                                     │ │
│ └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### 配置链路分析

```
配置过程:
1. 用户输入 SetupWizardActivity.kt
         ↓
2. serverPort 赋值: serverPort = 7001 ✓ (已正确)
         ↓
3. Config 对象创建: Config(serverPort=7001) ✓
         ↓
4. ConfigRepository.saveConfig() 存储配置
         ↓
5. Device DataStore 保存值
         ↓
6. 应用重启时读取:
   ConfigRepository.configFlow -> preferences[SERVER_PORT] ?: 7000
         ↓
   问题: 如果DataStore中值为空, 使用默认值 7000 ✗
         ↓
7. FrpManager 生成 frpc.ini
   server_port = $serverPort (值取决于上一步)
```

## 问题的真实原因

在 SetupWizardActivity.kt 中:

```kotlin
// 第280行 - 已正确设置
val config = Config(
    serverIp = serverIp,
    serverPort = 7001,  ✓ 正确
    ...
)
```

但在 ConfigRepository.kt 中:

```kotlin
// 第26行 - 原问题
val configFlow: Flow<Config> = context.dataStore.data.map { preferences ->
    Config(
        ...
        serverPort = preferences[SERVER_PORT] ?: 7000,  ✗ 默认值错误
        ...
    )
}
```

**问题链条:**
1. 用户首次配置时，SetupWizardActivity 创建 Config(serverPort=7001)
2. 保存到 DataStore 中
3. 但如果 DataStore 读取失败或数据丢失，会使用默认值 7000
4. 设备重启或应用重启时，使用旧的默认值生成 frpc.ini
5. 导致设备连接到错误的端口

## 本地所有 7000/7001 配置检查

| 文件 | 行号 | 配置 | 现状 | 说明 |
|------|------|------|------|------|
| docker-compose.yml | 23 | `0.0.0.0:7001:7000` | ✓ 正确 | Docker 端口映射 |
| frp/frps.ini | 1 | `bind_port = 7000` | ✓ 正确 | FRP 服务器内部端口 |
| frp/frps.toml | 1 | `bindPort = 7000` | ⚠️ 备用 | 备用配置文件 |
| frp/frpc.toml | 7 | `serverPort = 7000` | ⚠️ 备用 | 备用配置文件 |
| SetupWizardActivity.kt | 280 | `serverPort = 7001` | ✓ 正确 | 设备配置 UI |
| **ConfigRepository.kt** | **26** | `?: 7001` | ✅ **已修复** | 默认值（关键修复点） |

## 实施的修复

### 修复1: ConfigRepository.kt (第26行)

**修改前:**
```kotlin
serverPort = preferences[SERVER_PORT] ?: 7000,
```

**修改后:**
```kotlin
serverPort = preferences[SERVER_PORT] ?: 7001,
```

**验证:**
```
✅ 已确认修改成功
Line 26: serverPort = preferences[SERVER_PORT] ?: 7001,
```

### 修复2: APK 重新编译

```bash
cd android-remote-control
./gradlew clean assembleDebug
# 结果: BUILD SUCCESSFUL in 8s
```

**新 APK 路径:**
```
app/build/outputs/apk/debug/app-debug.apk (73 MB)
```

## 后续操作步骤

### 步骤 1: 等待设备重新连接
- 当前设备离线 (需要手动重启或等待网络恢复)
- 恢复后重新连接 ADB

### 步骤 2: 部署新 APK
```bash
adb uninstall com.phoneagent.remote
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 步骤 3: 重新配置设备
1. 启动 PhoneAgent 应用
2. 重新填写配置:
   - 服务器 IP: 10.91.1.207
   - FRP Token: 2024
   - 连接方式: 选择一种
   - 完成配置

### 步骤 4: 验证修复

**检查生成的 frpc.ini:**
```bash
adb shell "run-as com.phoneagent.remote cat /data/user/0/com.phoneagent.remote/files/termux/home/.phoneagent/frpc.ini"
```

**预期输出:**
```ini
[common]
server_addr = 10.91.1.207
server_port = 7001  ← 应该是 7001，不是 7000
token = 2024

[Android_A142_adb]
type = tcp
local_ip = 127.0.0.1
local_port = 5555
remote_port = 6104
```

**检查 FRP 连接日志:**
```bash
adb shell "run-as com.phoneagent.remote tail -f /data/user/0/com.phoneagent.remote/files/termux/home/.phoneagent/frpc.log"
```

**预期输出 (成功):**
```
[I] login success
```

**而不是 (失败):**
```
[W] connect to server error: connection write timeout
```

## Docker 端口映射说明

为什么使用 7001 → 7000？

1. **macOS Control Center 占用 7000**
   - macOS 的控制中心占用本地 7000 端口
   - 无法直接在 7000 上运行 FRP 服务器

2. **Docker 端口映射解决方案**
   ```yaml
   ports:
     - "0.0.0.0:7001:7000"  # Host:Container
   ```
   - Docker 内部运行 FRP 服务器: 7000
   - 外部通过 7001 访问

3. **设备如何连接**
   - 设备需要连接到: `10.91.1.207:7001`
   - 而不是: `10.91.1.207:7000` (被 macOS 占用)

## 总结

| 项目 | 状态 |
|------|------|
| 问题诊断 | ✅ 完成 |
| 根本原因 | ✅ ConfigRepository.kt 默认值为 7000 |
| 代码修复 | ✅ 改为 7001 |
| APK 重编译 | ✅ 成功 |
| APK 部署 | ⏳ 等待设备连接 |
| 设备重配 | ⏳ 等待设备连接 |
| 功能验证 | ⏳ 待测试 |

**关键修复点:** ConfigRepository.kt 第26行的默认值现已为 7001，与 Docker 映射一致。待设备重新连接后，需要重新部署 APK 并重新配置。
