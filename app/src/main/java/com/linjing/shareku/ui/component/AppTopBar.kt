package com.linjing.shareku.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 统一顶栏包装（String title 便捷重载）。
 * 说明：miuix 0.9.x 的 TopAppBar 参数为内部图标名+隐藏颜色参数，与 M3 差异大，
 * 暂统一用 M3 实现保证稳定；后续 miuix API 稳定后再接 MIUI 顶栏。
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    AppTopBar(
        title = { Text(title) },
        navigationIcon = if (onBack != null) {
            {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            }
        } else {
            {}
        },
        actions = actions
    )
}

/** 统一顶栏包装（完整参数，兼容 M3 TopAppBar 调用结构） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    ),
    modifier: Modifier = Modifier
) {
    val liquid = isLiquidGlassActive()
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon ?: {},
        actions = actions,
        modifier = if (liquid) {
            modifier.liquidGlass(
                shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                surfaceAlpha = 0.10f,
                blurRadius = 6.dp,
                refractionHeight = 18.dp,
                refractionAmount = 34.dp
            )
        } else {
            modifier
        },
        colors = if (liquid) {
            colors.copy(containerColor = Color.Transparent)
        } else {
            colors
        }
    )
}