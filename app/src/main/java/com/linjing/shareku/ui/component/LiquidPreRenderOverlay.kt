package com.linjing.shareku.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 首次启用液态玻璃时的「预渲染」引导遮罩。
 *
 * 显示期间底部界面照常渲染完成首绘（着色器编译/离屏层分配集中在这一刻），
 * 由调用方控制显示时长，结束后收走即可流畅使用。
 */
@Composable
fun LiquidPreRenderOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.93f))
            // 吞掉全部手势，防止误触
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(
                "正在预渲染液态玻璃效果…",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "首次启用需要几秒钟，完成后即可流畅使用，请勿退出应用",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}