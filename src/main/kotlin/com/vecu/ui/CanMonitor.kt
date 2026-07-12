package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vecu.viewmodel.CanLogEntry
import com.vecu.viewmodel.Direction

/** Right panel: live RX/TX frames with decoded signals. */
@Composable
fun CanMonitor(entries: List<CanLogEntry>, onCollapse: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("CAN Monitor · RX / TX", Icons.Filled.ChevronRight, onCollapse)
        val listState = rememberLazyListState()
        LaunchedEffect(entries.size) {
            if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), state = listState) {
            items(entries, key = { it.seq }) { e -> CanRow(e) }
        }
    }
}

@Composable
private fun CanRow(e: CanLogEntry) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            DirectionTag(e.direction)
            Spacer(Modifier.width(8.dp))
            Text(e.time, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF7A8792), maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            Text(
                e.idHex,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFD4DAE0),
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
