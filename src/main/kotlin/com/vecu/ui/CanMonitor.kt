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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vecu.viewmodel.CanLogEntry
import com.vecu.viewmodel.Direction

/** Monitor tabs. ERRORS = frames no profile's DBC decodes (logged as "(unknown)"). */
enum class CanFilterMode(val label: String) { ALL("All"), TX("TX"), RX("RX"), ERRORS("Errors") }

/**
 * CAN monitor filter state, hoisted into `App`: the panel lives inside an
 * `AnimatedVisibility`, so collapsing it disposes this composable and a filter
 * kept here would silently reset itself every time the panel is hidden.
 */
class CanFilterState {
    var mode by mutableStateOf(CanFilterMode.ALL)
    var query by mutableStateOf("")

    /** null = every ECU. */
    var ecu by mutableStateOf<String?>(null)

    /** Whether the funnel's search row is open. */
    var searchOpen by mutableStateOf(false)

    /** True when something beyond the tab is hiding rows — worth signalling. */
    val narrowed: Boolean get() = query.isNotBlank() || ecu != null

    fun clear() {
        query = ""
        ecu = null
    }
}

/** Right panel: live RX/TX frames with decoded signals. */
@Composable
fun CanMonitor(entries: List<CanLogEntry>, filter: CanFilterState, onCollapse: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("CAN Monitor · RX / TX", Icons.Filled.ChevronRight, onCollapse)

        // Keyed on (size, last seq) rather than the list itself: the log is
        // append-only but capped, so once it is full two successive lists have
        // equal sizes and `remember`'s == would compare every row element-wise.
        val view = remember(entries.size, entries.lastOrNull()?.seq, filter.mode, filter.query, filter.ecu) {
            buildView(entries, filter.mode, filter.query, filter.ecu)
        }
        FilterBar(filter, view)

        val listState = rememberLazyListState()
        // Follow the bus only while already parked at the bottom, so scrolling
        // back to read a frame is not undone by the next one to arrive.
        val atBottom by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                last == null || last.index >= info.totalItemsCount - 1
            }
        }
        LaunchedEffect(view.rows.size) {
            if (view.rows.isNotEmpty() && atBottom) listState.scrollToItem(view.rows.lastIndex)
        }

        if (view.rows.isEmpty() && entries.isNotEmpty()) {
            // A filtered-empty list looks exactly like a dead bus otherwise.
            EmptyNote(filter)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), state = listState) {
            items(view.rows, key = { it.seq }) { e -> CanRow(e) }
        }
    }
}

/** Rows to draw plus everything the filter bar shows, from one pass over the log. */
private class MonitorView(
    val rows: List<CanLogEntry>,
    val all: Int,
    val tx: Int,
    val rx: Int,
    val errors: Int,
    val ecus: List<String>,
)

/**
 * Tab counts and the ECU list come out of the same loop as the visible rows:
 * the log can hold a lot of frames and is rebuilt on every frame received, so
 * this runs often enough that four separate passes would be four times the cost.
 * Counts reflect the search/ECU filter but not the tab — they say how many rows
 * each tab *would* show.
 */
private fun buildView(
    entries: List<CanLogEntry>,
    mode: CanFilterMode,
    query: String,
    ecu: String?,
): MonitorView {
    val q = query.trim()
    val rows = ArrayList<CanLogEntry>()
    val ecus = LinkedHashSet<String>()
    var all = 0
    var tx = 0
    var rx = 0
    var errors = 0
    for (e in entries) {
        e.ecu?.let { ecus += it }
        if (ecu != null && e.ecu != ecu) continue
        if (!e.matches(q)) continue
        all++
        if (e.direction == Direction.TX) tx++ else rx++
        if (e.unknown) errors++
        val inTab = when (mode) {
            CanFilterMode.ALL -> true
            CanFilterMode.TX -> e.direction == Direction.TX
            CanFilterMode.RX -> e.direction == Direction.RX
            CanFilterMode.ERRORS -> e.unknown
        }
        if (inTab) rows += e
    }
    return MonitorView(rows, all, tx, rx, errors, ecus.sorted())
}

/** One box matching id, message, ECU and signal names — the three things you
 *  actually have in hand when hunting a frame. */
private fun CanLogEntry.matches(q: String): Boolean =
    q.isEmpty() ||
        idHex.contains(q, ignoreCase = true) ||
        message.contains(q, ignoreCase = true) ||
        ecu?.contains(q, ignoreCase = true) == true ||
        decoded.any { it.first.contains(q, ignoreCase = true) }

