package com.linjing.shareku.server

import android.content.ClipboardManager
import android.content.Context
import com.linjing.shareku.data.LogEntry
import com.linjing.shareku.data.LogManager
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ShutDownUrl
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.request.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder
import java.nio.file.Files as JavaFiles
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ShareKuServer(
    private val context: Context,
    private val logManager: LogManager,
    val sharedFiles: List<File> = emptyList(),
    val isSingleFileSandbox: Boolean = false,
    val allowUpload: Boolean = false,
    val allowDelete: Boolean = false,
    val allowOverwrite: Boolean = true,
    val enableAuth: Boolean = false,
    val authUsername: String = "shareku",
    val authPassword: String = "share123",
    val enableWebDav: Boolean = true,
    val requireConfirm: Boolean = false,
    val uploadDir: File? = null,
    val clipboardManager: ClipboardManager? = null,
    // 连接确认回调：当新IP需要审批时调用，传入IP地址
    val onNewConnection: ((String) -> Unit)? = null
) {
    private val _logChannel = Channel<LogEntry>(Channel.UNLIMITED)
    private val _clipboardState = MutableStateFlow("")

    // IP 连接确认白名单/黑名单/待审批集合
    val approvedIps = mutableSetOf<String>()
    val blockedIps = mutableSetOf<String>()
    val pendingIps = mutableSetOf<String>()

    fun start(
        host: String,
        port: Int
    ) = embeddedServer(
        factory = CIO,
        port = port,
        host = host
    ) {
        install(Compression) {
            gzip { priority = 1.0 }
            deflate { priority = 10.0 }
        }
        install(PartialContent)
        install(ConditionalHeaders)
        install(CORS) {
            allowHost("localhost")
            allowHost("127.0.0.1")
            allowHost("0.0.0.0")
            allowHost("*.local")
            allowMethod(io.ktor.http.HttpMethod.Options)
            allowMethod(io.ktor.http.HttpMethod.Get)
            allowMethod(io.ktor.http.HttpMethod.Post)
            allowMethod(io.ktor.http.HttpMethod.Put)
            allowMethod(io.ktor.http.HttpMethod.Delete)
            allowMethod(io.ktor.http.HttpMethod.Head)
            allowMethod(io.ktor.http.HttpMethod.Options)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader("Depth")
            allowHeader("Destination")
            allowHeader("Overwrite")
        }
        install(WebSockets)

        routing {
            // Static assets (no auth required)
            get("/assets/style.css") {
                call.respondText(
                    context.assets.open("web/style.css").bufferedReader().use { it.readText() },
                    ContentType.Text.CSS
                )
            }
            get("/assets/script.js") {
                call.respondText(
                    context.assets.open("web/script.js").bufferedReader().use { it.readText() },
                    ContentType.Text.JavaScript
                )
            }

            // WebSocket for real-time logs & clipboard sync
            webSocket("/ws") {
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            when {
                                text.startsWith("clipboard:") -> {
                                    val data = text.removePrefix("clipboard:")
                                    clipboardManager?.let { cm ->
                                        val clip = android.content.ClipData.newPlainText("from_web", data)
                                        cm.setPrimaryClip(clip)
                                    }
                                    send("clipboard:ok")
                                }
                                text == "get_clipboard" -> {
                                    val clip = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    send("clipboard_data:$clip")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Client disconnected
                }
            }

            // Unauthenticated endpoints
            get("/api/status") {
                call.respondText("""{"allowUpload":$allowUpload,"allowDelete":$allowDelete,"authRequired":$enableAuth,"webdav":$enableWebDav}""", ContentType.Application.Json)
            }
            get("/api/clipboard") {
                val clip = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                call.respondText("""{"text":"${clip.replace("\"","\\\"")}"}""", ContentType.Application.Json)
            }
            get("/api/files") {
                call.respondText(
                    buildFileListJson(sharedFiles.map { fileToJson(it) }),
                    ContentType.Application.Json
                )
            }

            // Authenticated routes (use checkAuth inline)
            get("/") {
                if (!checkIp(call)) return@get
                if (!checkAuth(call)) { call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized); return@get }
                logRequest(call, 200)
                call.respondText(buildWebUI(), ContentType.Text.Html)
            }
            get("/download") {
                if (!checkIp(call)) return@get
                if (!checkAuth(call)) { call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized); return@get }
                val reqPath = call.request.queryParameters["path"] ?: ""
                val file = resolvePath(reqPath)
                if (file != null && file.exists() && file.isFile && isAllowed(file)) {
                    logRequest(call, 200, file.length())
                    call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.name}\"")
                    call.response.headers.append(HttpHeaders.ContentType, guessContentType(file).toString())
                    call.respondFile(file)
                } else {
                    logRequest(call, 404)
                    call.respondText("404 Not Found", status = HttpStatusCode.NotFound)
                }
            }
            get("/api/list") {
                if (!checkIp(call)) return@get
                if (!checkAuth(call)) { call.respondText("""{"error":"Unauthorized"}""", status = HttpStatusCode.Unauthorized); return@get }
                val reqPath = call.request.queryParameters["path"] ?: ""
                val dir = resolvePath(reqPath)
                if (dir != null && dir.isDirectory && isAllowed(dir)) {
                    val files = dir.listFiles()?.map { fileToJson(it) }
                    logRequest(call, 200)
                    call.respondText(buildFileListJson(files), ContentType.Application.Json)
                } else if (dir != null && dir.isFile && isAllowed(dir)) {
                    logRequest(call, 200)
                    call.respondText(buildFileListJson(listOf(fileToJson(dir))), ContentType.Application.Json)
                } else {
                    logRequest(call, 404)
                    val dbg = if (dir == null) "null" else "exist=${dir.exists()} isDir=${dir.isDirectory}"
                    call.respondText("""{"error":"404","debug":"$dbg","path":"$reqPath"}""", status = HttpStatusCode.NotFound)
                }
            }
            get("/api/zip") {
                if (!checkIp(call)) return@get
                val paths = call.request.queryParameters.getAll("paths") ?: emptyList()
                val counter = AtomicLong(0)
                val byteCounter = AtomicLong(0)
                try {
                    logRequest(call, 200)
                    call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"shareku_archive.zip\"")
                    call.respondOutputStream(ContentType.Application.Zip) {
                        ZipOutputStream(this).use { zos ->
                            for (path in paths) {
                                val file = resolvePath(path)
                                if (file != null && isAllowed(file)) {
                                    addToZip(file, "", zos, counter, byteCounter)
                                }
                            }
                        }
                    }
                } catch (e: ZipLimitExceededException) {
                    throw e
                }
            }
            if (allowUpload) {
                post("/api/upload") {
                    if (!checkIp(call)) return@post
                    if (!checkAuth(call)) { call.respondText("""{"error":"Unauthorized"}""", status = HttpStatusCode.Unauthorized); return@post }
                    val root = getRootDir() ?: run {
                        call.respondText("""{"error":"No shared directory"}""", status = HttpStatusCode.BadRequest)
                        return@post
                    }
                    val queryName = call.request.queryParameters["name"]
                    try {
                        val channel = call.request.receiveChannel()
                        val origName = (queryName ?: "upload_${System.currentTimeMillis()}")
                            .let { URLDecoder.decode(it, "UTF-8") }
                            .replace("/", "_").replace("\\", "_")
                        var dest = File(root, origName)
                        var counter = 1
                        while (dest.exists()) {
                            val dot = origName.lastIndexOf('.')
                            val base = if (dot > 0) origName.substring(0, dot) else origName
                            val ext = if (dot > 0) origName.substring(dot) else ""
                            dest = File(root, "${base}_${counter}${ext}")
                            counter++
                        }
                        dest.outputStream().use { fos ->
                            val buf = ByteArray(32768)
                            while (!channel.isClosedForRead) {
                                val r = channel.readAvailable(buf, 0, buf.size)
                                if (r <= 0) break
                                fos.write(buf, 0, r)
                            }
                        }
                        logRequest(call, 200, dest.length())
                        call.respondText("""{"uploaded":1,"name":"${dest.name}","size":${dest.length()}}""", ContentType.Application.Json)
                    } catch (e: Exception) {
                        logRequest(call, 500)
                        call.respondText("""{"error":"${e.message}"}""", status = HttpStatusCode.InternalServerError)
                    }
                }
            }
            if (allowDelete) {
                delete("/api/delete") {
                    if (!checkIp(call)) return@delete
                    val path = call.request.queryParameters["path"] ?: ""
                    val file = resolvePath(path)
                    if (file != null && isAllowed(file) && deleteRecursively(file)) {
                        logRequest(call, 200)
                        call.respondText("""{"deleted":true}""", ContentType.Application.Json)
                    } else {
                        call.respondText("""{"deleted":false}""", status = HttpStatusCode.BadRequest)
                    }
                }
            }
            if (enableWebDav) {
                route("/webdav") {
                    route("/{...}") {
                        handle { handleWebDav(call) }
                    }
                }
            }
        }
    }

    // Recursive delete — handles non-empty directories
    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        return file.delete()
    }

    class ZipLimitExceededException(msg: String) : Exception(msg)

    companion object {
        const val MAX_ZIP_FILES = 10000L
        const val MAX_ZIP_BYTES = 4L * 1024 * 1024 * 1024  // 4 GB
    }

    private suspend fun logRequest(call: ApplicationCall, status: Int, bytes: Long = 0) {
        val entry = LogEntry(
            ip = call.request.local.remoteHost,
            method = call.request.httpMethod.value,
            path = call.request.uri,
            status = status,
            userAgent = call.request.header(HttpHeaders.UserAgent) ?: "",
            bytesTransferred = bytes
        )
        logManager.addEntry(entry)
    }

    // 【安全】路径解析 —— 防路径穿越
    private fun resolvePath(path: String): File? {
        if (isSingleFileSandbox && sharedFiles.size == 1) return sharedFiles.firstOrNull()
        val decoded = URLDecoder.decode(path.trimStart('/'), "UTF-8").trim()
        val root = getRootDir() ?: return null
        if (decoded.isEmpty()) return root
        val target = File(root, decoded)
        // 路径穿越检查：标准化路径后必须仍在根目录内
        val rootCanonical = root.canonicalPath
        val targetCanonical = target.canonicalFile.canonicalPath
        if (!targetCanonical.startsWith(rootCanonical + "/") && targetCanonical != rootCanonical) {
            return null // 拒绝路径穿越
        }
        return target
    }

    private fun resolveWebDavPath(path: String): File? {
        return resolvePath(path)
    }

    private fun isAllowed(file: File): Boolean {
        if (!isSingleFileSandbox) return true
        return sharedFiles.any { sf ->
            file.absolutePath == sf.absolutePath ||
            file.absolutePath.startsWith(sf.absolutePath)
        }
    }

    // 获取共享根目录，用于计算相对路径
    private fun getRootDir(): File? {
        val first = sharedFiles.firstOrNull() ?: return null
        return if (first.isDirectory) first else first.parentFile
    }

    // 手动 HTTP Basic Auth 验证 —— 参考网页文件挂载器的实现
    private fun checkAuth(call: ApplicationCall): Boolean {
        if (!enableAuth) return true
        val authHeader = call.request.header("authorization") ?: run {
            call.response.headers.append("WWW-Authenticate", "Basic realm=\"ShareKu\"")
            return false
        }
        if (!authHeader.startsWith("Basic ", ignoreCase = true)) {
            call.response.headers.append("WWW-Authenticate", "Basic realm=\"ShareKu\"")
            return false
        }
        try {
            val encoded = authHeader.removePrefix("Basic ").trim()
            val decoded = String(Base64.getDecoder().decode(encoded))
            val colonIdx = decoded.indexOf(':')
            if (colonIdx < 0) return false
            val user = decoded.substring(0, colonIdx)
            val pass = decoded.substring(colonIdx + 1)
            return user == authUsername && pass == authPassword
        } catch (e: Exception) {
            return false
        }
    }

    // 【连接确认】IP白名单检查 —— 首次访问需Android端审批
    // 返回 true=放行, false=已拦截(已返回等待页或403)
    private suspend fun checkIp(call: ApplicationCall): Boolean {
        if (!requireConfirm) return true
        val ip = call.request.local.remoteHost
        // 本机回环地址永远放行
        if (ip == "127.0.0.1" || ip == "0:0:0:0:0:0:0:1" || ip == "localhost") return true
        // 已批准 → 放行
        if (ip in approvedIps) return true
        // 已拉黑 → 403
        if (ip in blockedIps) {
            call.respondText(buildForbiddenHtml(), ContentType.Text.Html, HttpStatusCode.Forbidden)
            return false
        }
        // 新IP → 加入待审批，发通知，返回等待页
        if (ip !in pendingIps) {
            pendingIps.add(ip)
            // 异步回调，不阻塞当前请求
            onNewConnection?.invoke(ip)
        }
        // 返回等待审批页面（浏览器每3秒自动刷新）
        call.respondText(buildWaitingHtml(ip), ContentType.Text.Html)
        return false
    }

    // 等待审批的HTML页面 —— 简洁清新，自动刷新
    private fun buildWaitingHtml(ip: String): String {
        return """<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<meta http-equiv="refresh" content="3">
<title>等待授权 - ShareKu</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
 background:#f5f8f6;color:#2d3436;min-height:100vh;display:flex;
 align-items:center;justify-content:center;-webkit-font-smoothing:antialiased}
.card{background:#fff;border-radius:20px;padding:48px 40px;text-align:center;
 box-shadow:0 1px 3px rgba(0,0,0,.06),0 4px 12px rgba(0,0,0,.08);max-width:400px;margin:20px}
.spinner{width:48px;height:48px;border:3px solid #dde4e1;border-top-color:#50998b;
 border-radius:50%;animation:spin .8s linear infinite;margin:0 auto 24px}
@keyframes spin{to{transform:rotate(360deg)}}
h1{font-size:20px;font-weight:600;margin-bottom:8px;color:#2d3436}
p{font-size:14px;color:#636e72;line-height:1.5}
.ip{display:inline-block;background:#e8f3f0;color:#3d7a6e;padding:2px 10px;
 border-radius:6px;font-family:monospace;font-size:13px;margin:4px 0}
.hint{margin-top:20px;font-size:12px;color:#b2bec3}
</style></head>
<body>
<div class="card">
 <div class="spinner"></div>
 <h1>等待设备授权</h1>
 <p>你的设备 <span class="ip">$ip</span><br>正在请求访问 ShareKu</p>
 <p class="hint">请在手机上批准此连接 · 页面每3秒自动刷新</p>
</div>
</body></html>""".trimIndent()
    }

    // 已拉黑的提示页
    private fun buildForbiddenHtml(): String {
        return """<!DOCTYPE html>
<html lang="zh">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>访问被拒 - ShareKu</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
 background:#f5f8f6;color:#2d3436;min-height:100vh;display:flex;
 align-items:center;justify-content:center;-webkit-font-smoothing:antialiased}
.card{background:#fff;border-radius:20px;padding:48px 40px;text-align:center;
 box-shadow:0 1px 3px rgba(0,0,0,.06),0 4px 12px rgba(0,0,0,.08);max-width:400px;margin:20px}
.icon{width:56px;height:56px;border-radius:50%;background:#fdf0f0;color:#d38c8c;
 display:flex;align-items:center;justify-content:center;font-size:28px;margin:0 auto 20px}
h1{font-size:20px;font-weight:600;margin-bottom:8px}
p{font-size:14px;color:#636e72;line-height:1.5}
</style></head>
<body>
<div class="card">
 <div class="icon">&#x2715;</div>
 <h1>访问已被拒绝</h1>
 <p>此设备的连接请求已被管理员拒绝</p>
</div>
</body></html>""".trimIndent()
    }

    private fun fileToJson(file: File): Map<String, Any?> {
        val root = getRootDir()
        // 使用相对于共享根目录的路径，而非绝对路径
        val relPath = if (root != null && file.absolutePath.startsWith(root.absolutePath)) {
            file.absolutePath.removePrefix(root.absolutePath).trimStart('/')
        } else {
            file.absolutePath
        }
        return mapOf(
            "name" to file.name,
            "path" to relPath,
            "size" to file.length(),
            "isDirectory" to file.isDirectory,
            "lastModified" to file.lastModified(),
            "mimeType" to guessMimeType(file.name)
        )
    }

    private fun jsonEscape(s: String): String {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
    }

    private fun buildFileListJson(files: List<Map<String, Any?>>?): String {
        val sb = StringBuilder("[")
        files?.forEachIndexed { index, file ->
            sb.append("{")
            file.forEach { (key, value) ->
                sb.append("\"$key\":")
                when (value) {
                    is String -> sb.append("\"${jsonEscape(value)}\"")
                    is Number -> sb.append(value)
                    is Boolean -> sb.append(value)
                    null -> sb.append("null")
                    else -> sb.append("\"${jsonEscape(value.toString())}\"")
                }
                sb.append(",")
            }
            sb.setLength(sb.length - 1) // remove trailing comma
            sb.append("}")
            if (index < files.size - 1) sb.append(",")
        }
        sb.append("]")
        return sb.toString()
    }

    private suspend fun handleWebDav(call: ApplicationCall) {
        if (!checkIp(call)) return
        if (!checkAuth(call)) { call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized); return }
        val method = call.request.httpMethod.value
        when {
            method == "OPTIONS" -> {
                call.response.headers.append("Allow", "GET, PUT, DELETE, OPTIONS, PROPFIND, MKCOL, COPY, MOVE, HEAD")
                call.response.headers.append("DAV", "1,2")
                call.response.headers.append("MS-Author-Via", "DAV")
                call.respondText("")
            }
            method == "HEAD" -> {
                val path = call.request.uri.removePrefix("/webdav")
                val file = resolveWebDavPath(path)
                if (file != null && file.isFile && isAllowed(file)) {
                    logRequest(call, 200, file.length())
                    call.response.headers.append(HttpHeaders.ContentType, guessContentType(file).toString())
                    call.response.headers.append(HttpHeaders.ContentLength, file.length().toString())
                    call.respondText("")
                } else { logRequest(call, 404); call.respondText("404", status = HttpStatusCode.NotFound) }
            }
            method == "GET" -> {
                val path = call.request.uri.removePrefix("/webdav")
                val file = resolveWebDavPath(path)
                if (file != null && file.isFile && isAllowed(file)) {
                    logRequest(call, 200, file.length())
                    call.response.headers.append(HttpHeaders.ContentType, guessContentType(file).toString())
                    call.respondFile(file)
                } else {
                    logRequest(call, 404)
                    call.respondText("404", status = HttpStatusCode.NotFound)
                }
            }
            method == "PROPFIND" -> {
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="utf-8"?>""")
                sb.append("""<D:multistatus xmlns:D="DAV:">""")
                val path = call.request.uri.removePrefix("/webdav")
                val file = resolveWebDavPath(path)
                val depth = call.request.header("Depth") ?: "infinity"
                if (file != null && file.exists()) {
                    appendPropfindResponse(sb, file, path)
                    // Depth 1: also list immediate children of directories
                    if (file.isDirectory && (depth == "1" || depth == "infinity")) {
                        file.listFiles()?.forEach { child ->
                            val childPath = if (path.endsWith("/")) "$path${child.name}" else "$path/${child.name}"
                            appendPropfindResponse(sb, child, childPath)
                        }
                    }
                }
                sb.append("</D:multistatus>")
                logRequest(call, 207)
                call.respondText(sb.toString(), ContentType.Text.Xml)
            }
            method == "PUT" -> {
                if (!allowUpload) {
                    call.respondText("Upload disabled", status = HttpStatusCode.Forbidden)
                    return
                }
                val outPath = call.request.uri.removePrefix("/webdav")
                val file = resolveWebDavPath(outPath)
                if (file != null && !allowOverwrite && file.exists()) {
                    call.respondText("File exists", status = HttpStatusCode.Conflict)
                } else {
                    file?.parentFile?.mkdirs()
                    // 流式接收二进制数据
                    val channel = call.request.receiveChannel()
                    file?.outputStream()?.use { fos ->
                        val buf = ByteArray(32768)
                        while (!channel.isClosedForRead) {
                            val r = channel.readAvailable(buf, 0, buf.size)
                            if (r <= 0) break
                            fos.write(buf, 0, r)
                        }
                    }
                    logRequest(call, 201, file?.length() ?: 0)
                    call.respondText("Created", status = HttpStatusCode.Created)
                }
            }
            method == "DELETE" -> {
                if (!allowDelete) {
                    call.respondText("Delete disabled", status = HttpStatusCode.Forbidden)
                    return
                }
                val path = call.request.uri.removePrefix("/webdav")
                val file = resolveWebDavPath(path)
                if (file != null && deleteRecursively(file)) {
                    logRequest(call, 204)
                    call.respondText("Deleted", status = HttpStatusCode.NoContent)
                } else {
                    call.respondText("Not found", status = HttpStatusCode.NotFound)
                }
            }
            method == "MKCOL" -> {
                val mPath = call.request.uri.removePrefix("/webdav")
                val mFile = resolveWebDavPath(mPath)
                if (mFile != null && mFile.mkdirs()) {
                    logRequest(call, 201)
                    call.respondText("Created", status = HttpStatusCode.Created)
                } else {
                    call.respondText("Failed", status = HttpStatusCode.Conflict)
                }
            }
        }
    }

    private fun addToZip(file: File, parentPath: String, zos: ZipOutputStream,
                        fileCounter: AtomicLong, byteCounter: AtomicLong) {
        // Check limits
        if (fileCounter.incrementAndGet() > MAX_ZIP_FILES) {
            throw ZipLimitExceededException("Too many files (max $MAX_ZIP_FILES)")
        }
        val entryPath = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
        if (file.isDirectory) {
            zos.putNextEntry(ZipEntry("$entryPath/"))
            zos.closeEntry()
            file.listFiles()?.forEach { addToZip(it, entryPath, zos, fileCounter, byteCounter) }
        } else {
            val fileSize = file.length()
            if (byteCounter.addAndGet(fileSize) > MAX_ZIP_BYTES) {
                throw ZipLimitExceededException("Archive too large (max ${MAX_ZIP_BYTES / (1024*1024*1024)} GB)")
            }
            zos.putNextEntry(ZipEntry(entryPath))
            FileInputStream(file).use { it.copyTo(zos, 65536) }
            zos.closeEntry()
        }
    }

    private fun appendPropfindResponse(sb: StringBuilder, file: File, path: String) {
        val href = "/webdav${if (path.startsWith("/")) path else "/$path"}"
        sb.append("<D:response><D:href>$href</D:href>")
        sb.append("<D:propstat><D:prop>")
        if (file.isDirectory) {
            sb.append("<D:resourcetype><D:collection/></D:resourcetype>")
        }
        sb.append("<D:getcontentlength>${file.length()}</D:getcontentlength>")
        sb.append("<D:getlastmodified>${java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).format(java.util.Date(file.lastModified()))}</D:getlastmodified>")
        sb.append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>")
        sb.append("</D:response>")
    }

    private fun guessContentType(file: File): ContentType {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".html") -> ContentType.Text.Html
            name.endsWith(".css") -> ContentType.Text.CSS
            name.endsWith(".js") -> ContentType.Text.JavaScript
            name.endsWith(".json") -> ContentType.Application.Json
            name.endsWith(".xml") -> ContentType.Text.Xml
            name.endsWith(".txt") || name.endsWith(".log") -> ContentType.Text.Plain
            name.endsWith(".png") -> ContentType.Image.PNG
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> ContentType.Image.JPEG
            name.endsWith(".gif") -> ContentType.Image.GIF
            name.endsWith(".svg") -> ContentType.Image.SVG
            name.endsWith(".webp") -> ContentType("image", "webp")
            name.endsWith(".mp4") -> ContentType.Video.MP4
            name.endsWith(".webm") -> ContentType("video", "webm")
            name.endsWith(".mp3") -> ContentType("audio", "mpeg")
            name.endsWith(".ogg") -> ContentType("audio", "ogg")
            name.endsWith(".wav") -> ContentType("audio", "wav")
            name.endsWith(".pdf") -> ContentType.Application.Pdf
            name.endsWith(".zip") -> ContentType.Application.Zip
            name.endsWith(".apk") -> ContentType("application", "vnd.android.package-archive")
            name.endsWith(".md") -> ContentType("text", "markdown")
            else -> ContentType.Application.OctetStream
        }
    }

    private fun guessMimeType(name: String): String {
        return when {
            name.endsWith(".html") -> "text/html"
            name.endsWith(".css") -> "text/css"
            name.endsWith(".js") -> "text/javascript"
            name.endsWith(".json") -> "application/json"
            name.endsWith(".xml") -> "text/xml"
            name.endsWith(".txt") || name.endsWith(".log") -> "text/plain"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".svg") -> "image/svg+xml"
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".webm") -> "video/webm"
            name.endsWith(".mp3") -> "audio/mpeg"
            name.endsWith(".ogg") -> "audio/ogg"
            name.endsWith(".wav") -> "audio/wav"
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".zip") -> "application/zip"
            name.endsWith(".apk") -> "application/vnd.android.package-archive"
            name.endsWith(".md") -> "text/markdown"
            name.endsWith(".py") -> "text/x-python"
            name.endsWith(".kt") -> "text/x-kotlin"
            name.endsWith(".java") -> "text/x-java"
            name.endsWith(".cpp") || name.endsWith(".c") || name.endsWith(".h") -> "text/x-c"
            else -> "application/octet-stream"
        }
    }
    private fun buildWebUI(): String {
        return try {
            context.assets.open("web/index.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body><h1>ShareKu</h1><p>Error loading UI: ${e.message}</p></body></html>"
        }
    }
}
