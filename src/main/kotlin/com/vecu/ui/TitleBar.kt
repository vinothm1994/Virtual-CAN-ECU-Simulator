package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

private val TitleBarBg = Color(0xFF1B2129)

/** Custom dark title bar, replacing the native OS one (which defaults to
 *  white/light on Windows and clashes with the app's dark theme). */
@Composable
fun FrameWindowScope.TitleBar(title: String, state: WindowState, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp).background(TitleBarBg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowDraggableArea(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxSize().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color(0xFFE2E6EA), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        TitleBarButton(Icons.Filled.HorizontalRule, "Minimize") { state.isMinimized = true }
        TitleBarButton(
            icon = if (state.placement == WindowPlacement.Maximized) Icons.Filled.FilterNone else Icons.Filled.CropSquare,
            label = "Maximize",
        ) {
            state.placement =
                if (state.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
        }
        TitleBarButton(Icons.Filled.Close, "Close", danger = true, onClick = onClose)
    }
}

@Composable
private fun TitleBarButton(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        hovered && danger -> Color(0xFFE81123)
        hovered -> Color(0xFF2A323C)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFFD4DAE0), modifier = Modifier.size(15.dp))
    }
}
