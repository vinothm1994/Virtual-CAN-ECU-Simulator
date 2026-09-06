package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vecu.viewmodel.LogEntry

/** Levels as they are written by the ViewModel's log(). */
private val LEVELS = listOf("DEBUG", "INFO", "WARN", "ERROR")

/**
 * The application log with room to read it. The bottom dock stays for tailing
 * while you work the widgets; this is where you go to look something up, so it
 * is the one that gets filtering and full width.
 */
class LogFilterState {
    /** null = every level. */
    var level by mutableStateOf<String?>(null)
    var query by mutableStateOf("")

    val narrowed: Boolean get() = level != null || query.isNotBlank()

    fun clear() {
        level = null
        query = ""
    }
}

@Composable
fun LogsScreen(entries: List<LogEntry>, filter: LogFilterState) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Application Log")

        val rows = remember(entries.size, entries.lastOrNull()?.seq, filter.level, filter.query) {
            val q = filter.query.trim()
            entries.filter { e ->
                (filter.level == null || e.level == filter.level) &&
                    (q.isEmpty() || e.text.contains(q, ignoreCase = true) || e.level.contains(q, ignoreCase = true))
            }
        }
        val counts = remember(entries.size, entries.lastOrNull()?.seq) {
            LEVELS.associateWith { lvl -> entries.count { it.level == lvl } }
        }

        FilterRow(filter, counts, rows.size, entries.size)

        val listState = rememberLazyListState()
        val atBottom by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                last == null || last.index >= info.totalItemsCount - 1
            }
        }
        LaunchedEffect(rows.size) {
            if (rows.isNotEmpty() && atBottom) listState.scrollToItem(rows.lastIndex)
        }

        if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Note(if (entries.isEmpty()) "Nothing logged yet." else "No entries match this filter.")
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), state = listState) {
            items(rows, key = { it.seq }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(e.time, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF7A8792))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        e.level,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = levelColor(e.level),
                        modifier = Modifier.width(56.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    // Wraps rather than truncating: there is width for it here,
                    // and a clipped error message is the one you needed to read.
                    Text(e.text, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFC4CCD3))
                }
            }
        }
    }
}

@Composable
private fun FilterRow(filter: LogFilterState, counts: Map<String, Int>, shown: Int, total: Int) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF12171D)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LevelChip("All", filter.level == null, total, Color(0xFF9FB0BC)) { filter.level = null }
                LEVELS.forEach { lvl ->
                    LevelChip(lvl, filter.level == lvl, counts[lvl] ?: 0, levelColor(lvl)) { filter.level = lvl }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .width(220.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF0E1318))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                if (filter.query.isEmpty()) {
                    Note("search log text")
                }
                BasicTextField(
                    value = filter.query,
                    onValueChange = { filter.query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFFD4DAE0), fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(VecuColors.rx),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (filter.narrowed) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear filter",
                    tint = Color(0xFF8FA0AE),
                    modifier = Modifier.size(15.dp).clickable { filter.clear() },
                )
                Spacer(Modifier.width(8.dp))
                Note("$shown of $total")
            }
        }
    }
}

@Composable
private fun LevelChip(label: String, selected: Boolean, count: Int, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color(0xFF1B222A))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else Color(0xFF8593A0),
            maxLines = 1,
            softWrap = false,
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                count.toString(),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = (if (selected) accent else Color(0xFF6E7B86)).copy(alpha = 0.75f),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
