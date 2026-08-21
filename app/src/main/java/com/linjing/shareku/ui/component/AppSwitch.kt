package com.linjing.shareku.ui.component

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import com.linjing.shareku.ui.theme.LocalUiStyle

/**
 * 界面风格感知的裸开关：MIUI 模式用 miuix Switch，Material 模式用 M3 Switch。
 * 用于 ListItem trailingContent 等需要裸开关的位置。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    if (LocalUiStyle.current == "miuix") {
        top.yukonga.miuix.kmp.basic.Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    } else {
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors()
        )
    }
}