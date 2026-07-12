package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RailBg = Color(0xFF14181D)

/** Rotates text -90° and swaps its measured width/height so it reads bottom-to-top
 *  without clipping, matching Android Studio's vertical tool-window tab labels. */
private fun Modifier.verticalLabel(): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(Constraints(maxWidth = Constraints.Infinity))
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2),
            )
        }
    }
    .rotate(-90f)

/** Left/right edge tab that shows/hides its docked panel (Android Studio tool-window style). */
@Composable
fun VerticalRailTab(icon: ImageVector, label: String, checked: Boolean, onClick: () -> Unit) {
    val tint = if (checked) VecuColors.ok else Color(0xFF8FA0AE)
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(30.dp)
            .background(RailBg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp).verticalLabel(),
        )
    }
}

/** Bottom edge tab that shows/hides the Application Log (VS Code panel-toggle style). */
@Composable
fun HorizontalRailTab(icon: ImageVector, label: String, checked: Boolean, onClick: () -> Unit) {
    val tint = if (checked) VecuColors.ok else Color(0xFF8FA0AE)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(RailBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 11.sp, color = tint, modifier = Modifier.padding(start = 6.dp))
    }
}
