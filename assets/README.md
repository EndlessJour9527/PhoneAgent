# Assets 资源目录

本目录用于存放 PhoneAgent 项目的图片、二维码等静态资源。

## 📁 目录结构

```
assets/
├── images/          # 项目截图、功能展示图
│   ├── 首页.jpg            # Web 首页截图
│   ├── 设备管理.jpg        # Web 设备管理页
│   ├── 防风控配置.jpg      # Web 防风控配置页
│   ├── 性能诊断.jpg        # Web 性能诊断页
│   ├── app-首页.jpg        # Android App 首页
│   ├── app-设置1.jpg       # Android App 设置页1
│   ├── app-设置2.jpg       # Android App 设置页2
│   └── app-设置3.jpg       # Android App 设置页3
├── qrcode/          # 二维码图片
│   ├── 微信二维码.jpg      # 个人微信二维码
│   └── 赞赏码.jpg          # 赞赏码
└── README.md        # 本文件
```

---

## 📸 图片命名规范

### Web 前端截图

| 文件名 | 说明 | 尺寸建议 |
|-------|------|---------|
| `web-home.png` | 首页界面 | 1920x1080 |
| `web-devices.png` | 设备管理页 | 1920x1080 |
| `web-tasks.png` | 任务列表页 | 1920x1080 |
| `web-live-preview.png` | 实时预览界面 | 1920x1080 |
| `web-app-config.png` | 应用配置页 | 1920x1080 |
| `web-anti-detection.png` | 防检测配置页 | 1920x1080 |

### Android App 截图

| 文件名 | 说明 | 尺寸建议 |
|-------|------|---------|
| `app-main.png` | 主界面（BT 驾驶舱风格） | 1080x2400 |
| `app-voice-input.png` | 语音输入界面 | 1080x2400 |
| `app-settings.png` | 设置页面 | 1080x2400 |
| `app-demo.gif` | 语音交互演示 | 540x1200 |

### 架构图

| 文件名 | 说明 | 尺寸建议 |
|-------|------|---------|
| `architecture.png` | 系统架构图 | 1920x1080 |
| `deployment-flow.png` | 部署流程图 | 1920x1080 |

### 功能演示

| 文件名 | 说明 | 尺寸建议 |
|-------|------|---------|
| `demo-voice-control.gif` | 语音控制演示 | 800x600 |
| `demo-task-execution.gif` | 任务执行演示 | 800x600 |
| `demo-live-preview.gif` | 实时预览演示 | 800x600 |

---

## 🎨 图片要求

### 截图要求

- **格式**：PNG（静态）、GIF（动态）
- **质量**：高清，避免模糊
- **内容**：
  - ✅ 使用示例数据（避免真实敏感信息）
  - ✅ 界面完整（包含导航栏、功能区）
  - ✅ 美观（合理的配色、布局）
- **尺寸**：
  - Web 截图：1920x1080 或 1440x900
  - App 截图：1080x2400 或 1080x1920
  - GIF 动图：建议 800x600，文件大小 < 5MB

### 二维码要求

- **格式**：PNG
- **尺寸**：400x400 或 500x500
- **背景**：白色或透明
- **说明**：二维码下方添加文字说明（如"微信交流群"）

---

## 📝 使用指南

### 在 README.md 中引用

```markdown
## 📸 项目截图

### Web 管理界面

![首页](assets/images/web-home.png)

### Android 语音助手

<img src="assets/images/app-main.png" width="300" alt="主界面">

## 💬 联系方式

<table>
  <tr>
    <td align="center">
      <img src="assets/qrcode/wechat-contact.png" width="150"><br>
      个人微信
    </td>
    <td align="center">
      <img src="assets/qrcode/wechat-group.png" width="150"><br>
      微信交流群
    </td>
  </tr>
</table>
```

### 在文档中引用

```markdown
系统架构如下图所示：

![系统架构](../assets/images/architecture.png)
```

---

## 🔒 隐私保护

### ⚠️ 注意事项

1. **敏感信息检查**：
   - ❌ 不要包含真实的设备信息（IMEI、序列号）
   - ❌ 不要包含个人联系方式（真实姓名、电话）
   - ❌ 不要包含 API Key、Token、密码
   - ❌ 不要包含真实的应用数据（聊天记录、订单信息）

2. **示例数据使用**：
   - ✅ 设备名称：Device-1、测试设备
   - ✅ 任务指令：打开设置、截图等通用操作
   - ✅ 应用列表：使用系统应用（设置、Termux）

3. **二维码管理**：
   - ✅ 定期检查二维码有效性
   - ✅ 群满时及时更新群二维码
   - ✅ 个人微信二维码可添加验证消息

---

## 📦 文件大小建议

| 类型 | 单个文件大小 | 总计建议 |
|-----|------------|---------|
| PNG 截图 | < 500KB | < 5MB |
| GIF 动图 | < 5MB | < 20MB |
| 二维码 | < 100KB | < 1MB |

**优化建议**：
- 使用在线工具压缩图片（如 TinyPNG）
- GIF 动图控制帧数和分辨率
- 避免上传超大图片

---

## 🚀 快速开始

### 1. 上传截图

```bash
# 复制截图到对应目录
cp ~/screenshots/web-home.png assets/images/
cp ~/screenshots/app-main.png assets/images/

# 压缩图片（可选）
# 使用 ImageMagick
mogrify -resize 1920x1080 -quality 85 assets/images/*.png
```

### 2. 生成二维码

```bash
# 在线生成或使用工具生成二维码
# 保存到 assets/qrcode/ 目录
```

### 3. 更新文档

编辑 `README.md` 和 `README_EN.md`，引用新上传的图片。

---

## ✅ 已完成

- ✅ 上传 Web 前端截图（4张）
  - 首页.jpg
  - 设备管理.jpg
  - 防风控配置.jpg
  - 性能诊断.jpg
- ✅ 上传 Android App 截图（4张）
  - app-首页.jpg
  - app-设置1.jpg
  - app-设置2.jpg
  - app-设置3.jpg
- ✅ 上传个人微信二维码
- ✅ 上传赞赏码
- ✅ 更新 README.md 引用

## 🎯 待办清单

- [ ] 录制功能演示 GIF 动图
- [ ] 更新 README_EN.md 引用

---

**PhoneAgent Assets**  
Copyright (C) 2025 PhoneAgent Contributors  
Licensed under AGPL-3.0

