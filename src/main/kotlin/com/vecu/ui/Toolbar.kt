package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vecu.viewmodel.SimStatus

/** Top toolbar: Connect/Disconnect, Start/Stop ECU, Clear Log, live status. */
@Composable
fun Toolbar(
    status: SimStatus,
    profiles: List<String>,
    activeProfile: String,
    onSelectProfile: (String) -> Unit,
    interfaces: List<String>,
    canInterface: String,
    onSelectInterface: (String) -> Unit,
    baudrate: String,
    baudrates: List<String>,
    onSelectBaudrate: (String) -> Unit,
    bitrateEditable: Boolean,
    bitrateDisplay: String,
    busEditable: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(Color(0xFF1B2129))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Virtual CAN ECU",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color(0xFFE2E6EA),
        )
        Spacer(Modifier.width(4.dp))

        ProfileSelector(profiles, activeProfile, onSelectProfile)
        Spacer(Modifier.width(4.dp))

        // CAN bus selection (editable only while disconnected).
        DropdownField(canInterface, interfaces, onSelectInterface, busEditable)
        if (bitrateEditable) {
            // PCAN/Windows: the app sets the bitrate.
            DropdownField(baudrate, baudrates, onSelectBaudrate, busEditable)
        } else {
            // SocketCAN/Linux: bitrate is set by `ip link ... up` — show it read-only.
            BitrateChip(bitrateDisplay)
        }
        Spacer(Modifier.width(4.dp))

        if (status.connected) {
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        } else {
            Button(onClick = onConnect) { Text("Connect") }
        }

        if (status.ecuRunning) {
            OutlinedButton(onClick = onStop) { Text("Stop ECU") }
        } else {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = VecuColors.ok),
            ) { Text("Start ECU", color = Color(0xFF08240F)) }
        }

        OutlinedButton(onClick = onClear) { Text("Clear Log") }

        Spacer(Modifier.width(16.dp))

        StatusDot("CAN", status.connected, status.driverName)
        Spacer(Modifier.width(12.dp))
        StatusDot(
            "ECU",
            status.ecuRunning,
            if (status.ecuRunning) "running (${status.ecuCount})" else "stopped",
        )

        status.lastError?.let {
            Spacer(Modifier.width(12.dp))
            Text("⚠ $it", color = VecuColors.error, fontSize = 12.sp)
        }
    }
}

/** ECU profile chooser (HVAC ▾ / Vehicle …). */
@Composable
private fun ProfileSelector(profiles: List<String>, active: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$active  ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(name)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Compact dropdown for a single value (CAN interface / bitrate). Greyed when disabled. */
@Composable
private fun DropdownField(value: String, options: List<String>, onSelect: (String) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("$value  ▾", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
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

/** Read-only bitrate display (Linux SocketCAN — the OS-configured rate). */
@Composable
private fun BitrateChip(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF232B33))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = Color(0xFF9FB0BC),
    )
}

@Composable
private fun StatusDot(label: String, active: Boolean, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (active) VecuColors.ok else VecuColors.idle),
        )
        Text(
            "$label: $detail",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFB6C0CA),
        )
    }
}
