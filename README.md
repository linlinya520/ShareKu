# ShareKu

轻量、优雅的 Android 局域网文件共享工具

[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)

## 特性

- HTTP 文件共享：一键启动服务器，局域网内任意设备浏览器访问
- 设备直连传输：两台手机都装 ShareKu，App 间直接传文件，无需浏览器
- 双向传输进度：发送端界面与接收端通知栏实时显示传输进度
- 自动设备发现：mDNS(NSD) 扫描附近设备，扫不到可手动输入 IP
- Shizuku 受限目录访问：授权 Shizuku 后可浏览并共享 Android/data 等受限目录
- WebDAV 支持：支持 Windows/macOS 映射网络驱动器
- 身份验证：可选 Basic Auth，保护文件安全
- 灵活挂载：自由选择共享目录，系统文件管理器 / 自带文件浏览器
- 端口自动 Fallback：端口被占用自动尝试下一个，最多 10 次
- Material You 设计：动态取色 + 多种配色方案，完美深色模式
- 分享即共享：从任意应用分享文件，生成二维码 / 链接
- ZIP 打包下载：多文件打包下载，带大小限制保护
- 按 ABI 分架构打包：arm64-v8a / armeabi-v7a / x86_64 / x86 独立 APK，大幅减小体积

## 技术栈

- 语言: Kotlin
- UI: Jetpack Compose + Material 3
- 服务器: Ktor Server (CIO)
- 存储: DataStore Preferences
- 配色: Material Color Utilities (DynamicScheme)
- 高权限: Shizuku API (UserService)

## 安装

从 [Releases](https://github.com/linlinya520/ShareKu/releases) 下载对应架构的 APK。

### 架构选择

| 架构 | 适用设备 | 文件 |
|------|----------|------|
| arm64-v8a | 绝大多数 2015 年后的手机/平板（推荐） | ShareKu-v1.2.0-arm64-v8a.apk |
| armeabi-v7a | 较老的 32 位 ARM 设备 | ShareKu-v1.2.0-armeabi-v7a.apk |
| x86_64 | 模拟器、部分平板/盒子 | ShareKu-v1.2.0-x86_64.apk |
| x86 | 老式模拟器、部分盒子 | ShareKu-v1.2.0-x86.apk |

不确定架构时，优先选择 arm64-v8a。模拟器用户可在设置中查看 ABI。

## 快速开始

### 浏览器访问

1. 打开 ShareKu，授予存储权限
2. 选择共享目录（默认 /sdcard）
3. 点击「启动服务器」
4. 在电脑浏览器输入显示的地址即可访问

### App 间直连传输

1. 两台手机安装 ShareKu，连接同一 WiFi
2. 两台手机都在主页启动服务器（用于广播设备发现）
3. 进入「设备直连」页，等待扫描到对方设备
4. 选择文件发送，接收端自动保存，双方实时看到传输进度

> 若扫不到设备，可点击「手动输入 IP 连接」输入对方 WiFi IP 直连。

### Shizuku 受限目录访问

Android 11+ 对 /storage/emulated/0/Android/data 等目录有访问限制，普通文件 API 无法读取。授权 Shizuku 后 ShareKu 可以：

1. 安装并启动 [Shizuku](https://shizuku.rikka.app/)（需要 adb 或 root 授权）
2. 在 ShareKu 的目录选择界面切换到「Shizuku 模式」
3. 浏览并选择受限目录（如 /storage/emulated/0/Android/data 下的应用目录）
4. 启动服务器后，浏览器端可以真正列出并下载该目录中的文件

注意：

- 受限目录的共享目前支持列表与下载；上传、删除、WebDAV、ZIP 打包暂不支持
- 若共享目录受限但 Shizuku 未授权，启动服务器时会提示网页端无法访问
- Android/data 目录下每个文件夹通常对应一个应用包名，文件夹图标右下角会显示对应应用图标，方便定位

## 注意事项

- 本软件仅供局域网使用，建议只在受信任的局域网中使用；在公共局域网使用可能存在文件泄露风险
- 未经充分测试，可能存在 bug，欢迎提交 [Issue](https://github.com/linlinya520/ShareKu/issues)
- 设备直连依赖 NSD/mDNS，部分路由器/AP 隔离环境下可能扫描不到设备
- Shizuku 模式下请勿随意修改系统目录，操作风险自负

### Windows 映射网络驱动器

1. 确保 WebDAV 已启用
2. 启动服务器后点击「映射 Z: 盘」复制脚本
3. 在命令提示符粘贴运行

## 构建

```bash
git clone https://github.com/linlinya520/ShareKu.git
cd ShareKu
./gradlew assembleRelease
```

需要 Android SDK 35 + JDK 17。构建产物按 ABI 拆分四个 APK，输出在 app/build/outputs/apk/release/。

## 致谢

- [aShell](https://github.com/holzschu/a-shell) — UI 设计灵感
- [InstallerX](https://github.com/iamr0s/InstallerX) — 技术参考
- [Shizuku](https://github.com/RikkaApps/Shizuku) — 高权限访问方案

---

Made with love by Lin Jing
