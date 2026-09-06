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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SensorDoor
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
import androidx.compose.ui.draw.alpha
import com.vecu.core.property.Property
import com.vecu.core.property.WidgetType

private val GroupBg = Color(0xFF1A212A)
private val GroupBorder = Color(0xFF262F3A)
private val KeyBg = Color(0xFF2C3742)
private val KeyLabel = Color(0xFF9FB0BC)

private val KEY_SIZE = 66.dp
private val KEY_GAP = 8.dp

/** Width one stacked control gets, so a group of them keeps a steady shape
 *  instead of stretching to whatever the flow hands it. */
private val STACK_COLUMN = 230.dp

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

    // --- climate keys ---
    "power" to Icons.Filled.PowerSettingsNew,
    "ac" to Icons.Filled.AcUnit,
    "auto" to Icons.Filled.AutoMode,
    "recirc" to Icons.Filled.Autorenew,
    "dual" to Icons.Filled.VerticalSplit,
    "max_ac" to Icons.Filled.Cyclone,
    // Material has no windscreen-defrost glyph; the key's own label carries the
    // meaning and this reads as heat on glass.
    "defrost" to Icons.Filled.Waves,

    // --- body keys ---
    "door" to Icons.Filled.SensorDoor,
    "hood" to Icons.Filled.CarRepair,
    "trunk" to Icons.Filled.Luggage,
    "lock" to Icons.Filled.Lock,
    "headlight" to Icons.Filled.Highlight,
    "lamp" to Icons.Filled.Lightbulb,
    "hazard" to Icons.Filled.Warning,
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
    values: Map<String, Double>,
    onPress: (Property) -> Unit,
    onRelease: (Property) -> Unit,
    onChange: (Property, Double) -> Unit,
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
            GroupLayout.DPAD -> DPad(group, properties, values, onPress, onRelease, onChange)
            GroupLayout.GRID -> Grid(group, properties, values, onPress, onRelease, onChange)
            GroupLayout.STACK -> Stack(group, properties, values, onPress, onRelease, onChange)
        }
    }
}

/** The cross: up on top, left/center/right in the middle, down below. The
 *  corners stay empty, which is what makes it read as a pad at a glance. */
@Composable
private fun DPad(
    group: GroupSpec,
    properties: Map<String, Property>,
    values: Map<String, Double>,
    onPress: (Property) -> Unit,
    onRelease: (Property) -> Unit,
    onChange: (Property, Double) -> Unit,
) {
    @Composable
    fun slot(name: String) {
        val p = group.slots[name]?.let { properties[it] }
        if (p == null) Spacer(Modifier.size(KEY_SIZE)) else Key(p, values, onPress, onRelease, onChange)
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

/**
 * Members as their ordinary widgets, laid out in [GroupSpec.columns] columns
 * inside the one card. [Grid] draws key faces, which only a momentary or
 * latching key has; a slider, a gauge or a setpoint needs its own control.
 */
@Composable
private fun Stack(
    group: GroupSpec,
    properties: Map<String, Property>,
    values: Map<String, Double>,
    onPress: (Property) -> Unit,
    onRelease: (Property) -> Unit,
    onChange: (Property, Double) -> Unit,
) {
    val members = group.members.mapNotNull { properties[it] }
    val columns = group.columns.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        members.chunked(columns).forEach { row ->
            Row(Modifier.width(STACK_COLUMN * columns)) {
                row.forEach { p ->
                    Box(Modifier.weight(1f)) {
                        DynamicWidget(
                            p,
                            values,
                            framed = false,
                            onPress = { onPress(p) },
                            onRelease = { onRelease(p) },
                        ) { v -> onChange(p, v) }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Grid(
    group: GroupSpec,
    properties: Map<String, Property>,
    values: Map<String, Double>,
    onPress: (Property) -> Unit,
    onRelease: (Property) -> Unit,
    onChange: (Property, Double) -> Unit,
) {
    val keys = group.members.mapNotNull { properties[it] }
    Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        keys.chunked(group.columns.coerceAtLeast(1)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                row.forEach { Key(it, values, onPress, onRelease, onChange) }
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
private fun Key(
    property: Property,
    values: Map<String, Double>,
    onPress: (Property) -> Unit,
    onRelease: (Property) -> Unit,
    onChange: (Property, Double) -> Unit,
) {
    // A momentary key is lit while held; a latching one is lit by its FEEDBACK
    // signal, so a key the ECU refused (gated off) stays dark however often it
    // is clicked. The panel shows the bus, not the click.
    val latching = property.widget == WidgetType.TOGGLE
    val on = latching && (property.feedbackSignal?.let { values[it] } ?: 0.0) >= 0.5
    // Requested but not applied: the ECU is stopped, or a gate is holding it
    // off. Shown as an outline, because "I asked" and "it happened" are
    // different facts and a panel that conflates them is lying about the bus.
    val requested = latching && (property.requestSignal?.let { values[it] } ?: 0.0) >= 0.5 && !on
    // Dimmed, not disabled: the gate says what the ECU is doing, and you are
    // still allowed to ask. Blocking input here would make the whole panel
    // inert until the ECU runs.
    val gated = property.isGated(values)
    var down by remember { mutableStateOf(false) }
    val lit = if (latching) on else down
    val accent = accentColor(property.accent)
    val icon = property.icon?.let { KeyIcons[it] }
    val content = when {
        lit -> Color(0xFF10161C)
        requested -> VecuColors.rx
        accent != null -> accent
        else -> Color(0xFFD4DAE0)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (gated) 0.4f else 1f),
    ) {
        Box(
            Modifier
                .size(KEY_SIZE, 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        lit -> accent ?: VecuColors.rx
                        accent != null -> accent.copy(alpha = 0.12f)
                        else -> KeyBg
                    },
                )
                .then(
                    if (requested) Modifier.border(1.dp, VecuColors.rx, RoundedCornerShape(8.dp)) else Modifier,
                )
                .then(
                    when {
                        // Keyed on `on` as well as the id: pointerInput caches
                        // its lambda per key, so keying only on the id would
                        // freeze the first composition's value and a lit key
                        // would keep writing 1 instead of toggling to 0.
                        latching -> Modifier.pointerInput(property.id, on) {
                            detectTapGestures(onTap = { onChange(property, if (on) 0.0 else 1.0) })
                        }
                        else -> Modifier.pointerInput(property.id) {
                            detectTapGestures(
                                onPress = {
                                    down = true
                                    onPress(property)
                                    tryAwaitRelease()
                                    down = false
                                    onRelease(property)
                                },
                            )
                        }
                    },
                ),
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
            property.title,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = accent ?: KeyLabel,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(KEY_SIZE),
        )
    }
}
