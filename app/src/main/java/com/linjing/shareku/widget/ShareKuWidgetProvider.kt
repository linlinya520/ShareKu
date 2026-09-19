package com.linjing.shareku.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.MainActivity
import com.linjing.shareku.R
import com.linjing.shareku.server.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 桌面小组件：显示服务器运行状态与访问地址，支持自由调整大小。
 * 点击 → 打开 App 并自动启动服务器。
 */
class ShareKuWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == REFRESH_ACTION) refresh(context)
    }

    companion object {
        /** 打开 App 时是否自动启动服务器 */
        const val EXTRA_AUTO_START = "extra_auto_start"
        const val REFRESH_ACTION = "com.linjing.shareku.action.WIDGET_REFRESH"

        /** 刷新所有小组件实例 */
        fun refresh(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(
                    ComponentName(context, ShareKuWidgetProvider::class.java)
                )
                ids.forEach { render(context, mgr, it) }
            } catch (_: Exception) {}
        }

        private fun render(context: Context, mgr: AppWidgetManager, id: Int) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = AppSingletons.preferencesManager
                    val running = AppSingletons.isServerRunning.value
                    val port = prefs.port.first()
                    val ip = try {
                        NetworkUtils().getPreferredInterface(prefs.networkInterface.first())?.ipAddress
                    } catch (_: Exception) { null }

                    val layoutId = if (running) R.layout.widget_shareku_on else R.layout.widget_shareku
                    val views = RemoteViews(appContext.packageName, layoutId)

                    val url = "http://${ip ?: "?"}:$port"
                    // 打开 App（带自动启动）
                    val openApp = Intent(appContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(EXTRA_AUTO_START, !running)
                    }
                    // 整卡点击 → 打开 App（停止态会自动启动服务器）
                    views.setOnClickPendingIntent(
                        R.id.widget_root,
                        PendingIntent.getActivity(
                            appContext, id, openApp,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )

                    if (running) {
                        views.setTextViewText(R.id.widget_url, "${ip ?: "?"}:$port")
                        // 按钮：浏览器打开共享页
                        val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        views.setOnClickPendingIntent(
                            R.id.widget_action,
                            PendingIntent.getActivity(
                                appContext, id + 1, web,
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        )
                    } else {
                        views.setTextViewText(R.id.widget_url, "点击启动局域网共享")
                        // 按钮：启动服务器（打开 App 并自动启动）
                        views.setOnClickPendingIntent(
                            R.id.widget_action,
                            PendingIntent.getActivity(
                                appContext, id + 2, openApp,
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        )
                    }
                    mgr.updateAppWidget(id, views)
                } catch (_: Exception) {}
            }
        }
    }
}