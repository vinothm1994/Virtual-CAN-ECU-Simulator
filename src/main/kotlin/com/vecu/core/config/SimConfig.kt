package com.vecu.core.config

import com.vecu.core.property.WidgetType
import org.yaml.snakeyaml.Yaml
import java.io.File

/** A UI widget declaration from YAML. Only signal names are referenced — never CAN ids. */
data class WidgetSpec(
    val id: String,
    val title: String,
    val widget: WidgetType,
    val request: String?,
    val feedback: String?,
    val min: Double?,
    val max: Double?,
    val step: Double?,
    val snapZero: Boolean = false,
)

/**
 * A simulation rule. [type] selects the behaviour:
 *  - `mirror`:  `to = from`, forced to 0 when [gatedBy] is off; skipped unless
 *    [onlyWhen] is on (when set).
 *  - `scale`:   `to = from * factor` (also honours [gatedBy]).
 *  - `ramp`:    `to` moves toward [toward] by [rate] per tick.
 *  - `counter`: `to` increments by [rate] (default 1) per tick and wraps back
 *    to 0 at [wrap] — a rolling alive counter (e.g. `wrap: 16` => 0..15).
 */
data class RuleSpec(
    val type: String,
    val from: String? = null,
    val to: String? = null,
    val toward: String? = null,
    val gatedBy: String? = null,
    val onlyWhen: String? = null,
    val rate: Double? = null,
    val factor: Double? = null,
    val wrap: Double? = null,
)

/**
 * A status message to transmit. Mirrors CAN transmission types:
 *  - [periodMs] non-null => **cyclic** (sent every N ms).
 *  - [onChange] true      => **on-change** (sent when its content changes,
 *    e.g. gear selection, indicators — not periodic).
 * Both may be set (cyclic with an immediate push on change).
 */
data class TxSpec(
    val message: String,
    val periodMs: Long?,
    val onChange: Boolean,
)

/** The whole YAML config: what to show, how the ECU behaves, what it transmits. */
data class SimConfig(
    val ecuName: String,
    val defaults: Map<String, Double>,
    val widgets: List<WidgetSpec>,
    val rules: List<RuleSpec>,
    val tx: List<TxSpec>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun load(path: String): SimConfig {
            val root = File(path).inputStream().use { Yaml().load<Map<String, Any?>>(it) }
                ?: error("empty YAML: $path")

            val ecu = root["ecu"] as? Map<String, Any?> ?: emptyMap()

            val defaults = (root["defaults"] as? Map<Any?, Any?> ?: emptyMap())
                .entries.associate { it.key.toString() to (it.value as Number).toDouble() }

            val widgets = (root["ui"] as? List<Map<String, Any?>> ?: emptyList()).map { w ->
                WidgetSpec(
                    id = w["id"].str(),
                    title = w["title"].str(w["id"].str()),
                    widget = WidgetType.from(w["widget"].str("label")),
                    request = w["request"] as? String,
                    feedback = w["feedback"] as? String,
                    min = w["min"].dbl(),
                    max = w["max"].dbl(),
                    step = w["step"].dbl(),
                    snapZero = w["snap_zero"] as? Boolean ?: false,
                )
            }

            val rules = (root["rules"] as? List<Map<String, Any?>> ?: emptyList()).map { r ->
                RuleSpec(
                    type = r["type"].str(),
                    from = r["from"] as? String,
                    to = r["to"] as? String,
                    toward = r["toward"] as? String,
                    gatedBy = r["gatedBy"] as? String,
                    onlyWhen = r["onlyWhen"] as? String,
                    rate = r["rate"].dbl(),
                    factor = r["factor"].dbl(),
                    wrap = r["wrap"].dbl(),
                )
            }

            val tx = (root["tx"] as? List<Map<String, Any?>> ?: emptyList()).map { t ->
                TxSpec(
                    message = t["message"].str(),
                    periodMs = (t["period_ms"] as? Number)?.toLong(),
                    onChange = t["on_change"] as? Boolean ?: false,
                )
            }

            return SimConfig(
                ecuName = ecu["name"].str("ECU"),
                defaults = defaults,
                widgets = widgets,
                rules = rules,
                tx = tx,
            )
        }

        private fun Any?.str(default: String = ""): String = this?.toString() ?: default
        private fun Any?.dbl(): Double? = (this as? Number)?.toDouble()
    }
}
