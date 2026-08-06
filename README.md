# ShareKu 🚀

> 轻量、优雅的 Android 局域网文件共享工具

[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)

## ✨ 特性

- 📡 **HTTP 文件共享** — 一键启动服务器，局域网内任意设备浏览器访问
- 📲 **设备直连传输** — 两台手机都装 ShareKu，App 间直接传文件，无需浏览器
- 🔍 **自动设备发现** — mDNS(NSD) 扫描附近设备，扫不到可手动输入 IP
- 📂 **传输审批通知** — 接收端弹出通知，接受/拒绝后自动消失
- 🛡️ **后台定位保活** — 解决鸿蒙/国产 ROM 熄屏或切后台后断网问题
- 🌐 **WebDAV 支持** — 支持 Windows/macOS 映射网络驱动器
- 🔐 **身份验证** — 可选 Basic Auth，保护文件安全
- 📂 **灵活挂载** — 自由选择共享目录，系统文件管理器 / 自带文件浏览器
- ⚡ **端口自动 Fallback** — 端口被占用自动尝试下一个，最多 10 次
- 🎨 **Material You 设计** — 动态取色 + 9 种配色方案，完美深色模式
- 📱 **分享即共享** — 从任意应用分享文件，生成二维码 / 链接
- 🔒 **IP 审批** — 可选连接确认，手动批准陌生设备
- 📦 **ZIP 打包下载** — 多文件打包下载，带大小限制保护

## 🔧 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **服务器**: Ktor Server (CIO)
- **存储**: DataStore Preferences
- **配色**: Material Color Utilities (DynamicScheme)

## 📥 安装

从 [Releases](https://github.com/linlinya520/ShareKu/releases) 下载最新 APK。

## 🚀 快速开始

### 浏览器访问

1. 打开 ShareKu，授予存储权限
2. 选择共享目录（默认 `/sdcard`）
3. 点击「启动服务器」
4. 在电脑浏览器输入显示的地址即可访问

### App 间直连传输

1. 两台手机安装 ShareKu，连接同一 WiFi
2. 两台手机都在主页启动服务器（用于广播设备发现）
3. 进入「设备直连」页，等待扫描到对方设备
4. 选择文件 → 发送 → 对方手机弹出审批通知 → 接受即保存

> 若扫不到设备，可点击「手动输入 IP 连接」输入对方 WiFi IP 直连。

### 鸿蒙/国产 ROM 后台保活

1. 主页开启「后台定位保活」开关（默认开启）
2. 首次启动会引导授予定位权限（仅网络定位，低功耗，不记录坐标）
3. 若系统定位服务关闭，会引导前往系统设置开启

## ⚠️ 注意事项

- 本版本未经充分测试，可能存在 bug，欢迎提交 [Issue](https://github.com/linlinya520/ShareKu/issues)
- 后台定位保活依赖系统定位服务，定位被系统关闭时保活可能失效
- 设备直连依赖 NSD/mDNS，部分路由器/AP 隔离环境下可能扫描不到设备

### Windows 映射网络驱动器

1. 确保 WebDAV 已启用
2. 启动服务器后点击「映射 Z: 盘」复制脚本
3. 在命令提示符粘贴运行

## 🏗️ 构建

```bash
git clone https://github.com/linlinya520/ShareKu.git
cd ShareKu
./gradlew assembleRelease
```

需要 Android SDK 35 + JDK 17。

## 🙏 致谢

- [aShell](https://github.com/holzschu/a-shell) — UI 设计灵感
- [InstallerX](https://github.com/iamr0s/InstallerX) — 技术参考

---

**Made with ❤️ by Lin Jing**