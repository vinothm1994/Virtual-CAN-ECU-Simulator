package com.vecu.viewmodel

/** RX = received from the bus (e.g. IVI command); TX = transmitted by this ECU. */
enum class Direction { RX, TX }

/** One row in the CAN monitor. */
data class CanLogEntry(
    val seq: Long,
    val time: String,
    val direction: Direction,
    val idHex: String,
    val message: String,
    val dataHex: String,
    val decoded: List<Pair<String, Double>>,
    /** Owning ECU (which profile's DBC matched); null for unknown frames. */
    val ecu: String? = null,
)

/** One row in the application log. */
data class LogEntry(
    val seq: Long,
    val time: String,
    val level: String,
    val text: String,
)

/** Toolbar / status-bar state. */
data class SimStatus(
    val connected: Boolean = false,
    val ecuRunning: Boolean = false,
    /** Number of ECUs running concurrently on the shared bus. */
    val ecuCount: Int = 0,
    val driverName: String = "",
    val ecuName: String = "",
    val lastError: String? = null,
)
