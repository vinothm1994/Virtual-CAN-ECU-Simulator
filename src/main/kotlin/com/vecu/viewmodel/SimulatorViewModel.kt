package com.vecu.viewmodel

import com.vecu.can.CanDriver
import com.vecu.can.CanFrame
import com.vecu.can.Pcan
import com.vecu.can.PcanDriver
import com.vecu.can.SocketCanDriver
import com.vecu.config.AppConfig
import com.vecu.config.EcuProfile
import com.vecu.core.ecu.EcuInstance
import com.vecu.core.property.Property
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

/**
 * Application controller (MVVM). Runs **every** ECU profile concurrently on one
 * shared CAN bus (each an [EcuInstance]); the toolbar selects which one's UI is
 * shown — switching is a view change, nothing stops. The CAN monitor is global
 * (whole-bus). Exposes immutable [StateFlow]s for Compose to observe.
 */
class SimulatorViewModel(private val scope: CoroutineScope) {
    /** ECU profiles (HVAC, Vehicle, ...) — all run at once; one is viewed. */
    val profiles: List<EcuProfile> = AppConfig.PROFILES

    private val instances: List<EcuInstance>
    private lateinit var driver: CanDriver
    private lateinit var activeInstance: EcuInstance

    private val seq = AtomicLong(0)
    private val logLock = Any()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    // --- observable state ---
    private val _properties = MutableStateFlow<List<Property>>(emptyList())
    val properties: StateFlow<List<Property>> = _properties

    private val _signalValues = MutableStateFlow<Map<String, Double>>(emptyMap())
    val signalValues: StateFlow<Map<String, Double>> = _signalValues

    private val _canLog = MutableStateFlow<List<CanLogEntry>>(emptyList())
    val canLog: StateFlow<List<CanLogEntry>> = _canLog

    private val _appLog = MutableStateFlow<List<LogEntry>>(emptyList())
    val appLog: StateFlow<List<LogEntry>> = _appLog

    private val _status = MutableStateFlow(SimStatus())
    val status: StateFlow<SimStatus> = _status

    private val _activeProfile = MutableStateFlow(AppConfig.PROFILES[AppConfig.DEFAULT_PROFILE])
    val activeProfile: StateFlow<EcuProfile> = _activeProfile

    private var stateCollectJob: Job? = null

    init {
        // One instance per profile; they transmit via the shared driver (onInstanceTx),
        // gated on the bus being open.
        instances = profiles.map { EcuInstance(it, scope, { driver.isOpen }, ::onInstanceTx) }

        // A single shared bus for all ECUs — use the first profile's settings.
        val first = instances.first()
        val iface = first.config.canInterface ?: AppConfig.CAN_INTERFACE
        driver = if (isWindows()) {
            val chName = if (iface.uppercase().startsWith("PCAN_")) iface else "PCAN_USBBUS1"
            PcanDriver(
                channel = Pcan.channel(chName),
                baudrate = Pcan.baudrate(first.config.canBaudrate ?: AppConfig.CAN_BAUDRATE),
                channelName = chName,
            )
        } else {
            SocketCanDriver(iface)
        }
        driver.setListener(::onFrameReceived)

        activeInstance = instances[AppConfig.DEFAULT_PROFILE]
        showActive()
        _status.value = SimStatus(driverName = driver.name, ecuName = activeInstance.name, ecuCount = instances.size)
        log("INFO", "Loaded ${instances.size} ECUs on ${driver.name}:")
        instances.forEach {
            log("INFO", "  ${it.name}: ${it.profile.dbc} (${it.messageCount} messages), ${it.properties.size} widgets")
        }
        log("INFO", "Press Connect, then Start ECU.")
    }

    // --- view selection (non-destructive: all ECUs keep running) ---

    fun selectProfile(name: String) {
        if (name == activeInstance.profile.name) return
        val inst = instances.firstOrNull { it.profile.name == name } ?: return
        activeInstance = inst
        showActive()
        _status.value = _status.value.copy(ecuName = inst.name)
        log("INFO", "Viewing ${inst.name} ECU")
    }

    private fun showActive() {
        _properties.value = activeInstance.properties
        _activeProfile.value = activeInstance.profile
        stateCollectJob?.cancel()
        // collect() emits the current value immediately, so the view updates at once.
        stateCollectJob = scope.launch { activeInstance.state.collect { _signalValues.value = it } }
    }

    // --- toolbar actions (bus + all ECUs) ---

    fun connect() {
        if (_status.value.connected) return
        try {
            driver.open()
            instances.forEach { it.clearTxBaseline() } // resend on-change baselines after connect
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
        instances.forEach { it.start() }
        _status.value = _status.value.copy(ecuRunning = true)
        log("INFO", "Started ${instances.size} ECUs (tick ${AppConfig.TICK_INTERVAL_MS} ms)")
    }

    fun stopEcu() {
        if (!_status.value.ecuRunning) return
        instances.forEach { it.stop() }
        _status.value = _status.value.copy(ecuRunning = false)
        log("INFO", "Stopped all ECUs")
    }

    fun clearLog() {
        _canLog.value = emptyList()
        _appLog.value = emptyList()
    }

    /** A UI control moved on the active ECU: inject its request signal. */
    fun onWidgetChange(property: Property, value: Double) {
        val signal = property.requestSignal ?: return
        activeInstance.setSignal(signal, value)
        log("DEBUG", "[${activeInstance.name}] '${property.id}' -> $signal = ${fmt(value)}")
    }

    fun shutdown() {
        stopEcu()
        disconnect()
        stateCollectJob?.cancel()
        instances.forEach { it.close() }
    }

    // --- CAN paths (shared bus) ---

    private fun onFrameReceived(frame: CanFrame) {
        // Disjoint DBC id ranges, so at most one ECU claims a given frame.
        for (inst in instances) {
            val decoded = inst.onFrame(frame)
            if (decoded != null) {
                addCanRow(Direction.RX, frame, decoded.message.name, decoded.values, inst.name)
                return
            }
        }
        addCanRow(Direction.RX, frame, "(unknown)", emptyMap(), null)
    }

    private fun onInstanceTx(inst: EcuInstance, frame: CanFrame, message: String, values: Map<String, Double>) {
        driver.send(frame)
        addCanRow(Direction.TX, frame, message, values, inst.name)
    }

    // --- logging ---

    private fun addCanRow(
        dir: Direction,
        frame: CanFrame,
        message: String,
        values: Map<String, Double>,
        ecu: String?,
    ) {
        val row = CanLogEntry(
            seq = seq.incrementAndGet(),
            time = LocalTime.now().format(timeFmt),
            direction = dir,
            idHex = frame.idHex(),
            message = message,
            dataHex = frame.hex(),
            decoded = values.entries.map { it.key to it.value },
            ecu = ecu,
        )
        // Every ECU's TX, plus the RX thread, write here concurrently.
        synchronized(logLock) { _canLog.value = (_canLog.value + row).takeLast(AppConfig.MAX_LOG_ROWS) }
    }

    private fun log(level: String, text: String) {
        val entry = LogEntry(seq.incrementAndGet(), LocalTime.now().format(timeFmt), level, text)
        synchronized(logLock) { _appLog.value = (_appLog.value + entry).takeLast(AppConfig.MAX_LOG_ROWS) }
    }

    private fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")
}
