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
    /** No profile's DBC decodes this id — what the monitor's Errors tab shows. */
    val unknown: Boolean = false,
    /** Epoch ms of the frame. [time] is for reading; this is for arithmetic
     *  (rates and period drift on the Diagnostics screen). */
    val atMs: Long = 0L,
)

/** What one ECU says it will transmit — the contract Diagnostics measures the
 *  bus against. Mirrors the YAML's `tx:` entries. */
data class TxInfo(
    val message: String,
    val periodMs: Long?,
    val onChange: Boolean,
)

/**
 * Read-only description of a loaded ECU for the Settings and Diagnostics
 * screens. A snapshot on purpose: the screens get what they need without a
 * handle on the live EcuInstance.
 */
data class EcuInfo(
    val name: String,
    val dbc: String,
    val yaml: String,
    val messages: Int,
    val widgets: Int,
    /** Momentary (gesture) widgets. Non-zero means this ECU is event-shaped:
     *  it transmits when pressed, not on a period. */
    val eventWidgets: Int,
    val longPressMs: Long,
    val repeatMs: Long,
    val tx: List<TxInfo>,
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
    /** Number of ECUs loaded from PROFILES — all of them run at once. */
    val ecuCount: Int = 0,
    /** How many of those actually transmitted recently. Reads 0 while stopped,
     *  which is the point: "loaded" and "on the bus" are not the same claim. */
    val activeEcus: Int = 0,
    val driverName: String = "",
    val ecuName: String = "",
    val lastError: String? = null,
    /** Epoch ms of the last Start ECU; null while stopped. Drives the uptime clock. */
    val ecuStartedAt: Long? = null,
)
