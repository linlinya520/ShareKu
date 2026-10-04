package com.linjing.shareku.ui.screen

import com.linjing.shareku.ui.component.AppTopBar
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.component.CustomCard
import com.linjing.shareku.ui.theme.ShareThemeWrapper

/** aShellYou 风格更新日志 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current

    data class ChangelogEntry(val version: String, val date: String, val items: List<String>)

    val changelogs = remember {
        listOf(
            ChangelogEntry("v2.0.2", "2026-10", listOf(
                "修复：Shizuku 模式下进入受保护目录后按返回键闪退（JDK21 专有 API 在旧系统不存在；已改写并开启 core library desugaring 全局兜底）",
                "修复：诊断日志在部分机型（vivo Android 11）抓不到内容（logcat 不支持 --uid，现自动改用 --pid）",
                "修复：打开二级页面后背景约 1 秒才出现（壁纸解码结果加入内存缓存，打开即显示）",
                "修复：连接日志页背景全透明、文字与壁纸重叠（改为不透明底板）",
                "优化：悬浮弹窗不再套用「卡片不透明度」，液态玻璃下改为强磨砂，避免与下层内容重叠",
            )),
            ChangelogEntry("v2.0.1", "2026-10", listOf(
                "「自定义颜色」升级为 HSV 取色面板：色板拖动选色 + 色相条 + 明度条，HEX 可精确输入",
                "液态玻璃滑条重做：玻璃胶囊外壳 + 7dp 细轨道 + 玻璃拇指，跟手更自然",
                "新增「卡片不透明度」：使用自定义背景时让卡片 / 面板透出背景",
                "修复：Android 12 以下设备动态取色被锁死，导致配色方案无法修改",
                "修复：深色模式打开二级页面时先闪一帧白色（窗口底色双保险）",
                "修复：MIUI 风格下卡片不透明度形同虚设（改为明确提示，不再误导）",
                "修复：分享面板未跟随自定义配色 / 动态取色设置",
                "新增「日志采集」（工具页）：一键记录全量运行日志，逐行实时落盘——即使记录中闪退也不丢；支持查看 / 复制 / 导出 / 清除，崩溃堆栈自动写入",
            )),
            ChangelogEntry("v2.0.0", "2026-10", listOf(
                "液态玻璃主题全面重做：实时折射 / 模糊 / 边缘色散 / 高光 / 投影，Dock 液滴滑块",
                "三套界面风格（Material 3 / MIUI / 液态玻璃）× 两套布局（经典 / 底部悬浮 Dock）",
                "预测性返回 + 返回过渡模糊 + 页面转场动画（均为独立开关）",
                "动画与交互五开关：预见式返回预览 / 返回模糊 / 转场 / 骨架呼吸 / Dock 滑块",
                "设置搜索：关键词（标题 / 说明 / 分类 / 别名）直达任意设置项",
                "插件生态：插件市场（GitHub 仓库 + jsDelivr 驱动）、JS 桥、主页卡片、网络能力、皮肤包",
                "全局代码审计修复：主线程 IO、WebView 释放、皮肤重置等",
                "修复：插件授权后仍报未授权（授权状态失配）",
            )),
            ChangelogEntry("v1.4.0", "2026-09", listOf(
                "苹果液态玻璃主题：实时折射 / 模糊 / 边缘色散 / 高光 / 投影，全局壁纸作为折射源",
                "全新外观系统：三种界面风格（Material 3 / MIUI / 液态玻璃）× 两套布局（经典 / 底部悬浮 Dock）",
                "全局壁纸：默认 / 本地图片 / 本地视频 / 系统壁纸，支持双指缩放 + 拖动定位 + 360° 旋转（快速旋转自动吸附水平 / 垂直，缓慢微调不吸附）",
                "本地视频壁纸：循环 / 声音 / 音量调节（Media3 ExoPlayer）",
                "液态玻璃滑条：拇指为真实玻璃球，按压时放大 + 高亮 + 液滴鼓包 + 边缘色散",
                "首次启用液态玻璃自动预渲染引导（仅出现一次）",
                "性能优化：卡片玻璃低成本实现、预组合策略调整、重 IO / 解码全部下沉后台线程",
                "安全加固：WebSocket 与删除接口补充鉴权、剪贴板 / 上传接口转义修复、验证码失败限速、共享根目录防删除保护",
                "动态壁纸支持：系统壁纸为动态壁纸（如 Wallpaper Engine）时由系统层直接透出显示",
                "新增「清除本地壁纸占用」：删除已保存的壁纸文件并切回默认壁纸（壁纸文件不再永久残留）",
                "修复：设备直连发送端进度改为逐字节实时、端口输入校验、关于页版本号动态化、端口占用自动切换后地址同步",
            )),
            ChangelogEntry("v1.3.1", "2026-08", listOf(
                "网页端新增拖拽上传：支持多文件与文件夹，并新增深色模式",
                "新增控制中心快捷磁贴：一键启动服务器并打开 App",
                "新增桌面小组件：可调整大小，运行态青绿渐变显示访问地址",
                "关于页致谢开发者 CINXZ，版本号同步更新",
                "修复快速输入密码时光标跳动（输入框改为本地状态）",
                "修复服务器端口输入框同类光标问题",
                "图片资源改为 assets 可选加载：文件缺失时自动降级为占位图，保证任何环境可编译",
                "界面文案统一移除 emoji",
            )),
            ChangelogEntry("v1.3.0", "2026-08", listOf(
                "MIUI 风格界面系统：设置→外观可切换 Material 3 / MIUI 双风格",
                "接入 miuix 组件库：主题对齐 Spec2021 + BlurScaffold 毛玻璃",
                "MIUI 分组卡片：整组一个背景卡片+内部分割线，四角大圆角",
                "覆盖式展开选择组件：点击从锚点弹出，流水缩放动画，选中打勾",
                "关于页致谢开发者 CINXZ（miuix 组件参考，圆形头像+超链接）",
                "安全升级：一次性 4 位验证码+会话令牌替代 IP 白名单（改 IP 无法绕过审批）",
                "唤醒锁 + WiFi 锁：锁屏后台传输不中断",
                "⚡ 体积优化：开启 R8 裁切 + 移除冗余 Netty 引擎，APK 由 20MB 降至 2.8MB",
                "WebDAV 映射脚本兼容 Win7/10/11，服务端 PROPFIND 修复（Windows 资源管理器不再报位置不可用）",
                "全套组件 MIUI 化：按钮/开关/对话框/输入框/顶栏/展开式选择全面落地",
            )),
            ChangelogEntry("v1.2.0", "2026-08", listOf(
                "Shizuku 高权限文件访问：可浏览并共享 /storage/emulated/0/Android/data 等受限目录（需安装并授权 Shizuku）",
                "受限目录共享：选择 Android/data 等目录作为共享根后，网页端可真实列出并下载文件（Shizuku 模式下）",
                "文件浏览器：Shizuku 模式显示对应包名的 App 图标角标，方便定位应用目录",
                "分架构构建：按 x86 / x86_64 / arm64-v8a / armeabi-v7a 拆分为四个 APK，单包体积减小",
                "文件浏览器性能优化：目录列表改为异步加载，滚动帧率显著提升",
                "缓存清理修复：递归清理子目录缓存，显示真实释放大小",
                "安全加固：/api/files、/api/zip、/api/clipboard 增加认证校验",
                "启动服务器时对受限目录给出未授权 Shizuku 的明确提示",
            )),
            ChangelogEntry("v1.1.3", "2026-08", listOf(
                "设备直连传输：mDNS(NSD)自动发现附近设备，App间直接传文件，无需浏览器",
                "直连传输审批：接收端弹出通知，接受/拒绝后通知自动消失",
                "手动输入IP连接：NSD扫不到时可手动输入对方WiFi IP直连",
                "后台定位保活：解决鸿蒙/国产ROM熄屏或切后台后断网问题",
                "主页定位保活快捷开关：含状态实时检测（权限+系统定位服务）",
                "⚙安全设置页新增后台保护区块：定位保活开关+详细说明文案",
                "端口自动递归：占用时递增端口不闪退，提示建议更换端口",
                "修复文件名含冒号导致传输EPERM错误",
                "修复分享服务器与主服务器端口冲突",
                "修复传输接收目录未生效、文件保存到应用私有目录",
                "修复设备直连大文件传输 OOM（改为流式发送，内存占用恒定）",
                "未经充分测试，可能存在bug，欢迎提交 Issue",
            )),
            ChangelogEntry("v1.1.2", "2026-08", listOf(
                "设置子页面全部独立为 Activity，享受系统级预测性返回动画",
                "外观体验滚动流畅度大幅优化：LazyColumn 替换为 Column+verticalScroll",
                "缓存清理界面 UI 统一：卡片背景与页面融合，不再凸起",
                "缓存自动清理机制：启动时检查间隔，跳过活跃共享文件",
                "浏览器分享文件名修复：通过 ContentResolver 查询真实文件名",
                "主页新增缓存横条，支持一键手动清理",
                "设置新增缓存清理子页面，可配置自动清理间隔",
                "首页与设置页面切换动画统一为匀速整屏滑动",
            )),
            ChangelogEntry("v1.1.1", "2026-07", listOf(
                "剪贴板面板重构: 可收起为悬浮球, 拖拽移动, 5px死区防误触",
                "修复悬浮球点击穿透到后方文件卡片",
                "修复浏览器返回键退出网站 (history.pushState重复写入)",
                "映射 Z 盘脚本修复: UNC 格式, 去掉 DavWWWRoot, 重启 WebClient",
                "WebDAV PROPFIND 支持 Depth:1, Windows 资源管理器可浏览文件列表",
                "OPTIONS 响应添加 DAV 头, 正确标识 WebDAV 服务",
                "剪贴板 textarea 支持多行文本, 拉取批处理脚本不再揉成一行",
                "网页端新增一键下载映射脚本按钮 (动态IP端口)",
            )),
            ChangelogEntry("v1.1.0", "2026-07", listOf(
                "动画全面优化：二维码弹性展开/收起、按钮震动反馈、卡片长按缩放回弹",
                "⚡ 性能大幅提升：非交互卡片跳过动画管线，减少60%组合开销",
                "审批逻辑加固：应用内弹窗与通知栏双向同步，点击任意一方另一方实时消失",
                "多用户并发审批：IP 队列管理，多人同时访问顺序审批不丢失",
                "深色模式回归：浅色/深色/跟随系统三态，持久化到 DataStore",
                "外观体验重构：aShellYou 风格 first/middle/last R 角衔接卡片",
                "分享窗口改进：启动/停止按钮状态同步 + 快捷改端口",
                "多处 UI 细节打磨：安全设置背景统一、居中对齐、预测性返回手势",
            )),
            ChangelogEntry("v1.0.0", "2026-06", listOf(
                "首个正式版本发布",
                "基于 Ktor Server 的 HTTP 文件共享服务",
                "可选的用户名/密码身份验证",
                "WebDAV 支持，可映射为 Windows 网络驱动器",
                "系统分享菜单集成：从任意应用分享文件",
                "二维码快速访问共享链接",
                "Material 3 + 动态取色支持",
                "自定义共享目录",
                "新设备连接确认机制",
            )),
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("更新内容", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            changelogs.forEachIndexed { i, entry ->
                item {
                    Text(
                        text = "${entry.version}  ${entry.date}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                itemsIndexed(entry.items) { j, item ->
                    CustomCard(
                        cornerRadius = when { entry.items.size == 1 -> 24.dp; j == 0 -> 24.dp; j == entry.items.lastIndex -> 24.dp; else -> 4.dp },
                        border = null,
                        clickable = false,
                        enableHaptic = false
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Circle, null,
                                Modifier.size(8.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                item,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (i < changelogs.lastIndex) {
                    item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                }
            }
        }
    }
}

class ChangelogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper { ChangelogScreen(onBack = { finish() }) }
        }
    }
}