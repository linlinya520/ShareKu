package com.linjing.shareku

import android.content.Context
import com.linjing.shareku.data.LogManager
import com.linjing.shareku.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple manual DI container - replaces Hilt.
 * Initialized in LocalShareApp.onCreate().
 */
object AppSingletons {
    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var logManager: LogManager
        private set

    // Global server running state - reactive StateFlow for UI binding
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    fun setServerRunning(running: Boolean) {
        _isServerRunning.value = running
    }

    // IP 连接跟踪 —— 用于连接确认和黑名单
    val pendingConnections = mutableMapOf<String, Boolean>() // IP -> allowed
    val blockedIPs = mutableSetOf<String>()

    // 审批状态 —— UI 和通知共享，支持多用户队列
    private val pendingIpQueue = mutableListOf<String>()
    private val _pendingConfirmIp = MutableStateFlow<String?>(null)
    val pendingConfirmIp: StateFlow<String?> = _pendingConfirmIp.asStateFlow()
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    /** 入队一个待审批 IP，UI 自动弹出审批弹窗 */
    fun enqueuePendingIp(ip: String) {
        pendingIpQueue.add(ip)
        _pendingCount.value = pendingIpQueue.size
        if (_pendingConfirmIp.value == null) {
            _pendingConfirmIp.value = ip
        }
    }

    /** 当前 IP 处理完毕，出队下一个 */
    fun dequeuePendingIp() {
        if (pendingIpQueue.isNotEmpty()) {
            pendingIpQueue.removeFirst()
        }
        _pendingCount.value = pendingIpQueue.size
        _pendingConfirmIp.value = pendingIpQueue.firstOrNull()
    }

    /** 清空审批队列（服务停止时） */
    fun clearPendingQueue() {
        pendingIpQueue.clear()
        _pendingCount.value = 0
        _pendingConfirmIp.value = null
    }

    fun init(context: Context) {
        preferencesManager = PreferencesManager(context)
        logManager = LogManager()
    }
}