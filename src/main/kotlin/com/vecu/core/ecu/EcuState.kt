package com.vecu.core.ecu

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thread-safe store of every signal's current physical value — the single
 * source of truth shared by the CAN RX path, the rule engine, the TX scheduler,
 * and the UI. [flow] emits an immutable snapshot on every change so Compose can
 * observe it directly.
 */
class EcuState {
    private val lock = Any()
    private val values = HashMap<String, Double>()
    private val _flow = MutableStateFlow<Map<String, Double>>(emptyMap())
    val flow: StateFlow<Map<String, Double>> = _flow

    fun get(signal: String): Double = synchronized(lock) { values[signal] ?: 0.0 }

    fun set(signal: String, value: Double) {
        synchronized(lock) { values[signal] = value }
        publish()
    }

    fun setAll(pairs: Map<String, Double>) {
        synchronized(lock) { values.putAll(pairs) }
        publish()
    }

    /** Mutable copy for a rule-engine pass; write it back via [replace]. */
    fun snapshot(): MutableMap<String, Double> = synchronized(lock) { HashMap(values) }

    fun replace(newValues: Map<String, Double>) {
        synchronized(lock) {
            values.clear()
            values.putAll(newValues)
        }
        publish()
    }

    private fun publish() {
        _flow.value = synchronized(lock) { HashMap(values) }
    }
}
