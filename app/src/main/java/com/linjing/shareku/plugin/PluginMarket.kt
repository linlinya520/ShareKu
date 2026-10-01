package com.linjing.shareku.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 插件市场条目 */
data class MarketPlugin(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val icon: String,
    val download: String,
    val capabilities: List<String>
)

/**
 * 插件市场（GitHub 仓库驱动，零服务器 / 零域名）：
 * - 索引 registry.json 放在 ShareKu 仓库 plugins/ 目录
 * - 主源：jsDelivr CDN（国内可达性好）；备用：GitHub raw（自动降级）
 * - 插件包 zip 同目录分发
 */
object PluginMarket {
    /** 默认索引源（主：jsDelivr） */
    const val DEFAULT_REGISTRY_URL =
        "https://cdn.jsdelivr.net/gh/linlinya520/ShareKu@main/plugins/registry.json"

    private fun fallbackRegistryUrl(url: String): String {
        val marker = "cdn.jsdelivr.net/gh/"
        return if (url.contains(marker)) {
            val rest = url.substringAfter(marker)
            val ownerRepo = rest.substringBefore("@")
            val refPath = rest.substringAfter("@")
            val ref = refPath.substringBefore("/")
            val path = refPath.substringAfter("/")
            "https://raw.githubusercontent.com/$ownerRepo/$ref/$path"
        } else url
    }

    /** 拉取市场列表。返回 (列表, 错误信息?) */
    suspend fun fetchRegistry(
        registryUrl: String = DEFAULT_REGISTRY_URL
    ): Pair<List<MarketPlugin>, String?> = withContext(Dispatchers.IO) {
        var lastError: String? = null
        for (candidate in listOf(registryUrl, fallbackRegistryUrl(registryUrl)).distinct()) {
            try {
                val text = httpGet(candidate)
                val json = JSONObject(text)
                val arr = json.optJSONArray("plugins") ?: JSONArray()
                val list = mutableListOf<MarketPlugin>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val capsArr = o.optJSONArray("capabilities") ?: JSONArray()
                    val caps = mutableListOf<String>()
                    for (j in 0 until capsArr.length()) caps.add(capsArr.getString(j))
                    list.add(
                        MarketPlugin(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            version = o.optString("version", "1.0.0"),
                            author = o.optString("author", ""),
                            description = o.optString("description", ""),
                            icon = o.optString("icon", "🧩"),
                            download = o.optString("download", ""),
                            capabilities = caps
                        )
                    )
                }
                return@withContext list to null
            } catch (e: Exception) {
                lastError = e.message ?: "网络错误"
            }
        }
        emptyList<MarketPlugin>() to (lastError ?: "无法连接插件市场")
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "ShareKu/2.0")
        return try {
            if (conn.responseCode !in 200..299) throw RuntimeException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}