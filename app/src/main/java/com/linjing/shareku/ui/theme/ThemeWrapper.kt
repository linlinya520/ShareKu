package com.linjing.shareku.ui.theme

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.linjing.shareku.AppSingletons
import com.linjing.shareku.ui.component.BackPreviewGate
import com.linjing.shareku.ui.theme.color.PaletteStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private data class InitialPrefs(
    val themeModeName: String,
    val dynamicColor: Boolean,
    val paletteOrdinal: Int,
    val uiStyle: String
)

/** 退出渐糊半径（px）：返回/退出时内容渐糊（窗口退出动画期间可见） */
private const val EXIT_BLUR_PX = 8f

@Composable
fun ShareThemeWrapper(content: @Composable () -> Unit) {
    val prefs = AppSingletons.preferencesManager
    // 首帧同步读取一次用户设置并缓存：此前用「collectAsState + 默认值」会先渲染一帧
    // 系统主题/默认样式，导致「先暗黑 → 再变白」的闪烁。读取只发生一次，开销可忽略。
    val initial = remember {
        runBlocking {
            InitialPrefs(
                themeModeName = prefs.themeMode.first(),
                dynamicColor = prefs.dynamicColor.first(),
                paletteOrdinal = prefs.paletteStyleOrdinal.first(),
                uiStyle = prefs.uiStyle.first()
            )
        }
    }
    val themeModeName by prefs.themeMode.collectAsState(initial = initial.themeModeName)
    val dynamicColor by prefs.dynamicColor.collectAsState(initial = initial.dynamicColor)
    val paletteOrdinal by prefs.paletteStyleOrdinal.collectAsState(initial = initial.paletteOrdinal)
    val paletteStyle = PaletteStyle.entries.getOrElse(paletteOrdinal) { PaletteStyle.TONAL_SPOT }
    val uiStyle by prefs.uiStyle.collectAsState(initial = initial.uiStyle)

    // ═══ 动画与交互（开关）═══
    val activity = LocalContext.current as? Activity
    // 页面转场动画：运行时切换窗口动画（0 = 无动画）——对下一次转场生效
    val pageAnim by prefs.pageTransitionAnim.collectAsState(initial = true)
    LaunchedEffect(pageAnim) {
        activity?.window?.setWindowAnimations(
            if (pageAnim) com.linjing.shareku.R.style.ShareKuWindowAnimation else 0
        )
    }
    // 预见式返回预览：关 = 禁掉系统跟手预览（更省电；返回仍以窗口动画完成）
    val pbPreview by prefs.predictiveBackPreview.collectAsState(initial = true)
    BackPreviewGate(previewEnabled = pbPreview, onBack = { activity?.finish() })

    // 返回过渡模糊（跨 Activity）：退出（onPause）时内容渐糊——窗口退出动画期间可见；
    // 回到前台（onResume）时快速恢复清晰。受「返回过渡模糊」开关控制。
    val backBlur by prefs.backTransitionBlur.collectAsState(initial = true)
    val exitBlur = remember { Animatable(0f) }
    val exitScope = rememberCoroutineScope()
    DisposableEffect(activity) {
        val owner = activity as? LifecycleOwner ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (backBlur) exitScope.launch { exitBlur.animateTo(EXIT_BLUR_PX, tween(220)) }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (exitBlur.value > 0.1f) exitScope.launch { exitBlur.animateTo(0f, tween(150)) }
                }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LocalShareTheme(
        themeMode = ThemeMode.fromName(themeModeName),
        dynamicColor = dynamicColor,
        paletteStyle = paletteStyle,
        uiStyle = uiStyle
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val r = exitBlur.value
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && r > 0.1f) {
                        RenderEffect
                            .createBlurEffect(r, r, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    } else null
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.ui.graphics.Color.Transparent
            ) {
                content()
            }
        }
    }
}