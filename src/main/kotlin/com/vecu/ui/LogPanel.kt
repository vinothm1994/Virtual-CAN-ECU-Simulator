package com.vecu.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vecu.viewmodel.LogEntry

/** Bottom panel: application log (lifecycle, errors, property updates). */
@Composable
fun LogPanel(entries: List<LogEntry>, onCollapse: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Application Log", Icons.Filled.KeyboardArrowDown, onCollapse)
        val listState = rememberLazyListState()
        LaunchedEffect(entries.size) {
            if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp), state = listState) {
            items(entries, key = { it.seq }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(e.time, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF7A8792))
                    Spacer(Modifier.width(8.dp))
                    Text(e.level, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = levelColor(e.level), modifier = Modifier.width(52.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(e.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFC4CCD3))
                }
            }
        }
    }
}

private fun levelColor(level: String): Color = when (level) {
    "ERROR" -> VecuColors.error
    "WARN" -> VecuColors.warn
    "DEBUG" -> Color(0xFF7A8792)
    else -> VecuColors.ok
}
