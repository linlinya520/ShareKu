# ShareKu 🚀

> 轻量、优雅的 Android 局域网文件共享工具

[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)

## ✨ 特性

- 📡 **HTTP 文件共享** — 一键启动服务器，局域网内任意设备浏览器访问
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

1. 打开 ShareKu，授予存储权限
2. 选择共享目录（默认 `/sdcard`）
3. 点击「启动服务器」
4. 在电脑浏览器输入显示的地址即可访问

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