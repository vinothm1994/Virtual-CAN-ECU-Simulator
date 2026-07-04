package com.vecu.config

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
        EcuProfile("HVAC", "config/hvac.dbc", "config/hvac.yml"),
        EcuProfile("Vehicle", "config/vehicle.dbc", "config/vehicle.yml"),
    )

    /** Index into [PROFILES] loaded at startup. */
    const val DEFAULT_PROFILE = 0

    /** Fallback CAN interface if the YAML does not specify one (Linux SocketCAN). */
    const val CAN_INTERFACE = "vcan0"

    /** Fallback PCAN bitrate on Windows if the YAML does not specify one. */
    const val CAN_BAUDRATE = "500K"

    /** Rule-engine / state tick period. Ramp rates in the YAML are per tick. */
    const val TICK_INTERVAL_MS = 100L

    /** How many CAN monitor / log rows to retain in the UI. */
    const val MAX_LOG_ROWS = 300

    /**
     * Candidate locations for the JNI bridge (dbcppp + SocketCAN). The first
     * that exists is loaded. `native/build` is where the Gradle `buildNative`
     * task drops it during development.
     */
    val NATIVE_LIB_CANDIDATES = listOf(
        "native/build/libvecunative.so",
        "../native/build/libvecunative.so",
        "libvecunative.so",
    )
}
