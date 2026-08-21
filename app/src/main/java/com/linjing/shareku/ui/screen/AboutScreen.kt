package com.linjing.shareku.ui.screen

import com.linjing.shareku.ui.component.AppTopBar
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linjing.shareku.R
import com.linjing.shareku.ui.component.ZoomableImage
import com.linjing.shareku.ui.theme.ShareThemeWrapper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onChangelog: () -> Unit = {}) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("关于", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── App header ──
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // App icon — tap to zoom
                    ZoomableImage(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = "ShareKu 图标",
                        modifier = Modifier.size(96.dp),
                        contentScale = ContentScale.Fit,
                        normalShape = RoundedCornerShape(20.dp)
                    )
                    Text(
                        text = "ShareKu",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // ── Chips ──
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
AboutChip(R.drawable.ic_github_chip, "GitHub", "开源仓库",
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/linlinya520/ShareKu")))
                        }
                    AboutChip(R.drawable.ic_version_chip, "v1.2.0", "当前版本",
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer) { onChangelog() }
                    AboutChip(R.drawable.ic_license_chip, "GPL-3.0", "开源协议",
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer) {}
                }
            }

            // ── Developer ──
            item {
                Spacer(Modifier.height(20.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("开发者", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth())
                        // Tap to zoom avatar
                        ZoomableImage(
                            painter = painterResource(R.drawable.ic_author),
                            contentDescription = "作者头像",
                            modifier = Modifier.size(72.dp),
                            normalShape = RoundedCornerShape(20.dp)
                        )
                        Text("Lin Jing", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("QQ: 3470176230", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Donation ──
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("捐赠支持", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth())
                        ZoomableImage(
                            painter = painterResource(R.drawable.ic_donate_qr),
                            contentDescription = "微信捐赠二维码",
                            modifier = Modifier.size(160.dp),
                            contentScale = ContentScale.Fit,
                            normalShape = RoundedCornerShape(12.dp),
                            hint = "点击放大"
                        )
                        Text("微信扫码支持开发者 · 点击放大", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Credits ──
            item {
                Spacer(Modifier.height(12.dp))
                Text("致谢", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                Spacer(Modifier.height(4.dp))
                Text("界面设计参考以下优秀开源项目：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                // 致谢开发者 CINXZ（miuix 组件参考）
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.miuix_author),
                        contentDescription = "CINXZ",
                        modifier = Modifier.size(48.dp).clip(CircleShape).clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://b23.tv/GkVlxo5")))
                        },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://b23.tv/GkVlxo5")))
                        }) { Text("CINXZ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                        Text("miuix 组件参考",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/holzschu/a-shell")))
                    }) { Text("aShell", color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iamr0s/InstallerX")))
                    }) { Text("InstallerX", color = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AboutChip(
    @androidx.annotation.DrawableRes iconRes: Int,
    title: String, description: String,
    containerColor: Color, contentColor: Color,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f), label = "chip"
    )
    Card(
        modifier = Modifier.scale(scale).clickable(interactionSource, null) {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey); onClick()
        },
        shape = RoundedCornerShape(50),
        colors = CardDefaults.cardColors(containerColor, contentColor)
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(iconRes), null, tint = contentColor, modifier = Modifier.size(22.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = contentColor)
                Text(description, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
            }
        }
    }
}

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShareThemeWrapper {
                AboutScreen(
                    onBack = { finish() },
                    onChangelog = { startActivity(Intent(this@AboutActivity, ChangelogActivity::class.java)) }
                )
            }
        }
    }
}
