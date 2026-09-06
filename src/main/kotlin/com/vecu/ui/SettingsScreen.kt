package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vecu.config.AppConfig
import com.vecu.viewmodel.EcuInfo
import com.vecu.viewmodel.SimStatus

/**
 * Bus configuration and what was loaded at startup.
 *
 * Connect / Start ECU deliberately stay on the toolbar: you set the bus once
 * and act on it all day. Everything below the bus card is read-only — the DBC,
 * the YAML and the tick are what the app booted with, and pretending otherwise
 * would imply a reload this app does not do.
 */
@Composable
fun SettingsScreen(
    status: SimStatus,
    interfaces: List<String>,
    canInterface: String,
    onSelectInterface: (String) -> Unit,
    baudrate: String,
    baudrates: List<String>,
    onSelectBaudrate: (String) -> Unit,
    bitrateEditable: Boolean,
    bitrateDisplay: String,
    ecus: List<EcuInfo>,
) {
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Settings")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenCard("CAN BUS") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Interface", fontSize = 12.sp, lineHeight = 14.sp, color = ScreenLabel, modifier = Modifier.width(120.dp))
                    SettingDropdown(canInterface, interfaces, onSelectInterface, enabled = !status.connected)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bitrate", fontSize = 12.sp, lineHeight = 14.sp, color = ScreenLabel, modifier = Modifier.width(120.dp))
                    if (bitrateEditable) {
                        // PCAN/Windows: the app sets the bitrate.
                        SettingDropdown(baudrate, baudrates, onSelectBaudrate, enabled = !status.connected)
                    } else {
                        // SocketCAN: the OS owns it (ip link set ... type can bitrate N).
                        ReadOnlyValue(bitrateDisplay.ifBlank { "—" })
                        Spacer(Modifier.width(10.dp))
                        Note("set by the OS on this interface")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Note(
                    if (status.connected) {
                        "Connected — disconnect from the toolbar to change the bus."
                    } else {
                        "Not connected. Pick a bus, then Connect from the toolbar."
                    },
                    color = if (status.connected) VecuColors.warn else ScreenLabel,
                )
            }

            ScreenCard("TIMING") {
                KeyValueRow("Rule / TX tick", "${AppConfig.TICK_INTERVAL_MS} ms")
                val gesture = ecus.firstOrNull { it.eventWidgets > 0 }
                if (gesture != null) {
                    KeyValueRow("Gesture long press (${gesture.name})", "${gesture.longPressMs} ms")
                    KeyValueRow("Gesture repeat (${gesture.name})", "${gesture.repeatMs} ms")
                }
                Spacer(Modifier.height(6.dp))
                Note("Simulator timings, not a CAN contract — no consumer may infer a long press from them.")
            }

            ScreenCard("LOADED ECUS") {
                TableRow {
                    Cell("ECU", 1.2f, ScreenLabel, mono = false, bold = true)
                    Cell("DBC", 1.6f, ScreenLabel, mono = false, bold = true)
                    Cell("YAML", 1.6f, ScreenLabel, mono = false, bold = true)
                    Cell("MSGS", 0.5f, ScreenLabel, mono = false, bold = true)
                    Cell("WIDGETS", 0.7f, ScreenLabel, mono = false, bold = true)
                    Cell("TRANSMITS", 1.3f, ScreenLabel, mono = false, bold = true)
                }
                ScreenDivider()
                ecus.forEach { e ->
                    TableRow {
                        Cell(e.name, 1.2f, VecuColors.rx)
                        Cell(e.dbc.substringAfterLast('/'), 1.6f)
                        Cell(e.yaml.substringAfterLast('/'), 1.6f)
                        Cell(e.messages.toString(), 0.5f)
                        Cell(e.widgets.toString(), 0.7f)
                        Cell(txSummary(e), 1.3f, if (e.tx.isEmpty()) VecuColors.warn else ScreenValue)
                    }
                }
            }
        }
    }
}

/** What this ECU puts on the bus, in the terms the YAML uses. */
private fun txSummary(e: EcuInfo): String {
    if (e.tx.isEmpty()) return if (e.eventWidgets > 0) "on gesture" else "nothing"
    val cyclic = e.tx.count { it.periodMs != null }
    val onChange = e.tx.count { it.onChange }
    return buildList {
        if (cyclic > 0) add("$cyclic cyclic")
        if (onChange > 0) add("$onChange on-change")
    }.joinToString(", ")
}

@Composable
private fun SettingDropdown(value: String, options: List<String>, onSelect: (String) -> Unit, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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

@Composable
private fun ReadOnlyValue(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF232B33))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        fontSize = 12.sp,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace,
        color = Color(0xFF9FB0BC),
    )
}