@Composable
private fun FilterBar(filter: CanFilterState, view: MonitorView) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFF12171D))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The panel is narrow; let the tabs scroll rather than squeeze them.
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // All carries no number: it is always TX + RX, both of which are
                // right there, and the width it costs pushes Errors off the panel.
                ModeTab(filter, CanFilterMode.ALL, 0, VecuColors.rx)
                ModeTab(filter, CanFilterMode.TX, view.tx, VecuColors.tx)
                ModeTab(filter, CanFilterMode.RX, view.rx, VecuColors.rx)
                ModeTab(filter, CanFilterMode.ERRORS, view.errors, VecuColors.error)
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.clip(RoundedCornerShape(5.dp))
                    .background(if (filter.searchOpen) VecuColors.rx.copy(alpha = 0.18f) else Color(0xFF1B222A))
                    .clickable { filter.searchOpen = !filter.searchOpen }
                    .padding(4.dp),
            ) {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = "Filter frames",
                    tint = if (filter.narrowed) VecuColors.rx else Color(0xFF8FA0AE),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (filter.searchOpen) {
            Spacer(Modifier.height(5.dp))
            SearchRow(filter, view.ecus)
        }
    }
}

@Composable
private fun ModeTab(filter: CanFilterState, mode: CanFilterMode, count: Int, accent: Color) {
    val selected = filter.mode == mode
    Row(
        Modifier.clip(RoundedCornerShape(5.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color(0xFF1B222A))
            .clickable { filter.mode = mode }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            mode.label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else Color(0xFF8593A0),
            maxLines = 1,
            softWrap = false,
        )
        // A count tells an empty tab apart from a filtered-out one at a glance.
        if (count > 0) {
            Spacer(Modifier.width(3.dp))
            Text(
                count.toString(),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = (if (selected) accent else Color(0xFF6E7B86)).copy(alpha = 0.75f),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun SearchRow(filter: CanFilterState, ecus: List<String>) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF0E1318))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            if (filter.query.isEmpty()) {
                Text(
                    "id / signal",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF5C6773),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            BasicTextField(
                value = filter.query,
                onValueChange = { filter.query = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFFD4DAE0),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(VecuColors.rx),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        EcuPicker(filter.ecu, ecus) { filter.ecu = it }
        if (filter.narrowed) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear filter",
                tint = Color(0xFF8FA0AE),
                modifier = Modifier.size(14.dp).clickable { filter.clear() },
            )
        }
    }
}

/** ECU names come from the log, not the profile list: the entries carry the
 *  YAML `ecuName`, which need not match the toolbar's profile name. */
@Composable
private fun EcuPicker(selected: String?, ecus: List<String>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF1B222A))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                "${selected ?: "All ECUs"}  ▾",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (selected != null) VecuColors.rx else Color(0xFF8593A0),
                maxLines = 1,
                softWrap = false,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All ECUs", fontSize = 12.sp) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            ecus.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                    onClick = {
                        onSelect(name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyNote(filter: CanFilterState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "No frames match this filter.",
            fontSize = 11.sp,
            color = Color(0xFF6E7B86),
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            "Reset",
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            fontWeight = FontWeight.Bold,
            color = VecuColors.rx,
            modifier = Modifier.clickable {
                filter.clear()
                filter.mode = CanFilterMode.ALL
            },
        )
    }
}

@Composable
private fun CanRow(e: CanLogEntry) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionTag(e.direction)
            Spacer(Modifier.width(8.dp))
            Text(e.time, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF7A8792), maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            Text(
                e.idHex,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                // An undecoded id is the thing you are looking for in the All tab.
                color = if (e.unknown) VecuColors.error else Color(0xFFD4DAE0),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                e.message,
                fontSize = 12.sp,
                color = Color(0xFF9FB0BC),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            e.ecu?.let {
                Spacer(Modifier.width(6.dp))
                Text("· $it", fontSize = 11.sp, color = Color(0xFF6E7B86), maxLines = 1, softWrap = false)
            }
        }
        Text(
            e.dataHex,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFB6C0CA),
            modifier = Modifier.padding(start = 44.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (e.decoded.isNotEmpty()) {
            Text(
                e.decoded.joinToString("  ") { "${it.first}=${fmtSig(it.second)}" },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF6E7B86),
                modifier = Modifier.padding(start = 44.dp, top = 1.dp),
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DirectionTag(dir: Direction) {
    val color = if (dir == Direction.RX) VecuColors.rx else VecuColors.tx
    Text(
        dir.name,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = color,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
    )
}

private fun fmtSig(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
