package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import com.vecu.viewmodel.SimStatus
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val HeaderBg = Color(0xFF0D141C)
private val HeaderTitle = Color(0xFFE9EDF1)
private val HeaderSubtitle = Color(0xFF7A8792)
private val HeaderRule = Color(0xFF212B36)

private val DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * The window's one header: identity, wall clock, bus/ECU state and the three
 * actions, with the window controls in the same band.
 *
 * This replaces the old title bar + toolbar pair. The bus pickers sit next to
 * the actions rather than across the band: choosing a bus and connecting to it
 * is one gesture, and they are disabled together once connected.
 */
@Composable
fun FrameWindowScope.AppHeader(
    status: SimStatus,
    state: WindowState,
    interfaces: List<String>,
    canInterface: String,
    onSelectInterface: (String) -> Unit,
    baudrate: String,
    baudrates: List<String>,
    onSelectBaudrate: (String) -> Unit,
    bitrateEditable: Boolean,
    bitrateDisplay: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) = VecuTheme {
    // The header is built in main(), outside App's theme scope, so it carries
    // the theme itself: without it Material falls back to its light scheme and
    // the dropdown menus render white on this dark band.
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(HeaderBg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowDraggableArea(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxSize().padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The identity takes the slack, so the readouts stay pinned to
                // the right and the title ellipsizes — rather than colliding —
                // when a long driver name pushes them left.
                Brand(Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                Clock()
                Spacer(Modifier.width(14.dp))
                Box(Modifier.width(1.dp).height(34.dp).background(HeaderRule))
                Spacer(Modifier.width(14.dp))
                StatusDot(
                    "CAN",
                    status.connected,
                    // The picker beside it already names the interface, so this
                    // says what the driver is doing, not what it is bound to.
                    if (status.connected) status.driverName.substringBefore(" (") else "disconnected",
                )
                Spacer(Modifier.width(16.dp))
                StatusDot(
                    "ECU",
                    status.ecuRunning,
                    if (status.ecuRunning) "running (${status.activeEcus}/${status.ecuCount})" else "stopped",
                )
                Spacer(Modifier.width(16.dp))
            }
        }

        // Window controls sit above the actions, as in the reference: the band
        // is one row, but its right end stacks chrome over commands.
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Bus selection is only meaningful while disconnected — the
                // driver is built from it at Connect and cannot be swapped
                // under a live socket.
                HeaderDropdown(canInterface, interfaces, onSelectInterface, enabled = !status.connected)
                Spacer(Modifier.width(6.dp))
                if (bitrateEditable) {
                    // PCAN/Windows: the app sets the bitrate.
                    HeaderDropdown(baudrate, baudrates, onSelectBaudrate, enabled = !status.connected)
                } else {
                    // SocketCAN: the OS owns it, so this is a readout.
                    BitrateReadout(bitrateDisplay)
                }
                Spacer(Modifier.width(10.dp))
                WindowButtons(state, onClose)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (status.connected) {
                    HeaderButton("Disconnect", Icons.Filled.LinkOff, onClick = onDisconnect)
                } else {
                    HeaderButton("Connect", Icons.Filled.Link, accent = VecuColors.rx, onClick = onConnect)
                }
                if (status.ecuRunning) {
                    HeaderButton("Stop ECU", Icons.Filled.Stop, accent = VecuColors.error, onClick = onStop)
                } else {
                    HeaderButton("Start ECU", Icons.Filled.PlayArrow, accent = VecuColors.ok, onClick = onStart)
                }
                HeaderButton("Clear Log", Icons.Filled.DeleteOutline, onClick = onClear)
            }
        }
    }
}

/** Same pill as [HeaderButton], but opening a menu instead of firing an action. */
@Composable
private fun HeaderDropdown(value: String, options: List<String>, onSelect: (String) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.height(26.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC4CCD3)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A434D)),
        ) {
            Text(
                "$value  ▾",
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
            )
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(opt, fontFamily = FontFamily.Monospace) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** The OS-configured SocketCAN bitrate: shown, never set from here. */
@Composable
private fun BitrateReadout(text: String) {
    Text(
        text.ifBlank { "—" },
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1B222A))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontFamily = FontFamily.Monospace,
        color = Color(0xFF9FB0BC),
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(VecuColors.rx.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Memory, contentDescription = null, tint = VecuColors.rx, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                "Virtual CAN ECU Simulator",
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                color = HeaderTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Automotive ECU Test Environment",
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = HeaderSubtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Wall clock. Ticks once a second — it is a bench clock for correlating a
 *  frame you just saw with a note you are writing, not a precision source. */
@Composable
private fun Clock() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = LocalDateTime.now()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(now.format(DATE_FMT), fontSize = 12.sp, lineHeight = 14.sp, color = Color(0xFFB6C0CA), maxLines = 1, softWrap = false)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(1.dp).height(12.dp).background(HeaderRule))
        Spacer(Modifier.width(10.dp))
        Text(
            now.format(TIME_FMT),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFD4DAE0),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun StatusDot(label: String, active: Boolean, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) VecuColors.ok else VecuColors.idle))
        Text(
            "$label : $detail",
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = if (active) Color(0xFFC4CCD3) else Color(0xFF8593A0),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Outlined pill with a leading icon; [accent] tints it for Connect/Start/Stop. */
@Composable
private fun HeaderButton(
    label: String,
    icon: ImageVector,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 7.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = accent ?: Color(0xFFC4CCD3),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            (accent ?: Color(0xFF3A434D)).copy(alpha = if (accent == null) 1f else 0.55f),
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}
