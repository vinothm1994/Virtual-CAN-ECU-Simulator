package com.vecu.config

/**
 * Hardcoded MVP configuration. No file dialogs, no project management — the app
 * boots straight from these paths (see the master spec). Everything about *which*
 * ECU is simulated lives in the DBC + YAML, so a different ECU is just a
 * different pair of files here.
 */
object AppConfig {
    /** DBC database: messages, signals, bit layout, scaling, enums. */
    const val DBC_FILE = "config/hvac.dbc"

    /** YAML: UI widgets, request/feedback bindings, rules, periodic TX. */
    const val YAML_FILE = "config/hvac.yml"

    /** Fallback CAN interface if the YAML does not specify one. */
    const val CAN_INTERFACE = "vcan0"

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
