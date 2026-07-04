package com.linjing.shareku.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * An image that can be tapped to zoom to fullscreen.
 * Tap again on the backdrop to dismiss, or press back.
 */
@Composable
fun ZoomableImage(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    normalShape: RoundedCornerShape = RoundedCornerShape(0.dp),
    hint: String? = null
) {
    var zoomed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { zoomed = true },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().clip(normalShape),
            contentScale = contentScale
        )
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(hint, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        }
    }

    if (zoomed) {
        BackHandler { zoomed = false }
        Dialog(
            onDismissRequest = { zoomed = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .pointerInput(Unit) { detectTapGestures { zoomed = false } }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { zoomed = false },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f, matchHeightConstraintsFirst = false),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}