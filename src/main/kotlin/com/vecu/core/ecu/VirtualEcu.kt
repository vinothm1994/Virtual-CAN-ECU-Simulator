package com.vecu.core.ecu

import com.vecu.core.rule.RuleEngine
import com.vecu.dbc.DbcSchema
import com.vecu.dbc.DecodedMessage

/**
 * The Virtual ECU engine. Owns the signal [state] and drives the request ->
 * rules -> feedback loop:
 *
 *  - [onFrame] applies an incoming CAN request (from the IVI) to the state.
 *  - [setSignal] injects a request locally (from a UI control).
 *  - [tick] runs the rule engine to update feedback signals.
 *  - [buildTx] gathers a status message's current signal values for encoding.
 */
class VirtualEcu(
    private val schema: DbcSchema,
    private val ruleEngine: RuleEngine,
    defaults: Map<String, Double>,
) {
    val state = EcuState()

    init {
        // Seed every known signal to 0, then apply configured defaults so gauges
        // and setpoints start at sensible values rather than all-zero.
        val seed = HashMap<String, Double>()
        schema.signalInfo.keys.forEach { seed[it] = 0.0 }
        seed.putAll(defaults)
        state.replace(seed)
    }

    fun onFrame(decoded: DecodedMessage) = state.setAll(decoded.values)

    fun setSignal(signal: String, value: Double) = state.set(signal, value)

    fun tick() {
        val working = state.snapshot()
        ruleEngine.apply(working)
        state.replace(working)
    }

    /** Current values of every signal in [message], for [DbcService] encoding. */
    fun buildTx(message: String): Map<String, Double> {
        val info = schema.messageByName[message] ?: return emptyMap()
        val snap = state.snapshot()
        return info.signalNames.associateWith { snap[it] ?: 0.0 }
    }
}
