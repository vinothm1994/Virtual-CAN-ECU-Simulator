package com.vecu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vecu.core.property.Property
import com.vecu.core.property.WidgetType

/** Left panel: a compact list of every ECU property and its live feedback value. */
@Composable
fun PropertyPanel(properties: List<Property>, values: Map<String, Double>) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Virtual ECU · Properties")
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            items(properties, key = { it.id }) { p ->
                val v = p.displaySignal?.let { values[it] } ?: 0.0
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(p.title, fontSize = 13.sp, color = Color(0xFFD4DAE0))
                    Text(
                        display(p, v),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun display(p: Property, v: Double): String = when (p.widget) {
    WidgetType.SWITCH -> if (v >= 0.5) "ON" else "OFF"
    WidgetType.DROPDOWN -> p.options.firstOrNull { it.value == v }?.label ?: fmtVal(v)
    WidgetType.TEMPERATURE -> "%.1f°C".format(v)
    else -> if (p.unit.isBlank()) fmtVal(v) else "${fmtVal(v)} ${p.unit}"
}

private fun fmtVal(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
