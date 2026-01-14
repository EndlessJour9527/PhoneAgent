# 更新日志

所有重要的项目变更都会记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [1.0.0] - 2026-01-14

### 🎉 首次发布

PhoneAgent Remote 首个正式版本发布！

### ✨ 核心功能

- **FRP 隧道管理**
  - 自动启动和管理 FRP 客户端
  - 支持 ARM64 和 ARMv7 架构
  - 集成 Termux JNI 执行二进制文件
  
- **WebSocket 通信**
  - 支持直连 IP 模式（开发/测试）
  - 支持域名代理模式（生产环境）
  - 自动重连机制
  - 心跳保活
  
- **后台保活**
  - 前台服务（通知栏常驻）
  - WorkManager 定期检查
  - 双进程守护（主进程 + 守护进程）
  - 1像素保活 Activity
  - JobScheduler 保活
  - 屏幕状态监听
  - 开机自启动
  
- **配置管理**
  - 可视化配置向导
  - DataStore 持久化存储
  - 配置验证和自动生成
  - ContentProvider 跨应用共享配置
  
- **增强工具**
  - 预装 yadb（支持中文输入、强制截图）
  - 预装 Termux bootstrap
  - 预装 FRP 客户端
  
- **用户界面**
  - Material Design 3 设计
  - 实时状态显示
  - 内置日志查看器
  - 关于页面（版本信息、开源协议）

### 📦 技术栈

- **语言**: Kotlin
- **最低系统**: Android 5.0 (API 21)
- **目标系统**: Android 9.0 (API 28)
- **架构支持**: ARM64-v8a, ARMv7
- **APK 大小**: 约 70 MB

### 📝 已知限制

- `targetSdk` 必须保持在 28，不能升级到 29+（Android 10+ 的 W^X 限制）
- 部分厂商需要手动配置电池优化和后台运行权限
- 首次使用需要通过 USB 启用 ADB TCP/IP

### 🙏 致谢

本项目使用了以下开源组件：
- [Termux](https://github.com/termux/termux-app) (GPLv3)
- [YADB](https://github.com/ysbing/YADB) (LGPL-3.0)
- [FRP](https://github.com/fatedier/frp) (Apache-2.0)
- [Material Components](https://github.com/material-components/material-components-android) (Apache-2.0)
- [Timber](https://github.com/JakeWharton/timber) (Apache-2.0)

---

## 版本号说明

- **主版本号**（Major）：不兼容的 API 修改
- **次版本号**（Minor）：向下兼容的功能性新增
- **修订号**（Patch）：向下兼容的问题修正

---

[1.0.0]: https://github.com/tmwgsicp/PhoneAgent/releases/tag/v1.0.0
