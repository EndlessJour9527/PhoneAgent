# PhoneAgent Remote - 构建指南

## 📋 构建前检查清单

### 必需文件检查

在构建 APK 之前，请确保以下文件**没有被 .gitignore 过滤掉**：

#### ✅ Native 库文件（必需）
```
app/src/main/jniLibs/
├── arm64-v8a/
│   └── libtermux.so          ✓ 必需（ARM64 设备）
└── armeabi-v7a/
    └── libtermux.so          ✓ 必需（ARM32 设备）
```

**重要**：`.gitignore` 中**不应该**忽略 `*.so` 文件！

#### ✅ Assets 资源文件（必需）
```
app/src/main/assets/
├── frp/
│   ├── frpc_arm              ✓ 必需（ARM32 FRP 客户端）
│   └── frpc_arm64            ✓ 必需（ARM64 FRP 客户端）
├── termux/
│   ├── bootstrap-aarch64.zip ✓ 必需（ARM64 Termux 环境）
│   └── bootstrap-arm.zip     ✓ 必需（ARM32 Termux 环境）
└── yadb/
    └── yadb                  ✓ 必需（增强型 ADB 工具）
```

#### ✅ Gradle Wrapper（必需）
```
gradle/wrapper/
├── gradle-wrapper.jar        ✓ 必需（Gradle wrapper 可执行文件）
└── gradle-wrapper.properties ✓ 必需（Gradle 配置）
```

**重要**：`.gitignore` 中已经配置了 `!gradle-wrapper.jar` 和 `!gradle-wrapper.properties` 来确保这些文件不被忽略。

#### ✅ 核心配置文件
```
android-remote-control/
├── build.gradle.kts          ✓ 项目构建配置
├── settings.gradle.kts       ✓ 项目设置
├── gradle.properties         ✓ Gradle 属性
├── gradlew                   ✓ Gradle wrapper 脚本（Linux/Mac）
├── gradlew.bat               ✓ Gradle wrapper 脚本（Windows）
├── LICENSE                   ✓ 开源协议
└── README.md                 ✓ 项目文档
```

#### ✅ 应用配置文件
```
app/
├── build.gradle.kts          ✓ 应用构建配置
├── proguard-rules.pro        ✓ ProGuard 混淆规则
└── src/main/
    ├── AndroidManifest.xml   ✓ 应用清单
    ├── java/                 ✓ 源代码
    ├── res/                  ✓ 资源文件
    ├── assets/               ✓ Assets 资源（见上）
    └── jniLibs/              ✓ Native 库（见上）
```

---

## 🔧 手动构建 APK

### 环境要求

- **JDK**: 17 或更高版本
- **Android SDK**: API 34
- **Gradle**: 8.2+（通过 wrapper 自动下载）
- **操作系统**: Windows / Linux / macOS

### 构建步骤

#### 1. 清理项目

```bash
# Windows
cd android-remote-control
.\gradlew clean

# Linux/macOS
cd android-remote-control
./gradlew clean
```

#### 2. 构建 Release APK

```bash
# Windows
.\gradlew assembleRelease

# Linux/macOS
./gradlew assembleRelease
```

#### 3. 查找生成的 APK

构建成功后，APK 文件位于：

```
android-remote-control/app/build/outputs/apk/release/app-release.apk
```

#### 4. 重命名 APK（可选）

```bash
# Windows
cd app\build\outputs\apk\release
ren app-release.apk PhoneAgent-Remote-v1.0.0.apk

# Linux/macOS
cd app/build/outputs/apk/release
mv app-release.apk PhoneAgent-Remote-v1.0.0.apk
```

---

## 🐛 常见问题

### 问题 1: 找不到 libtermux.so

**症状**：
```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libtermux.so" not found
```

**原因**：`.gitignore` 中忽略了 `*.so` 文件。

**解决方案**：
1. 检查 `.gitignore`，确保没有 `*.so` 规则
2. 确认 `app/src/main/jniLibs/` 中有 `.so` 文件
3. 重新构建

### 问题 2: 找不到 FRP 客户端

**症状**：
```
FRP binary not found in assets
```

**原因**：`assets/frp/` 中的文件丢失。

**解决方案**：
1. 确认 `app/src/main/assets/frp/` 中有 `frpc_arm` 和 `frpc_arm64`
2. 检查文件权限（Linux/macOS 需要可执行权限）
3. 重新构建

