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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    onChange: (Double) -> Unit,
) {
    val control = property.requestSignal?.let { values[it] }
        ?: property.feedbackSignal?.let { values[it] }
        ?: property.min
    val feedback = property.feedbackSignal?.let { values[it] } ?: control

    WidgetCard(property, feedback) {
        when (property.widget) {
            WidgetType.SWITCH -> SwitchWidget(control, onChange)
            WidgetType.SLIDER -> SliderWidget(property, control, onChange)
            WidgetType.TEMPERATURE -> TemperatureWidget(property, control, onChange)
            WidgetType.DROPDOWN -> DropdownWidget(property, control, onChange)
            WidgetType.GAUGE -> GaugeWidget(property, feedback)
            WidgetType.LABEL -> Text(valueText(property, feedback), fontSize = 20.sp)
            WidgetType.BUTTON -> OutlinedButton(onClick = { onChange(1.0) }) { Text(property.title) }
        }
    }
}

@Composable
private fun WidgetCard(property: Property, feedback: Double, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E252D)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(property.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    feedbackTag(property, feedback),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SwitchWidget(control: Double, onChange: (Double) -> Unit) {
    val checked = control >= 0.5
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = checked, onCheckedChange = { onChange(if (it) 1.0 else 0.0) })
        Text(if (checked) "Requested ON" else "Requested OFF", fontSize = 12.sp, color = Color(0xFFB6C0CA))
    }
}

@Composable
private fun SliderWidget(property: Property, control: Double, onChange: (Double) -> Unit) {
    val steps = discreteSteps(property)
    Column {
        Text(fmt(control), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = control.coerceIn(property.min, property.max).toFloat(),
            onValueChange = { onChange(snap(it.toDouble(), property)) },
            valueRange = property.min.toFloat()..property.max.toFloat(),
            steps = steps,
        )
    }
}

@Composable
private fun TemperatureWidget(property: Property, control: Double, onChange: (Double) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.1f".format(control), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(" °C", fontSize = 14.sp, color = Color(0xFFB6C0CA))
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
        OutlinedButton(onClick = { expanded = true }) { Text(current) }
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
            Text(fmt(feedback), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (property.unit.isNotBlank()) {
                Text(property.unit, fontSize = 12.sp, color = Color(0xFFB6C0CA))
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
