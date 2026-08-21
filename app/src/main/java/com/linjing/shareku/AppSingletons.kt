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

    // 审批状态 —— UI 和通知共享，支持多用户队列（ip -> 一次性验证码）
    private val pendingIpQueue = mutableListOf<Pair<String, String>>()
    private val _pendingConfirmIp = MutableStateFlow<String?>(null)
    val pendingConfirmIp: StateFlow<String?> = _pendingConfirmIp.asStateFlow()
    private val _pendingConfirmCode = MutableStateFlow<String?>(null)
    val pendingConfirmCode: StateFlow<String?> = _pendingConfirmCode.asStateFlow()
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    /** 入队一个待审批 IP（附带一次性验证码），UI 自动弹出审批卡片 */
    fun enqueuePendingIp(ip: String, code: String) {
        pendingIpQueue.add(ip to code)
        _pendingCount.value = pendingIpQueue.size
        if (_pendingConfirmIp.value == null) {
            _pendingConfirmIp.value = ip
            _pendingConfirmCode.value = code
        }
    }

    /** 当前 IP 处理完毕，出队下一个 */
    fun dequeuePendingIp() {
        if (pendingIpQueue.isNotEmpty()) {
            pendingIpQueue.removeFirst()
        }
        _pendingCount.value = pendingIpQueue.size
        val next = pendingIpQueue.firstOrNull()
        _pendingConfirmIp.value = next?.first
        _pendingConfirmCode.value = next?.second
    }

    /** 清空审批队列（服务停止时） */
    fun clearPendingQueue() {
        pendingIpQueue.clear()
        _pendingCount.value = 0
        _pendingConfirmIp.value = null
        _pendingConfirmCode.value = null
    }

    /** 当前活跃的共享文件（缓存清理跳过） */
    val activeSharedFiles = mutableSetOf<String>()

    fun init(context: Context) {
        preferencesManager = PreferencesManager(context)
        logManager = LogManager()
    }
}