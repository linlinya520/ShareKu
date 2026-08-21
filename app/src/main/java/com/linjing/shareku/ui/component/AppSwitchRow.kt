package com.linjing.shareku.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linjing.shareku.ui.theme.LocalUiStyle

/**
 * 界面风格感知的开关行：MIUI 模式用 miuix Switch，Material 模式用 M3 Switch。
 */
@Composable
fun AppSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (LocalUiStyle.current == "miuix") {
            top.yukonga.miuix.kmp.basic.Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onChange
            )
        } else {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors()
            )
        }
    }
}