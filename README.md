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
- WebDAV 支持：支持 Windows 7/10/11 映射网络驱动器（含一键生成映射脚本）
- MIUI 风格界面系统：设置 → 外观可切换 Material 3 / MIUI 双风格
- 身份验证 + 一次性验证码：4 位验证码 + 会话令牌，防局域网内改 IP 绕过审批
- 唤醒锁：锁屏 / 切后台时传输不中断
- 灵活挂载：自由选择共享目录，系统文件管理器 / 自带文件浏览器
- 端口自动 Fallback：端口被占用自动尝试下一个
- 分享即共享：从任意应用分享文件，生成二维码 / 链接
- ZIP 打包下载：多文件打包下载，带大小限制保护
- 体积优化：R8 裁切 + 精简依赖，APK 约 2.8MB
- 按 ABI 分架构打包：arm64-v8a / armeabi-v7a / x86_64 / x86 独立 APK

## 技术栈

- 语言: Kotlin
- UI: Jetpack Compose + Material 3 + miuix (MIUI 组件库)
- 服务器: Ktor Server (CIO)
- 存储: DataStore Preferences
- 配色: Material Color Utilities (DynamicScheme) + ThemeController (Spec2021)
- 高权限: Shizuku API (UserService)

## 安装

从 [Releases](https://github.com/linlinya520/ShareKu/releases) 下载对应架构的 APK。

### 架构选择

| 架构 | 适用设备 | 文件 |
|------|----------|------|
| arm64-v8a | 绝大多数 2015 年后的手机/平板（推荐） | ShareKu-v1.3.0-arm64-v8a.apk |
| armeabi-v7a | 较老的 32 位 ARM 设备 | ShareKu-v1.3.0-armeabi-v7a.apk |
| x86_64 | 模拟器、部分平板/盒子 | ShareKu-v1.3.0-x86_64.apk |
| x86 | 老式模拟器、部分盒子 | ShareKu-v1.3.0-x86.apk |

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

### MIUI 风格切换

设置 → 外观 → 界面风格，可在 Material 3 与 MIUI 两种风格间切换。MIUI 风格采用 miuix 组件库，提供分组卡片、覆盖式展开选择、毛玻璃等原生观感。

### Windows 映射网络驱动器（Win7/10/11）

1. 启动服务器并确保开启 WebDAV
2. 主页点击「映射 Z: 盘」复制脚本，或到网页端下载脚本
3. 将脚本保存为 .bat 双击运行（无需管理员）

> 若提示错误码 67，需先在系统「可选功能」中启用「WebDAV 客户端」并重启电脑。

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

## 安全

- 连接确认：新设备访问时手机端弹出一次性 4 位验证码，对方输入后签发绑定 IP 的 24h 会话令牌
- 令牌绑定 IP：局域网内即使修改 IP 也无法绕过审批
- 可选 Basic Auth：提供额外的用户名 / 密码身份验证
- 本软件仅供局域网使用，在公共局域网使用可能存在文件泄露风险

## 构建

```bash
git clone https://github.com/linlinya520/ShareKu.git
cd ShareKu
./gradlew assembleRelease
```

需要 Android SDK 36 + JDK 17。构建产物按 ABI 拆分四个 APK，输出在 app/build/outputs/apk/release/。

## 致谢

- [miuix](https://github.com/compose-miuix-ui/miuix) — MIUI 组件库
- CINXZ — miuix 组件参考
- [aShell](https://github.com/holzschu/a-shell) — UI 设计灵感
- [InstallerX](https://github.com/iamr0s/InstallerX) — 技术参考
- [Shizuku](https://github.com/RikkaApps/Shizuku) — 高权限访问方案

---

Made with love by Lin Jing