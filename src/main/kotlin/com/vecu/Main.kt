package com.vecu

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.vecu.ui.App
import com.vecu.ui.ErrorScreen
import com.vecu.viewmodel.SimulatorViewModel

/**
 * Entry point. Boots straight from [com.vecu.config.AppConfig]: load DBC + YAML,
 * build the property model + Virtual ECU, then show the window. No project setup.
 */
fun main() = application {
    val scope = rememberCoroutineScope()

    // Build the ViewModel once; surface any startup failure instead of crashing.
    val result = remember {
        runCatching { SimulatorViewModel(scope) }
    }
    val vm = result.getOrNull()

    // Tidy shutdown (stop ECU threads, close CAN, release DBC handle).
    if (vm != null) {
        DisposableViewModel(vm)
    }

    val windowState = rememberWindowState(width = 1440.dp, height = 900.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Virtual CAN ECU Simulator",
    ) {
        if (vm != null) {
            App(vm)
        } else {
            ErrorScreen(result.exceptionOrNull()?.message ?: "unknown error")
        }
    }
}

@androidx.compose.runtime.Composable
private fun DisposableViewModel(vm: SimulatorViewModel) {
    androidx.compose.runtime.DisposableEffect(vm) {
        onDispose { vm.shutdown() }
    }
}
