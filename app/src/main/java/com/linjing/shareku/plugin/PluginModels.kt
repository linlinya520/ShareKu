package com.linjing.shareku.plugin

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 插件能力清单（权限） */
object PluginCapabilities {
    const val DEVICE_INFO = "device.info"
    const val SERVER_INFO = "server.info"
    const val SHARE_TEXT = "share.text"
    const val UI_TOAST = "ui.toast"
    const val CLIPBOARD_WRITE = "clipboard.write"
    const val UI_OPEN_URL = "ui.openUrl"
    const val STORAGE_INFO = "storage.info"
    const val HOME_CARD = "home.card"
    const val NETWORK_HTTP = "network.http"

    val all: List<String> = listOf(
        DEVICE_INFO, SERVER_INFO, SHARE_TEXT, UI_TOAST, CLIPBOARD_WRITE, UI_OPEN_URL, STORAGE_INFO,
        HOME_CARD, NETWORK_HTTP
    )

    fun label(cap: String): String = when (cap) {
        DEVICE_INFO -> "读取设备信息（品牌 / 型号 / 系统版本）"
        SERVER_INFO -> "读取服务器状态与访问地址"
        SHARE_TEXT -> "调用系统分享（分享文本 / 链接）"
        UI_TOAST -> "显示提示消息（Toast）"
        CLIPBOARD_WRITE -> "写入剪贴板"
        UI_OPEN_URL -> "打开外部链接（浏览器等）"
        STORAGE_INFO -> "读取共享目录信息（路径，不含文件内容）"
        HOME_CARD -> "在主页显示卡片（自定义主页内容）"
        NETWORK_HTTP -> "发起网络请求（HTTP，供协议类插件使用）"
        else -> cap
    }
}

/** 插件清单（manifest.json） */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val entry: String = "index.html",
    val card: String? = null,
    val type: String = "app", // "app" | "skin"
    val skin: SkinSpec? = null,
    val capabilities: List<String> = emptyList(),
    val icon: String = "🧩" // emoji 图标（MVP）
) {
    companion object {
        private val idRegex = Regex("^[A-Za-z0-9_.-]{1,64}$")

        /** 从 manifest.json 解析（失败返回 null） */
        fun parse(file: File): PluginManifest? = try {
            val json = JSONObject(file.readText())
            val id = json.optString("id")
            val name = json.optString("name")
            if (!idRegex.matches(id) || name.isBlank()) {
                null
            } else {
                val capsArr = json.optJSONArray("capabilities") ?: JSONArray()
                val caps = buildList { for (i in 0 until capsArr.length()) add(capsArr.getString(i)) }
                val skinObj = json.optJSONObject("skin")
                val skin = skinObj?.let { s ->
                    SkinSpec(
                        style = s.optString("style", "").takeIf { it.isNotBlank() },
                        darkMode = s.optString("darkMode", "").takeIf { it.isNotBlank() },
                        palette = if (s.has("palette")) s.optInt("palette") else null,
                        dynamicColor = if (s.has("dynamicColor")) s.optBoolean("dynamicColor") else null,
                        glassDensity = if (s.has("glassDensity")) s.optDouble("glassDensity").toFloat() else null,
                        layoutMode = s.optString("layoutMode", "").takeIf { it.isNotBlank() },
                        wallpaper = s.optString("wallpaper", "").takeIf { it.isNotBlank() }
                    )
                }
                PluginManifest(
                    id = id,
                    name = name,
                    version = json.optString("version", "1.0.0"),
                    author = json.optString("author", ""),
                    description = json.optString("description", ""),
                    entry = json.optString("entry", "index.html"),
                    card = json.optString("card", "").takeIf { it.isNotBlank() },
                    type = json.optString("type", "app"),
                    skin = skin,
                    capabilities = caps,
                    icon = json.optString("icon", "🧩")
                )
            }
        } catch (_: Throwable) {
            null
        }
    }
}

/** 皮肤包规格（type = "skin" 时生效）：整体改变组件风格 / 配色 / 主题 / 布局 / 壁纸 */
data class SkinSpec(
    val style: String? = null,        // material / miuix / liquid（组件风格）
    val darkMode: String? = null,     // SYSTEM / LIGHT / DARK
    val palette: Int? = null,         // 配色方案 ordinal
    val dynamicColor: Boolean? = null,
    val glassDensity: Float? = null,  // 液态玻璃质感 0..1
    val layoutMode: String? = null,   // classic / dock
    val wallpaper: String? = null     // 皮肤目录内的壁纸文件名
)

/** 插件运行时信息（扫描结果） */
data class PluginInfo(
    val manifest: PluginManifest,
    val dir: File,
    val enabled: Boolean,
    val granted: Set<String>
)