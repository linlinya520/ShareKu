# 🔌 ShareKu 插件开发指南

ShareKu 插件是一个**网页应用**（HTML / CSS / JavaScript），运行在 ShareKu 内置的 WebView 中，通过 `ShareKuBridge` 调用原生能力。无需编译、无需签名，打包成 ZIP 即可分发。

## 目录结构

```
你的插件id/
├─ manifest.json    # 插件清单（必需）
├─ index.html       # 主界面（默认入口）
└─ card.html        # （可选）主页卡片界面，见「主页卡片」
```

## manifest.json

```json
{
  "id": "my-plugin",
  "name": "我的插件",
  "version": "1.0.0",
  "author": "你的名字",
  "description": "一句话介绍",
  "entry": "index.html",
  "card": "card.html",
  "capabilities": ["device.info", "ui.toast"],
  "icon": "🧩"
}
```

- `id`：唯一标识（字母/数字/`.`/`-`/`_`），也是安装目录名
- `entry`：主界面文件（默认 index.html）
- `card`：主页卡片文件（可选，需声明 `home.card` 能力）
- `icon`：一个 emoji 即可

## 桥 API

```js
// 同步调用：返回 JSON 字符串 {"ok":true,"data":…} 或 {"ok":false,"error":…}
const r = JSON.parse(ShareKuBridge.invoke("method", JSON.stringify(params)));
if (r.ok) { console.log(r.data); } else { alert(r.error); }
```

## 能力清单（capabilities）

| 能力 | 方法 | 说明 |
|---|---|---|
| `device.info` | `device.info` | 设备信息（brand/model/sdk/appVersion） |
| `server.info` | `server.info` | 服务器状态（running/port/url） |
| `share.text` | `share.text` `{text}` | 调起系统分享 |
| `ui.toast` | `ui.toast` `{text}` | 显示 Toast |
| `clipboard.write` | `clipboard.write` `{text}` | 写入剪贴板 |
| `ui.openUrl` | `ui.openUrl` `{url}` | 打开外部链接 |
| `storage.info` | `storage.info` | 插件目录路径 |
| `home.card` | — | 允许在主页展示卡片（用 `card` 字段指定页面） |
| `network.http` | `http.request` `{url, method, headers, body}` | HTTP 请求（返回 status/body，body 截断 512KB）|

> 权限安全：安装后**首次运行**时逐项向用户确认，未授权的能力调用会返回错误。

## 示例：读取设备信息 + 发一个 HTTP 请求

```js
const d = JSON.parse(ShareKuBridge.invoke("device.info", "{}"));
if (d.ok) document.title = d.data.model;

const n = JSON.parse(ShareKuBridge.invoke("http.request", JSON.stringify({
  url: "https://example.com/api", method: "GET"
})));
if (n.ok) console.log(n.data.status, n.data.body);
```

## 主页卡片（home.card）

- manifest 中声明 `"home.card"` 并指定 `"card": "card.html"`
- 卡片以 WebView 内嵌在主页（约 170dp 高），背景请设为透明：

```css
html, body { background: transparent; }
```

- 卡片内同样可以使用桥（权限与主界面一致）

## 调试与安装

1. **开发目录**：把插件文件夹放到 `/sdcard/ShareKu/plugins/<id>/`，在 ShareKu「插件」页即可看到
2. **ZIP 分发**：把插件目录内文件直接打包成 ZIP（manifest.json 在压缩包根目录或单层子目录内均可），在「插件」页「导入 ZIP」
3. **上架到插件市场**：把 ZIP 提交到 ShareKu 仓库 `plugins/` 目录并在 `registry.json` 添加一条记录

## registry.json（市场索引）格式

```json
{
  "version": 1,
  "plugins": [
    {
      "id": "my-plugin",
      "name": "我的插件",
      "version": "1.0.0",
      "author": "你的名字",
      "description": "一句话介绍",
      "icon": "🧩",
      "download": "https://cdn.jsdelivr.net/gh/linlinya520/ShareKu@main/plugins/my-plugin.zip",
      "capabilities": ["device.info"]
    }
  ]
}
```

## 安全须知

- 插件运行在受限的 WebView 中，**只能**通过桥访问声明且被授权的原生能力
- 桥不提供任意文件读写；请勿在插件内硬编码敏感信息
- 请只安装来源可信的插件（与其他平台规则一致）