### 问题 3: Gradle 构建失败

**症状**：
```
OutOfMemoryError: Java heap space
```

**解决方案**：
检查 `gradle.properties` 中的内存配置：

```properties
org.gradle.jvmargs=-Xmx6144m -XX:MaxMetaspaceSize=2048m
```

### 问题 4: targetSdk 警告

**症状**：
```
Warning: targetSdk 28 is below the recommended 34
```

**说明**：这是**正常的**，不要升级 `targetSdk`！

**原因**：
- PhoneAgent Remote 使用 Termux JNI 执行二进制文件
- Android 10+ (API 29+) 引入了 W^X 限制
- `targetSdk >= 29` 会导致 FRP 无法执行

---

## 📦 构建输出

### Release APK 信息

- **文件名**: `app-release.apk` → `PhoneAgent-Remote-v1.0.0.apk`
- **大小**: 约 70 MB（包含所有 native 库和 assets）
- **架构**: ARM64 + ARM32（支持所有 Android 设备）
- **最低版本**: Android 5.0 (API 21)
- **目标版本**: Android 9.0 (API 28)

### APK 内容验证

使用以下命令验证 APK 内容：

```bash
# Windows (使用 7-Zip 或其他工具)
7z l app-release.apk

# Linux/macOS
unzip -l app-release.apk

# 验证 .so 文件
unzip -l app-release.apk | grep "\.so$"

# 验证 assets 文件
unzip -l app-release.apk | grep "assets/"
```

**预期输出**：
```
lib/arm64-v8a/libtermux.so
lib/armeabi-v7a/libtermux.so
assets/frp/frpc_arm
assets/frp/frpc_arm64
assets/termux/bootstrap-aarch64.zip
assets/termux/bootstrap-arm.zip
assets/yadb/yadb
```

---

## ✅ 构建成功检查清单

构建完成后，请验证：

- [ ] APK 文件存在：`app/build/outputs/apk/release/app-release.apk`
- [ ] APK 大小合理：约 70 MB
- [ ] APK 包含 `.so` 文件（使用 `unzip -l` 检查）
- [ ] APK 包含 `assets/` 文件（使用 `unzip -l` 检查）
- [ ] 可以成功安装到测试设备
- [ ] 应用可以正常启动
- [ ] FRP 服务可以正常连接

---

## 🚀 发布准备

### 1. 签名 APK（可选）

如果需要发布到应用商店，需要签名：

```bash
# 生成签名密钥（首次）
keytool -genkey -v -keystore phoneagent-remote.jks -keyalg RSA -keysize 2048 -validity 10000 -alias phoneagent

# 签名 APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore phoneagent-remote.jks app-release.apk phoneagent

# 对齐 APK
zipalign -v 4 app-release.apk PhoneAgent-Remote-v1.0.0-signed.apk
```

### 2. 发布清单

- [ ] APK 已构建并测试
- [ ] 版本号正确（v1.0.0）
- [ ] README.md 已更新
- [ ] LICENSE 文件存在
- [ ] 所有必需文件已提交到 Git
- [ ] 创建 Git tag：`v1.0.0`

---

## 📝 版本管理

### 更新版本号

编辑 `app/build.gradle.kts`：

```kotlin
android {
    defaultConfig {
        versionCode = 1        // 每次发布递增
        versionName = "1.0.0"  // 语义化版本号
    }
}
```

### 版本号规则

- **主版本号**：重大更新，不兼容的 API 变更
- **次版本号**：新增功能，向后兼容
- **修订号**：Bug 修复，向后兼容

---

## 💡 提示

1. **首次构建**可能需要下载依赖，耗时较长（5-10 分钟）
2. **后续构建**会使用缓存，速度更快（1-2 分钟）
3. **清理构建**：如遇到奇怪问题，先执行 `gradlew clean`
4. **离线构建**：如需离线构建，先执行 `gradlew --refresh-dependencies`
5. **并行构建**：在 `gradle.properties` 中添加 `org.gradle.parallel=true`

---

## 📞 获取帮助

如果遇到构建问题：

1. 查看构建日志：`gradlew assembleRelease --stacktrace`
2. 查看详细日志：`gradlew assembleRelease --info`
3. 提交 Issue：[GitHub Issues](https://github.com/tmwgsicp/PhoneAgent/issues)

---

**祝构建顺利！🎉**
