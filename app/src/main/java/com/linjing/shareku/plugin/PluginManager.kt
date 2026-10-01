package com.linjing.shareku.plugin

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 插件管理器：
 * - 插件目录：/sdcard/ShareKu/plugins/<id>/（manifest.json + 资源）；不可写时回退应用私有目录
 * - 状态（启用 / 已授权能力）存 SharedPreferences（shareku_plugins）
 * - 支持 ZIP 导入、内置示例安装、删除
 */
object PluginManager {
    const val SAMPLE_PLUGIN_ID = "hello-demo"

    /** 插件根目录（外置优先，用户可直接拷贝插件文件夹） */
    fun pluginRoot(context: Context): File {
        val external = File("/sdcard/ShareKu/plugins")
        return try {
            if (!external.exists()) external.mkdirs()
            if (external.canWrite()) external else fallbackRoot(context)
        } catch (_: Throwable) {
            fallbackRoot(context)
        }
    }

    private fun fallbackRoot(context: Context): File {
        val dir = File(context.filesDir, "plugins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("shareku_plugins", Context.MODE_PRIVATE)

    fun scan(context: Context): List<PluginInfo> {
        val root = pluginRoot(context)
        val sp = prefs(context)
        val list = mutableListOf<PluginInfo>()
        root.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val manifestFile = File(dir, "manifest.json")
                val manifest = if (manifestFile.isFile) PluginManifest.parse(manifestFile) else null
                if (manifest != null) {
                    list.add(
                        PluginInfo(
                            manifest = manifest,
                            dir = dir,
                            enabled = sp.getBoolean("enabled_${manifest.id}", true),
                            granted = sp.getStringSet("granted_${manifest.id}", null) ?: emptySet()
                        )
                    )
                }
            }
        }
        return list.sortedBy { it.manifest.name }
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled_$id", enabled).apply()
    }

    fun grantCapabilities(context: Context, id: String, caps: Set<String>) {
        prefs(context).edit().putStringSet("granted_$id", caps).apply()
    }

    fun deletePlugin(context: Context, id: String) {
        val dir = File(pluginRoot(context), id)
        dir.deleteRecursively()
        prefs(context).edit().remove("enabled_$id").remove("granted_$id").apply()
    }

    /** 从网络 URL 安装插件（插件市场；支持 jsDelivr→GitHub raw 自动降级）。 */
    fun installFromUrl(context: Context, url: String): Pair<Boolean, String> {
        val candidates = listOf(url, toRawUrl(url)).distinct()
        var lastError = "下载失败"
        for (candidate in candidates) {
            try {
                val conn = java.net.URL(candidate).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "ShareKu/2.0")
                if (conn.responseCode !in 200..299) {
                    lastError = "HTTP ${conn.responseCode}"
                    conn.disconnect()
                    continue
                }
                val result = conn.inputStream.use { installFromStream(context, it) }
                conn.disconnect()
                if (result.first) return result
                lastError = result.second
            } catch (e: Exception) {
                lastError = e.message ?: "下载失败"
            }
        }
        return false to "下载失败：$lastError"
    }

    private fun toRawUrl(url: String): String {
        return try {
            val marker = "cdn.jsdelivr.net/gh/"
            if (url.contains(marker)) {
                val rest = url.substringAfter(marker)
                val ownerRepo = rest.substringBefore("@")
                val refPath = rest.substringAfter("@")
                val ref = refPath.substringBefore("/")
                val path = refPath.substringAfter("/")
                "https://raw.githubusercontent.com/$ownerRepo/$ref/$path"
            } else url
        } catch (_: Exception) {
            url
        }
    }

    /** 从输入流（ZIP）安装——ZIP 导入与网络下载共用。 */
    fun installFromStream(context: Context, input: java.io.InputStream): Pair<Boolean, String> {
        val root = pluginRoot(context)
        val temp = File(root, ".tmp_install")
        try {
            if (temp.exists()) temp.deleteRecursively()
            temp.mkdirs()
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val outFile = File(temp, name)
                    val canonical = outFile.canonicalPath
                    if (!canonical.startsWith(temp.canonicalPath + File.separator) &&
                        canonical != temp.canonicalPath
                    ) {
                        return false to "压缩包包含非法路径"
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val manifestDir = findManifestDir(temp)
                ?: return false to "未找到 manifest.json（不是有效的插件包）"
            val manifest = PluginManifest.parse(File(manifestDir, "manifest.json"))
                ?: return false to "manifest.json 解析失败（缺少 id/name？）"
            val target = File(root, manifest.id)
            if (target.exists()) target.deleteRecursively()
            if (!manifestDir.renameTo(target)) {
                manifestDir.copyRecursively(target, overwrite = true)
            }
            temp.deleteRecursively()
            return true to "插件「${manifest.name}」安装成功"
        } catch (e: Exception) {
            temp.deleteRecursively()
            return false to "安装失败：${e.message ?: "未知错误"}"
        }
    }

    /** 从 ZIP 导入插件（content uri）。返回 (成功?, 消息) */
    fun installFromZip(context: Context, uri: Uri): Pair<Boolean, String> {
        val root = pluginRoot(context)
        val temp = File(root, ".tmp_install")
        try {
            if (temp.exists()) temp.deleteRecursively()
            temp.mkdirs()

            // 解压（防 ZipSlip）
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val outFile = File(temp, name)
                        val canonical = outFile.canonicalPath
                        if (!canonical.startsWith(temp.canonicalPath + File.separator) &&
                            canonical != temp.canonicalPath
                        ) {
                            return false to "压缩包包含非法路径"
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out -> zip.copyTo(out) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return false to "无法读取所选文件"

            // 兼容：manifest.json 可能在压缩包顶层或单层子目录内
            val manifestDir = findManifestDir(temp)
                ?: return false to "未找到 manifest.json（不是有效的插件包）"
            val manifest = PluginManifest.parse(File(manifestDir, "manifest.json"))
                ?: return false to "manifest.json 解析失败（缺少 id/name？）"

            val target = File(root, manifest.id)
            if (target.exists()) target.deleteRecursively()
            if (!manifestDir.renameTo(target)) {
                manifestDir.copyRecursively(target, overwrite = true)
            }
            temp.deleteRecursively()
            return true to "插件「${manifest.name}」安装成功"
        } catch (e: Exception) {
            temp.deleteRecursively()
            return false to "安装失败：${e.message ?: "未知错误"}"
        }
    }

    private fun findManifestDir(dir: File): File? {
        if (File(dir, "manifest.json").isFile) return dir
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory && File(child, "manifest.json").isFile) return child
        }
        return null
    }

    /** 安装内置示例插件（从 assets/plugins/hello-demo 复制） */
    fun installSamplePlugin(context: Context): Pair<Boolean, String> {
        val root = pluginRoot(context)
        val target = File(root, SAMPLE_PLUGIN_ID)
        return try {
            if (target.exists()) target.deleteRecursively()
            target.mkdirs()
            copyAssetDir(context, "plugins/$SAMPLE_PLUGIN_ID", target)
            true to "示例插件安装成功"
        } catch (e: Exception) {
            false to "示例安装失败：${e.message ?: "未知错误"}"
        }
    }

    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
        } else {
            target.mkdirs()
            children.forEach { child ->
                copyAssetDir(context, "$assetPath/$child", File(target, child))
            }
        }
    }
}