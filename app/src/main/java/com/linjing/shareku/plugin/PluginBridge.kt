package com.linjing.shareku.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.linjing.shareku.AppSingletons
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * 插件 JS 桥：window.ShareKuBridge.invoke(method, paramsJson) → 返回结果 JSON 字符串。
 * 所有调用在后台线程进入 → 主线程执行 UI 操作；能力未声明/未授权时抛错。
 */
class PluginBridge(
    private val context: Context,
    private val plugin: PluginInfo,
    private val granted: Set<String>
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun invoke(method: String, paramsJson: String): String {
        return try {
            val params = if (paramsJson.isBlank()) JSONObject() else JSONObject(paramsJson)
            val result = dispatch(method, params)
            JSONObject().put("ok", true).put("data", result).toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: "调用失败").toString()
        }
    }

    private fun require(cap: String) {
        if (cap !in plugin.manifest.capabilities) throw SecurityException("插件未声明能力: $cap")
        if (cap !in granted) throw SecurityException("能力未授权: $cap")
    }

    private fun dispatch(method: String, params: JSONObject): Any {
        return when (method) {
            "device.info" -> {
                require(PluginCapabilities.DEVICE_INFO)
                JSONObject()
                    .put("brand", Build.BRAND)
                    .put("model", Build.MODEL)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("appVersion", appVersion())
            }
            "server.info" -> {
                require(PluginCapabilities.SERVER_INFO)
                val running = AppSingletons.isServerRunning.value
                val port = AppSingletons.serverActualPort.value ?: -1
                JSONObject()
                    .put("running", running)
                    .put("port", port)
                    .put("url", if (running && port > 0) "http://${localIp()}:$port" else "")
            }
            "ui.toast" -> {
                require(PluginCapabilities.UI_TOAST)
                val msg = params.optString("text", "")
                main.post { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                true
            }
            "share.text" -> {
                require(PluginCapabilities.SHARE_TEXT)
                val text = params.optString("text", "")
                main.post {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(
                        Intent.createChooser(send, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                true
            }
            "clipboard.write" -> {
                require(PluginCapabilities.CLIPBOARD_WRITE)
                val text = params.optString("text", "")
                main.post {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("ShareKu 插件", text))
                }
                true
            }
            "ui.openUrl" -> {
                require(PluginCapabilities.UI_OPEN_URL)
                val url = params.optString("url", "")
                main.post {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                true
            }
            "storage.info" -> {
                require(PluginCapabilities.STORAGE_INFO)
                JSONObject()
                    .put("pluginsDir", PluginManager.pluginRoot(context).absolutePath)
            }
            "http.request" -> {
                require(PluginCapabilities.NETWORK_HTTP)
                val url = params.optString("url", "")
                if (url.isBlank()) throw IllegalArgumentException("缺少 url")
                val method = params.optString("method", "GET").uppercase()
                val body = params.optString("body", "")
                // 桥调用本身在 WebView 的 JS 线程（非主线程），可直接执行网络请求
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.instanceFollowRedirects = true
                conn.requestMethod = method
                conn.setRequestProperty("User-Agent", "ShareKu-Plugin/1.0")
                params.optJSONObject("headers")?.let { hs ->
                    hs.keys().forEach { k -> conn.setRequestProperty(k, hs.getString(k)) }
                }
                if (method != "GET" && method != "HEAD" && body.isNotEmpty()) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.let { input ->
                    input.bufferedReader(Charsets.UTF_8).use { r ->
                        val sb = StringBuilder()
                        val buf = CharArray(8192)
                        var total = 0
                        while (total < 512_000) {
                            val n = r.read(buf)
                            if (n < 0) break
                            sb.append(buf, 0, n)
                            total += n
                        }
                        sb.toString()
                    }
                } ?: ""
                conn.disconnect()
                JSONObject().put("status", code).put("body", text)
            }
            else -> throw IllegalArgumentException("未知方法: $method")
        }
    }

    private fun appVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    private fun localIp(): String {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { nif ->
                nif.inetAddresses.toList().forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }
}