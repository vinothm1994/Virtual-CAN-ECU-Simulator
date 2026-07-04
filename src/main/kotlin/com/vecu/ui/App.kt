package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vecu.viewmodel.SimulatorViewModel

private val Background = Color(0xFF101418)
private val PanelSurface = Color(0xFF161B21)
private val DividerColor = Color(0xFF0B0E11)

/** Root layout: toolbar, three panels (properties / dynamic UI / CAN monitor), log. */
@Composable
fun App(vm: SimulatorViewModel) {
    val status by vm.status.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()
    val properties by vm.properties.collectAsState()
    val values by vm.signalValues.collectAsState()
    val canLog by vm.canLog.collectAsState()
    val appLog by vm.appLog.collectAsState()

    VecuTheme {
        Column(Modifier.fillMaxSize().background(Background)) {
            Toolbar(
                status = status,
                profiles = vm.profiles.map { it.name },
                activeProfile = activeProfile.name,
                onSelectProfile = vm::selectProfile,
                onConnect = vm::connect,
                onDisconnect = vm::disconnect,
                onStart = vm::startEcu,
                onStop = vm::stopEcu,
                onClear = vm::clearLog,
            )

            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.width(250.dp).fillMaxHeight().background(PanelSurface)) {
                    PropertyPanel(properties, values)
                }
                VDivider()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    PanelHeader("Dynamic UI · ${status.ecuName} ECU")
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(230.dp),
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    ) {
                        items(properties, key = { it.id }) { p ->
                            DynamicWidget(p, values) { v -> vm.onWidgetChange(p, v) }
                        }
                    }
                }
                VDivider()
                Box(Modifier.width(400.dp).fillMaxHeight().background(PanelSurface)) {
                    CanMonitor(canLog)
                }
            }

            Box(Modifier.fillMaxWidth().height(2.dp).background(DividerColor))
            Box(Modifier.fillMaxWidth().height(150.dp).background(PanelSurface)) {
                LogPanel(appLog)
            }
        }
    }
}

@Composable
private fun VDivider() {
    Box(Modifier.width(2.dp).fillMaxHeight().background(DividerColor))
}

/** Shown when startup (DBC/YAML/native load) fails. */
@Composable
fun ErrorScreen(message: String) {
    VecuTheme {
        Box(Modifier.fillMaxSize().background(Background).padding(24.dp)) {
            androidx.compose.material3.Text(
                "Startup failed:\n\n$message",
                color = VecuColors.error,
            )
        }
    }
}
