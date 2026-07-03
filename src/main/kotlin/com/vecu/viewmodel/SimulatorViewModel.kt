package com.vecu.viewmodel

import com.vecu.can.CanDriver
import com.vecu.can.CanFrame
import com.vecu.can.PcanDriver
import com.vecu.can.SocketCanDriver
import com.vecu.config.AppConfig
import com.vecu.core.config.SimConfig
import com.vecu.core.ecu.VirtualEcu
import com.vecu.core.property.Property
import com.vecu.core.property.PropertyManager
import com.vecu.core.rule.RuleEngine
import com.vecu.core.scheduler.TxScheduler
import com.vecu.dbc.DbcService
import com.vecu.dbc.DecodedMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/**
 * Application controller (MVVM). Loads the DBC + YAML, wires the Virtual ECU to
 * the CAN driver, and exposes immutable [StateFlow]s for Compose to observe.
 * Owns all lifecycle: connect/disconnect, start/stop ECU.
 */
class SimulatorViewModel(private val scope: CoroutineScope) {
    private val dbc = DbcService()
    private val ecu: VirtualEcu
    private val scheduler: TxScheduler
    private val driver: CanDriver
    private val config: SimConfig

    /** Messages configured to transmit on-change (non-cyclic). */
    private val onChangeMessages: List<String>
    /** Last transmitted content per on-change message, for change detection. */
    private val lastSentValues = HashMap<String, Map<String, Double>>()

    private val seq = AtomicLong(0)
    private val logLock = Any()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    // --- observable state ---
    private val _properties = MutableStateFlow<List<Property>>(emptyList())
    val properties: StateFlow<List<Property>> = _properties

    val signalValues: StateFlow<Map<String, Double>>

    private val _canLog = MutableStateFlow<List<CanLogEntry>>(emptyList())
    val canLog: StateFlow<List<CanLogEntry>> = _canLog

    private val _appLog = MutableStateFlow<List<LogEntry>>(emptyList())
    val appLog: StateFlow<List<LogEntry>> = _appLog

    private val _status = MutableStateFlow(SimStatus())
    val status: StateFlow<SimStatus> = _status

    private var tickJob: Job? = null

    init {
        dbc.load(AppConfig.DBC_FILE)
        log("INFO", "DBC loaded: ${AppConfig.DBC_FILE} (${dbc.schema.messages.size} messages)")

        config = SimConfig.load(AppConfig.YAML_FILE)
        log("INFO", "YAML loaded: ${AppConfig.YAML_FILE} — ECU '${config.ecuName}'")

        _properties.value = PropertyManager.build(config.widgets, dbc.schema)

        ecu = VirtualEcu(dbc.schema, RuleEngine(config.rules), config.defaults)
        signalValues = ecu.state.flow
        log("INFO", "Property model built: ${_properties.value.size} widgets")

        val iface = config.canInterface ?: AppConfig.CAN_INTERFACE
        driver = if (isWindows()) PcanDriver() else SocketCanDriver(iface)
        driver.setListener(::onFrameReceived)

        scheduler = TxScheduler(scope, config.tx, ::onScheduledTx)
        onChangeMessages = config.tx.filter { it.onChange }.map { it.message }

        _status.value = SimStatus(driverName = driver.name, ecuName = config.ecuName)
        log("INFO", "Ready. Driver: ${driver.name}. Press Connect, then Start ECU.")
    }

    // --- toolbar actions ---

    fun connect() {
        if (_status.value.connected) return
        try {
            driver.open()
            // Force a fresh baseline so every on-change message is (re)sent once
            // on the next tick after connecting.
            lastSentValues.clear()
            _status.value = _status.value.copy(connected = true, lastError = null)
            log("INFO", "CAN connected: ${driver.name}")
        } catch (e: Throwable) {
            _status.value = _status.value.copy(connected = false, lastError = e.message)
            log("ERROR", "Connect failed: ${e.message}")
        }
    }

    fun disconnect() {
        if (!_status.value.connected) return
        driver.close()
        _status.value = _status.value.copy(connected = false)
        log("INFO", "CAN disconnected")
    }

    fun startEcu() {
        if (_status.value.ecuRunning) return
        _status.value = _status.value.copy(ecuRunning = true)
        tickJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                ecu.tick()
                evaluateOnChange()
                delay(AppConfig.TICK_INTERVAL_MS)
            }
        }
        scheduler.start()
        log("INFO", "Virtual ECU started (tick ${AppConfig.TICK_INTERVAL_MS} ms)")
    }

    fun stopEcu() {
        if (!_status.value.ecuRunning) return
        _status.value = _status.value.copy(ecuRunning = false)
        tickJob?.cancel()
        tickJob = null
        scheduler.stop()
        log("INFO", "Virtual ECU stopped")
    }

    fun clearLog() {
        _canLog.value = emptyList()
        _appLog.value = emptyList()
    }

    /** A UI control moved: inject the request signal, as if the IVI sent it. */
    fun onWidgetChange(property: Property, value: Double) {
        val signal = property.requestSignal ?: return
        ecu.setSignal(signal, value)
        log("DEBUG", "Property '${property.id}' -> $signal = ${fmt(value)}")
    }

    fun shutdown() {
        stopEcu()
        disconnect()
        dbc.close()
    }

    // --- CAN paths ---

    private fun onFrameReceived(frame: CanFrame) {
        val decoded: DecodedMessage? = dbc.decode(frame)
        if (decoded != null) {
            ecu.onFrame(decoded)
            addCanRow(Direction.RX, frame, decoded.message.name, decoded.values)
        } else {
            addCanRow(Direction.RX, frame, "(unknown)", emptyMap())
        }
    }

    private fun onScheduledTx(message: String) {
        if (!driver.isOpen) return
        val values = ecu.buildTx(message)
        val frame = dbc.encode(message, values) ?: return
        driver.send(frame)
        lastSentValues[message] = values // keep on-change baseline in sync with cyclic sends
        addCanRow(Direction.TX, frame, message, values)
    }

    /**
     * Transmit each on-change message whose encoded content changed since it was
     * last sent. Called every tick, so a UI/rule-driven change reaches the bus
     * within one tick — the model for non-cyclic signals like gear selection.
     */
    private fun evaluateOnChange() {
        if (onChangeMessages.isEmpty() || !driver.isOpen) return
        for (message in onChangeMessages) {
            val values = ecu.buildTx(message)
            if (lastSentValues[message] == values) continue
            lastSentValues[message] = values
            val frame = dbc.encode(message, values) ?: continue
            driver.send(frame)
            addCanRow(Direction.TX, frame, message, values)
        }
    }

    // --- logging ---

    private fun addCanRow(dir: Direction, frame: CanFrame, message: String, values: Map<String, Double>) {
        val row = CanLogEntry(
            seq = seq.incrementAndGet(),
            time = LocalTime.now().format(timeFmt),
            direction = dir,
            idHex = frame.idHex(),
            message = message,
            dataHex = frame.hex(),
            decoded = values.entries.map { it.key to it.value },
        )
        // Cyclic scheduler, on-change tick loop and RX thread all write here.
        synchronized(logLock) { _canLog.value = (_canLog.value + row).takeLast(AppConfig.MAX_LOG_ROWS) }
    }

    private fun log(level: String, text: String) {
        val entry = LogEntry(seq.incrementAndGet(), LocalTime.now().format(timeFmt), level, text)
        synchronized(logLock) { _appLog.value = (_appLog.value + entry).takeLast(AppConfig.MAX_LOG_ROWS) }
    }

    private fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    companion object
}
