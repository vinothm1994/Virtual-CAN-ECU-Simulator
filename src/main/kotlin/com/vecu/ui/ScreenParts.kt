package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared furniture for the full-area screens (Diagnostics / Logs / Settings). */

internal val ScreenCardBg = Color(0xFF10161C)
private val ScreenCardBorder = Color(0xFF212A33)
internal val ScreenLabel = Color(0xFF7A8792)
internal val ScreenValue = Color(0xFFC4CCD3)

/** A titled card. Sections read as blocks rather than one long wall of rows. */
@Composable
internal fun ScreenCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ScreenCardBg)
            .border(1.dp, ScreenCardBorder, RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Text(
            title,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            color = ScreenLabel,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Label on the left, value on the right — the shape of every settings row. */
@Composable
internal fun KeyValueRow(label: String, value: String, valueColor: Color = ScreenValue) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, lineHeight = 14.sp, color = ScreenLabel, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One cell of a fixed-column table. [weight] 0 means size to content. */
@Composable
internal fun RowScope.Cell(
    text: String,
    weight: Float,
    color: Color = ScreenValue,
    mono: Boolean = true,
    bold: Boolean = false,
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal typealias RowScope = androidx.compose.foundation.layout.RowScope

@Composable
internal fun TableRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun ScreenDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp).height(1.dp).background(Color(0xFF1E262F)))
}

/** Empty-state / explanatory line inside a card. */
@Composable
internal fun Note(text: String, color: Color = ScreenLabel) {
    Text(text, fontSize = 11.sp, lineHeight = 15.sp, color = color)
}
