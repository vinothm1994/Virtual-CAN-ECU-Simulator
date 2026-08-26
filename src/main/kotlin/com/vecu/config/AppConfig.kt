package com.vecu.config

import java.io.File

/**
 * Hardcoded MVP configuration. No file dialogs, no project management — the app
 * boots straight from these paths (see the master spec). Everything about *which*
 * ECU is simulated lives in the DBC + YAML, so a different ECU is just a
 * different pair of files here.
 */
object AppConfig {
    /**
     * The ECU profiles the app can simulate (each = a DBC + a YAML). Add an ECU
     * by adding a profile here — no code change. Selectable from the toolbar.
     */
    val PROFILES = listOf(
        EcuProfile("Vehicle", "config/vehicle.dbc", "config/vehicle.yml"),
        // One profile per ECU, so a vehicle is simulated by choosing which ECUs
        // are on the bus rather than by editing any one file:
        //   ICE  = Vehicle + Powertrain
        //   HEV  = Vehicle + Powertrain + Battery + Motor
        //   PHEV = Vehicle + Powertrain + Battery + Motor + Charging
        //   BEV  = Vehicle + Battery + Motor + Charging
        // Set PowertrainType in the Vehicle profile to match what you run.
        EcuProfile("Powertrain", "config/powertrain.dbc", "config/powertrain.yml"),
        EcuProfile("Battery", "config/battery.dbc", "config/battery.yml"),
        EcuProfile("Motor", "config/motor.dbc", "config/motor.yml"),
        EcuProfile("Charging", "config/charging.dbc", "config/charging.yml"),
        // Steering, Body and HVAC are on every vehicle whatever propels it, so
        // unlike the five above they are not part of the powertrain arithmetic.
        EcuProfile("Steering", "config/steering.dbc", "config/steering.yml"),
        EcuProfile("Body", "config/body.dbc", "config/body.yml"),
        EcuProfile("HVAC", "config/hvac.dbc", "config/hvac.yml"),
        // Steering-wheel controls. The one EVENT-shaped profile: its widgets
        // report gestures (press / long press / repeat / release) instead of
        // holding state, and its frame is sent per gesture step rather than
        // cyclically. See config/swc.yml.
        EcuProfile("SWC", "config/swc.dbc", "config/swc.yml"),
    )

    /** Index into [PROFILES] loaded at startup. */
    const val DEFAULT_PROFILE = 0

    /** Fallback CAN interface if the YAML does not specify one (Linux SocketCAN). */
    const val CAN_INTERFACE = "vcan0"

    /** Default TCP bus for the AAOS emulator bench: vcan_tcp_bridge inside the
     *  guest dials this port on the host's loopback (10.0.2.2 as seen from the
     *  guest). Needs no CAN hardware and no SocketCAN, so it is the one bus type
     *  that works identically on Linux and Windows. */
    const val CAN_TCP_BUS = "tcp:29536"

    /** Fallback PCAN bitrate on Windows if the YAML does not specify one. */
    const val CAN_BAUDRATE = "500K"

    /** Rule-engine / state tick period. Ramp rates in the YAML are per tick. */
    const val TICK_INTERVAL_MS = 100L

    /** How many CAN monitor / log rows to retain in the UI. */
    const val MAX_LOG_ROWS = 300 * 10000

    // Set by the compose plugin (both `run` and the packaged app) to the merged
    // appResources/ tree (see build.gradle.kts' stageAppResources task).
    private val resourcesDir: File? =
        System.getProperty("compose.application.resources.dir")?.let(::File)

    /**
     * Resolves a [PROFILES] path (e.g. "config/hvac.dbc") against the packaged
     * app's bundled resources when present, else returns it unchanged (the
     * dev-mode fallback, relative to the working directory `./gradlew run` uses).
     */
    fun resolvePath(path: String): String {
        val bundled = resourcesDir?.let { File(it, path) }
        return if (bundled != null && bundled.exists()) bundled.absolutePath else path
    }
}
