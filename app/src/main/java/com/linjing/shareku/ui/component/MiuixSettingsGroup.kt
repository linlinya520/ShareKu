package com.linjing.shareku.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.theme.LocalUiStyle

/**
 * MIUI 分组卡片容器：MIUI 模式下整组组件共用一个背景（四角 R 角），
 * 组内项之间由调用方用 Divider 分隔；Material 模式无背景（保持各卡片自带背景）。
 */
@Composable
fun MiuixSettingsGroup(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalUiStyle.current == "miuix") {
        top.yukonga.miuix.kmp.basic.Card(
            modifier = modifier.fillMaxWidth(),
            onClick = null
        ) {
            Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), content = content)
        }
    } else {
        Column(modifier = modifier, content = content)
    }
}