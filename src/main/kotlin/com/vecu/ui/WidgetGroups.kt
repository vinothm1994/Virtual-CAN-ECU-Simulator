package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vecu.core.config.GroupLayout
import com.vecu.core.config.GroupSpec
import com.vecu.core.property.Property

private val GroupBg = Color(0xFF1A212A)
private val GroupBorder = Color(0xFF262F3A)
private val KeyBg = Color(0xFF2C3742)
private val KeyLabel = Color(0xFF9FB0BC)

private val KEY_SIZE = 66.dp
private val KEY_GAP = 8.dp

/**
 * Glyph tokens a widget can name. Tokens rather than icon class names, so
 * config/ never has to know what an ImageVector is and an unknown token
 * degrades to the key's title instead of failing to compile.
 */
private val KeyIcons: Map<String, ImageVector> = mapOf(
    "arrow_up" to Icons.Filled.KeyboardArrowUp,
    "arrow_down" to Icons.Filled.KeyboardArrowDown,
    "arrow_left" to Icons.AutoMirrored.Filled.KeyboardArrowLeft,
    "arrow_right" to Icons.AutoMirrored.Filled.KeyboardArrowRight,
    "back" to Icons.AutoMirrored.Filled.Undo,
    "home" to Icons.Filled.Home,
    "menu" to Icons.Filled.Menu,
    "view" to Icons.Filled.Visibility,
    "info" to Icons.Filled.Info,
    "volume_up" to Icons.Filled.VolumeUp,
    "volume_down" to Icons.Filled.VolumeDown,
    "volume_mute" to Icons.AutoMirrored.Filled.VolumeOff,
    "previous" to Icons.Filled.SkipPrevious,
    "play_pause" to Icons.Filled.PlayArrow,
    "next" to Icons.Filled.SkipNext,
    "mic" to Icons.Filled.Mic,
    "call" to Icons.Filled.Call,
    "call_end" to Icons.Filled.CallEnd,
)

private fun accentColor(token: String?): Color? = when (token) {
    "ok" -> VecuColors.ok
    "warn" -> VecuColors.warn
    "error" -> VecuColors.error
    else -> null
}

/**
 * One declared [GroupSpec], drawn as a bordered block of keys.
 *
 * Groups size to their contents and are laid out by the caller in a flow, so
 * two of them sit side by side when the panel is wide enough and stack when it
 * is not — the arrangement a physical wheel has, as far as the width allows.
 */
@Composable
fun WidgetGroup(
    group: GroupSpec,
    properties: Map<String, Property>,
    onPress: (Property) -> Unit,
    onRelease: (Property) -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GroupBg)
            .border(1.dp, GroupBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        group.title?.let {
            Text(
                it.uppercase(),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6E7B86),
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
        }
        when (group.layout) {
            GroupLayout.DPAD -> DPad(group, properties, onPress, onRelease)
            GroupLayout.GRID -> Grid(group, properties, onPress, onRelease)
        }
    }
}

/** The cross: up on top, left/center/right in the middle, down below. The
 *  corners stay empty, which is what makes it read as a pad at a glance. */
@Composable
private fun DPad(
    group: GroupSpec,
    properties: Map<String, Property>,
    onPress: (Property) -> Unit,
    onRelease: (Property) -> Unit,
) {
    @Composable
    fun slot(name: String) {
        val p = group.slots[name]?.let { properties[it] }
        if (p == null) Spacer(Modifier.size(KEY_SIZE)) else Key(p, onPress, onRelease)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KEY_GAP),
    ) {
        slot("up")
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            slot("left")
            slot("center")
            slot("right")
        }
        slot("down")
    }
}

@Composable
private fun Grid(
    group: GroupSpec,
    properties: Map<String, Property>,
    onPress: (Property) -> Unit,
    onRelease: (Property) -> Unit,
) {
    val keys = group.members.mapNotNull { properties[it] }
    Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        keys.chunked(group.columns.coerceAtLeast(1)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                row.forEach { Key(it, onPress, onRelease) }
                // Pad a short last row so the block stays rectangular.
                repeat(group.columns - row.size) { Spacer(Modifier.size(KEY_SIZE)) }
            }
        }
    }
}

/**
 * A key face: glyph over label, or the title alone when no icon is named.
 *
 * Same gesture handling as the standalone momentary widget — both edges, and a
 * release even when the pointer is dragged off, so a consumer is never left
 * holding a key down.
 */
@Composable
private fun Key(property: Property, onPress: (Property) -> Unit, onRelease: (Property) -> Unit) {
    var down by remember { mutableStateOf(false) }
    val accent = accentColor(property.accent)
    val icon = property.icon?.let { KeyIcons[it] }
    val content = when {
        down -> Color(0xFF10161C)
        accent != null -> accent
        else -> Color(0xFFD4DAE0)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(KEY_SIZE, 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        down -> accent ?: VecuColors.rx
                        accent != null -> accent.copy(alpha = 0.12f)
                        else -> KeyBg
                    },
                )
                .pointerInput(property.id) {
                    detectTapGestures(
                        onPress = {
                            down = true
                            onPress(property)
                            tryAwaitRelease()
                            down = false
                            onRelease(property)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = property.title,
                    tint = content,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    property.title,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = content,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "[${property.title}]",
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = accent ?: KeyLabel,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(KEY_SIZE),
        )
    }
}
