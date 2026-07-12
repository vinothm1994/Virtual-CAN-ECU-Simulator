package com.vecu.core.ecu

import com.vecu.can.CanFrame
import com.vecu.config.AppConfig
import com.vecu.config.EcuProfile
import com.vecu.core.config.SimConfig
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One simulated ECU running from a [profile] (its own DBC + YAML). Several of
 * these run concurrently on one shared bus: each decodes only the messages in
 * its own DBC and transmits only its own status. Sending is delegated to [onTx]
 * (the shared driver) and gated on [isBusOpen], so an instance never touches the
 * transport directly.
 */
class EcuInstance(
    val profile: EcuProfile,
    private val scope: CoroutineScope,
    private val isBusOpen: () -> Boolean,
    private val onTx: (EcuInstance, CanFrame, String, Map<String, Double>) -> Unit,
) {
    val dbc: DbcService = DbcService().apply { load(profile.dbc) }
    val config: SimConfig = SimConfig.load(profile.yaml)
    val properties: List<Property> = PropertyManager.build(config.widgets, dbc.schema)
    val name: String get() = config.ecuName
    val messageCount: Int get() = dbc.schema.messages.size

    private val ecu = VirtualEcu(dbc.schema, RuleEngine(config.rules), config.defaults)

    /** Live signal state for the UI to observe when this ECU is the active view. */
    val state get() = ecu.state.flow

    private val onChangeMessages = config.tx.filter { it.onChange }.map { it.message }
    private val lastSentValues = HashMap<String, Map<String, Double>>()
    private val scheduler = TxScheduler(scope, config.tx) { message -> transmit(message) }
    private var tickJob: Job? = null

    /** Applies an inbound frame if it belongs to this ECU's DBC; else null. */
    fun onFrame(frame: CanFrame): DecodedMessage? {
        val decoded = dbc.decode(frame) ?: return null
        ecu.onFrame(decoded)
        return decoded
    }

    /** Injects a request signal locally (from a UI control). */
    fun setSignal(signal: String, value: Double) = ecu.setSignal(signal, value)

    /** Starts the rule tick loop and the cyclic transmit scheduler. */
    fun start() {
        if (tickJob != null) return
        tickJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                ecu.tick()
                evaluateOnChange()
                delay(AppConfig.TICK_INTERVAL_MS)
            }
        }
        scheduler.start()
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        scheduler.stop()
    }

    /** Reset change-detection so every on-change message is (re)sent on connect. */
    fun clearTxBaseline() = lastSentValues.clear()

    fun close() {
        stop()
        dbc.close()
    }

    // Cyclic path (driven by the scheduler).
    private fun transmit(message: String) {
        if (!isBusOpen()) return
        val values = ecu.commitTx(message)
        val frame = dbc.encode(message, values) ?: return
        onTx(this, frame, message, values)
        // Snapshot *after* commitTx, so the next on-change check compares
        // like-for-like and doesn't mistake our own counter bump for a real change.
        lastSentValues[message] = ecu.buildTx(message)
    }

    // On-change path (evaluated every tick).
    private fun evaluateOnChange() {
        if (onChangeMessages.isEmpty() || !isBusOpen()) return
        for (message in onChangeMessages) {
            if (lastSentValues[message] == ecu.buildTx(message)) continue // peek, no side effects
            val values = ecu.commitTx(message)
            val frame = dbc.encode(message, values) ?: continue
            onTx(this, frame, message, values)
            lastSentValues[message] = ecu.buildTx(message)
        }
    }
}
