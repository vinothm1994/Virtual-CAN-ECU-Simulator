package com.vecu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vecu.core.property.Property
import com.vecu.core.property.WidgetType
import kotlin.math.roundToInt

/** Renders one [Property] as its widget, bound to live signal [values]. */
@Composable
fun DynamicWidget(
    property: Property,
    values: Map<String, Double>,
    onPress: () -> Unit = {},
    onRelease: () -> Unit = {},
    onChange: (Double) -> Unit,
) = DynamicWidget(property, values, framed = true, onPress, onRelease, onChange)

/** [framed] = false drops the card chrome: inside a group the group's own card
 *  is the container, and nesting a second one is just two borders. */
@Composable
fun DynamicWidget(
    property: Property,
    values: Map<String, Double>,
    framed: Boolean,
    onPress: () -> Unit = {},
    onRelease: () -> Unit = {},
    onChange: (Double) -> Unit,
) {
    val control = property.requestSignal?.let { values[it] }
        ?: property.feedbackSignal?.let { values[it] }
        ?: property.min
    val feedback = property.feedbackSignal?.let { values[it] } ?: control
    val gated = property.isGated(values)

    WidgetCard(property, feedback, gated, framed) {
        when (property.widget) {
            WidgetType.SWITCH -> SwitchWidget(control, onChange)
            WidgetType.SLIDER -> SliderWidget(property, control, onChange)
            WidgetType.TEMPERATURE -> TemperatureWidget(property, control, onChange)
            WidgetType.DROPDOWN -> DropdownWidget(property, control, onChange)
            WidgetType.GAUGE -> GaugeWidget(property, feedback)
            WidgetType.LABEL -> Text(
                valueText(property, feedback),
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            WidgetType.BUTTON -> OutlinedButton(onClick = { onChange(1.0) }) {
                Text(property.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            WidgetType.MOMENTARY -> MomentaryWidget(property, onPress, onRelease)
            WidgetType.TOGGLE -> ToggleWidget(property, feedback, gated, onChange)
            WidgetType.SEGMENTED -> SegmentedWidget(property, control, gated, onChange)
        }
    }
}

/**
 * True while the ECU is forcing this widget's output to 0 whatever the UI
 * says — its rule carries `gatedBy:` and that gate currently reads off. The
 * control is drawn dimmed and stops accepting input, because a switch that
 * reports ON while the bus carries OFF is the panel lying about the ECU.
 */
internal fun Property.isGated(values: Map<String, Double>): Boolean {
    val gate = gateSignal ?: return false
    return (values[gate] ?: 0.0) < 0.5
}

@Composable
private fun WidgetCard(
    property: Property,
    feedback: Double,
    gated: Boolean = false,
    framed: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dim = Modifier.alpha(if (gated) 0.45f else 1f)
    if (!framed) {
        // A Card supplies its content colour; a bare Column does not, and
        // Material3's LocalContentColor defaults to black — which on this dark
        // panel is invisible. Provide it here rather than colouring each Text,
        // so a widget kind added later cannot inherit the same bug.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(dim.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)) {
                WidgetHeader(property, feedback)
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(6.dp).then(dim),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E252D)),
    ) {
        Column(Modifier.padding(14.dp)) {
            WidgetHeader(property, feedback)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Title on the left, live value on the right. */
@Composable
private fun WidgetHeader(property: Property, feedback: Double) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Title takes the remaining width and ellipsises if too long, so
        // the value tag beside it always keeps its single-line space.
        Text(
            property.title,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // A gauge shows its value in the body already; a momentary key has no
        // value at all — it reports gestures, not state.
        if (property.widget != WidgetType.GAUGE &&
            property.widget != WidgetType.MOMENTARY
        ) {
            Text(
                feedbackTag(property, feedback),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * A key that reports a GESTURE, not a value: it fires on the way down and again
 * on the way up, with long-press and auto-repeat in between (see
 * [com.vecu.core.ecu.GestureDriver]).
 *
 * `detectTapGestures` rather than a Button's onClick, because a click is one
 * discrete callback and this needs both edges. `tryAwaitRelease` also returns
 * when the pointer is dragged off the key, so a finger slid away still produces
 * a RELEASED — a key that can be pressed but not released leaves every consumer
 * holding it down.
 */
/** The shared key face: a filled pad that is lit or not, with its title on it. */
@Composable
private fun KeyFace(
    property: Property,
    lit: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .alpha(if (enabled) 1f else 0.5f)
            .background(if (lit) MaterialTheme.colorScheme.primary else Color(0xFF2C3742))
            .then(
                // Keyed on `lit` too: pointerInput caches its lambda per key,
                // and onClick closes over the current state.
                if (enabled) Modifier.pointerInput(property.id, lit) {
                    detectTapGestures(onTap = { onClick() })
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (lit) "ON" else "OFF",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (lit) Color(0xFF10161C) else Color(0xFF8E9BA8),
            maxLines = 1,
        )
    }
}

@Composable
private fun MomentaryWidget(property: Property, onPress: () -> Unit, onRelease: () -> Unit) {
    var down by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (down) MaterialTheme.colorScheme.primary else Color(0xFF2C3742))
            .pointerInput(property.id) {
                detectTapGestures(
                    onPress = {
                        down = true
                        onPress()
                        tryAwaitRelease()
                        down = false
                        onRelease()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // No "press · hold" caption here any more: it repeated on every key,
        // and the panel says it once at the top instead. The fill colour is
        // the feedback — a held key is lit, and that is the whole state.
        Text(
            property.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (down) Color(0xFF10161C) else Color(0xFF8E9BA8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwitchWidget(control: Double, onChange: (Double) -> Unit) {
    val checked = control >= 0.5
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = checked, onCheckedChange = { onChange(if (it) 1.0 else 0.0) })
        Text(
            if (checked) "Requested ON" else "Requested OFF",
            fontSize = 12.sp,
            color = Color(0xFFB6C0CA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SliderWidget(property: Property, control: Double, onChange: (Double) -> Unit) {
    val steps = discreteSteps(property)
    Column {
        Text(fmt(control), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Slider(
            value = control.coerceIn(property.min, property.max).toFloat(),
            onValueChange = { raw ->
                var v = snap(raw.toDouble(), property)
                if (property.snapZero) {
                    val threshold = (property.max - property.min) * 0.025
                    if (kotlin.math.abs(v) <= threshold) v = 0.0
                }
                onChange(v)
            },
            valueRange = property.min.toFloat()..property.max.toFloat(),
            steps = steps,
        )
    }
}

@Composable
private fun TemperatureWidget(property: Property, control: Double, onChange: (Double) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "%.1f".format(control),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(" °C", fontSize = 14.sp, color = Color(0xFFB6C0CA), maxLines = 1, softWrap = false)
        }
        Slider(
            value = control.coerceIn(property.min, property.max).toFloat(),
            onValueChange = { onChange(snap(it.toDouble(), property)) },
            valueRange = property.min.toFloat()..property.max.toFloat(),
            steps = discreteSteps(property),
        )
    }
}

@Composable
private fun DropdownWidget(property: Property, control: Double, onChange: (Double) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = property.options.firstOrNull { it.value == control }?.label ?: fmt(control)
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            property.options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = {
                        onChange(opt.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun GaugeWidget(property: Property, feedback: Double) {
    val frac = ((feedback - property.min) / (property.max - property.min)).coerceIn(0.0, 1.0)
    val track = Color(0xFF2C3742)
    val fill = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.size(96.dp, 56.dp)) {
            val stroke = 10f
            val topLeft = Offset(stroke, stroke)
            val arcSize = Size(size.width - 2 * stroke, (size.height - 2 * stroke) * 2)
            drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(fill, 180f, (180f * frac).toFloat(), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column {
            Text(fmt(feedback), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (property.unit.isNotBlank()) {
                Text(property.unit, fontSize = 12.sp, color = Color(0xFFB6C0CA), maxLines = 1, softWrap = false)
            }
        }
    }
}

// --- helpers ---

private fun discreteSteps(p: Property): Int {
    if (p.step <= 0.0) return 0
    val n = ((p.max - p.min) / p.step).roundToInt() - 1
    return n.coerceAtLeast(0)
}

private fun snap(value: Double, p: Property): Double {
    if (p.step <= 0.0) return value
    val snapped = p.min + ((value - p.min) / p.step).roundToInt() * p.step
    return snapped.coerceIn(p.min, p.max)
}

private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

private fun valueText(p: Property, v: Double): String =
    if (p.unit.isBlank()) fmt(v) else "${fmt(v)} ${p.unit}"

/** The small live-state tag in the card header (feedback signal value). */
private fun feedbackTag(p: Property, v: Double): String = when (p.widget) {
    WidgetType.SWITCH -> if (v >= 0.5) "ON" else "OFF"
    WidgetType.DROPDOWN -> p.options.firstOrNull { it.value == v }?.label ?: fmt(v)
    WidgetType.TEMPERATURE -> "%.1f °C".format(v)
    else -> valueText(p, v)
}

/**
 * A latching key: the momentary face without the gesture. It lights from the
 * FEEDBACK signal, not the request, so a key the ECU refused (gated off, or
 * simply not applied yet) stays dark — the panel shows the bus, not the click.
 */
@Composable
private fun ToggleWidget(property: Property, feedback: Double, gated: Boolean, onChange: (Double) -> Unit) {
    val on = feedback >= 0.5
    KeyFace(
        property = property,
        lit = on,
        enabled = true,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onChange(if (on) 0.0 else 1.0) },
    )
}

/**
 * Every option on screen at once. Values come from the DBC's VAL_ table when
 * the signal has one, and from min/max/step when it does not — a fan speed of
 * 0..7 has no labels in the DBC, but it is still a row of discrete choices.
 */
@Composable
private fun SegmentedWidget(property: Property, control: Double, gated: Boolean, onChange: (Double) -> Unit) {
    val options = segmentsOf(property)
    // Six air directions in one row leaves ~30 px a label, which reads as
    // nothing at all. Wrap instead of shrinking.
    Column(
        Modifier.fillMaxWidth().alpha(if (gated) 0.6f else 1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
    // Fan speed's labels are one character; an air direction's are seven. Fit
    // fewer per row when they are wide rather than truncating them to nothing.
    val perRow = if (options.any { it.label.length > 5 }) 3 else SEGMENTS_PER_ROW
        options.chunked(perRow).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        row.forEach { opt ->
            val selected = kotlin.math.abs(control - opt.value) < 0.001
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color(0xFF2C3742))
                    // Dimmed, not disabled: see the note on the key face.
                    .pointerInput(property.id, opt.value) {
                        detectTapGestures(onTap = { onChange(opt.value) })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    opt.label,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color(0xFF10161C) else Color(0xFFB6C0CA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
        }
    }
}

/** Widest a segment row gets before it wraps. */
private const val SEGMENTS_PER_ROW = 4

/** DBC labels when the signal has them; the discrete steps otherwise. */
internal fun segmentsOf(property: Property): List<com.vecu.core.property.EnumOption> {
    if (property.options.isNotEmpty()) {
        // Labels are DBC-length ("FACE_AND_FEET"); a segment is a few chars wide.
        return property.options.map { it.copy(label = shortLabel(it.label)) }
    }
    val step = if (property.step > 0) property.step else 1.0
    val out = ArrayList<com.vecu.core.property.EnumOption>()
    var v = property.min
    while (v <= property.max + 1e-9 && out.size < 16) {
        out += com.vecu.core.property.EnumOption(v, fmt(v))
        v += step
    }
    return out
}

/**
 * Trims a DBC VAL_ label to something a segment can show. Labels are written
 * for the bus ("face_and_floor"), not for a 60 px button.
 */
private fun shortLabel(label: String): String {
    val parts = label.split('_').filter { it != "and" }
    if (parts.size == 1) return parts[0].take(5).uppercase()
    // A compound direction gets its first word and an initial for the rest:
    // face_and_floor -> FACE+FL. Spelling both out does not fit and truncating
    // evenly ("FACE+FLOO") loses the part that distinguishes it.
    return parts.first().take(4).uppercase() +
        parts.drop(1).joinToString("") { "+" + it.take(2).uppercase() }
}
