package com.vecu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vecu.viewmodel.SimulatorViewModel

private val Background = Color(0xFF101418)
private val PanelSurface = Color(0xFF161B21)
private val DividerColor = Color(0xFF0B0E11)
private val ErrorBannerBg = Color(0xFF3A1A1A)

/** Root layout: toolbar, three panels (properties / dynamic UI / CAN monitor), log. */
@Composable
fun App(vm: SimulatorViewModel) {
    val status by vm.status.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()
    val canInterface by vm.canInterface.collectAsState()
    val canBaudrate by vm.canBaudrate.collectAsState()
    val bitrateDisplay by vm.bitrateDisplay.collectAsState()
    val interfaces = remember { vm.availableInterfaces() }
    val properties by vm.properties.collectAsState()
    val values by vm.signalValues.collectAsState()
    val canLog by vm.canLog.collectAsState()
    val appLog by vm.appLog.collectAsState()

    var showProperties by remember { mutableStateOf(true) }
    var showCanMonitor by remember { mutableStateOf(true) }
    var showAppLog by remember { mutableStateOf(true) }

    VecuTheme {
        Column(Modifier.fillMaxSize().background(Background)) {
            Toolbar(
                status = status,
                profiles = vm.profiles.map { it.name },
                activeProfile = activeProfile.name,
                onSelectProfile = vm::selectProfile,
                interfaces = interfaces,
                canInterface = canInterface,
                onSelectInterface = vm::setInterface,
                baudrate = canBaudrate,
                baudrates = vm.baudrateOptions,
                onSelectBaudrate = vm::setBaudrate,
                bitrateEditable = vm.bitrateEditable,
                bitrateDisplay = bitrateDisplay,
                busEditable = !status.connected,
                onConnect = vm::connect,
                onDisconnect = vm::disconnect,
                onStart = vm::startEcu,
                onStop = vm::stopEcu,
                onClear = vm::clearLog,
            )

            status.lastError?.let { err ->
                Box(Modifier.fillMaxWidth().background(ErrorBannerBg).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("⚠ $err", color = VecuColors.error, fontSize = 12.sp)
                }
            }

            Row(Modifier.weight(1f).fillMaxWidth()) {
                VerticalRailTab(
                    icon = Icons.Filled.ViewList,
                    label = "Properties",
                    checked = showProperties,
                    onClick = { showProperties = !showProperties },
                )
                AnimatedVisibility(visible = showProperties, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                    Row {
                        Box(Modifier.width(250.dp).fillMaxHeight().background(PanelSurface)) {
                            PropertyPanel(properties, values, onCollapse = { showProperties = false })
                        }
                        VDivider()
                    }
                }
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
                AnimatedVisibility(visible = showCanMonitor, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                    Row {
                        VDivider()
                        Box(Modifier.width(400.dp).fillMaxHeight().background(PanelSurface)) {
                            CanMonitor(canLog, onCollapse = { showCanMonitor = false })
                        }
                    }
                }
                VerticalRailTab(
                    icon = Icons.Filled.SwapHoriz,
                    label = "CAN Monitor",
                    checked = showCanMonitor,
                    onClick = { showCanMonitor = !showCanMonitor },
                )
            }

            AnimatedVisibility(visible = showAppLog, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(DividerColor))
                    Box(Modifier.fillMaxWidth().height(150.dp).background(PanelSurface)) {
                        LogPanel(appLog, onCollapse = { showAppLog = false })
                    }
                }
            }
            HorizontalRailTab(
                icon = Icons.Filled.Article,
                label = "Application Log",
                checked = showAppLog,
                onClick = { showAppLog = !showAppLog },
            )
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
