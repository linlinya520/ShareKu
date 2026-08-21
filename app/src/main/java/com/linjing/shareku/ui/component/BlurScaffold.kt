package com.linjing.shareku.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 带毛玻璃（backdrop blur）的 Scaffold 容器（移植自参考实现）。
 * 开启模糊且系统 >= Android 12 时，顶/底栏背景从全屏内容 GraphicsLayer 截取
 * 对应区域施加高斯模糊 + 半透明 surface 底色，形成毛玻璃效果。
 */
@Composable
fun BlurScaffold(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current.density
    var topH by remember { mutableStateOf(0) }
    var botH by remember { mutableStateOf(0) }
    var screenH by remember { mutableStateOf(0) }
    val topDp = (topH / density).dp
    val botDp = (botH / density).dp
    val canBlur = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val blurRadius = with(LocalDensity.current) { 18.dp.toPx() }
    val blurLayer = rememberGraphicsLayer()
    val surface = MiuixTheme.colorScheme.surface

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { screenH = it.size.height }
                .drawWithContent {
                    blurLayer.record { this@drawWithContent.drawContent() }
                    drawContent()
                }
        ) {
            content(PaddingValues(top = topDp, bottom = botDp))
        }

        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            if (canBlur) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Decal)
                        }
                        .drawWithContent {
                            drawContext.canvas.save()
                            drawContext.canvas.translate(0f, topH.toFloat())
                            drawLayer(blurLayer)
                            drawContext.canvas.restore()
                            val tint = Paint().apply { color = surface; alpha = 0.55f }
                            drawContext.canvas.drawRect(0f, 0f, size.width, size.height, tint)
                        }
                        .clipToBounds()
                        .height(topDp)
                )
            } else {
                Box(Modifier.fillMaxWidth().background(surface).height(topDp))
            }
            Box(Modifier.onGloballyPositioned { topH = it.size.height }) { topBar() }
        }

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (canBlur) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Decal)
                        }
                        .drawWithContent {
                            drawContext.canvas.save()
                            drawContext.canvas.translate(0f, -(screenH - botH).toFloat())
                            drawLayer(blurLayer)
                            drawContext.canvas.restore()
                            val tint = Paint().apply { color = surface; alpha = 0.55f }
                            drawContext.canvas.drawRect(0f, 0f, size.width, size.height, tint)
                        }
                        .clipToBounds()
                        .height(botDp)
                )
            } else {
                Box(Modifier.fillMaxWidth().background(surface).height(botDp))
            }
            Box(Modifier.onGloballyPositioned { botH = it.size.height }) { bottomBar() }
        }
    }
}