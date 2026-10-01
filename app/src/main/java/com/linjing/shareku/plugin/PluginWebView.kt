package com.linjing.shareku.plugin

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 创建插件专用 WebView：
 * - JS 桥（ShareKuBridge）
 * - alert / confirm 支持（WebView 默认静默忽略 alert，必须实现 WebChromeClient）
 */
@SuppressLint("SetJavaScriptEnabled")
fun createPluginWebView(
    context: Context,
    plugin: PluginInfo,
    loadUrl: String,
    transparent: Boolean = false,
    granted: Set<String> = plugin.granted
): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = false
        settings.setSupportZoom(false)
        if (transparent) setBackgroundColor(0)
        webViewClient = WebViewClient()
        webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(view.context)
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result.confirm() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(view.context)
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result.confirm() }
                    .setNegativeButton("取消") { _, _ -> result.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }
        }
        addJavascriptInterface(PluginBridge(context, plugin, granted), "ShareKuBridge")
        loadUrl(loadUrl)
    }
}