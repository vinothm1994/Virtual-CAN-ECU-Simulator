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

    /** Glyph token for the button face (see the table in ui/DynamicUi.kt).
     *  Unset or unrecognised falls back to the title text. */
    val icon: String? = null,
    /** Colour role for the face: "ok" | "warn" | "error". Unset = neutral. */
    val accent: String? = null,

    // --- momentary (event) widgets only ---
    /** DBC message fired once per gesture step. */
    val event: String? = null,
    /** Signal carrying the gesture phase (e.g. ButtonAction). */
    val phase: String? = null,
    /** Signals identifying this key, as written in the YAML: either a number or
     *  a VAL_ label from the DBC (resolved by [com.vecu.core.property.PropertyManager]). */
    val set: Map<String, String> = emptyMap(),
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

/**
 * Gesture timing for `momentary` widgets: how long a key must be held before it
 * becomes a long press, and how fast it then auto-repeats.
 *
 * SIMULATOR settings, not part of the CAN contract. A real switch module has its
 * own thresholds in firmware, and no consumer may infer a long press from
 * timing anyway — that is what the LONG_PRESSED action exists for.
 */
data class GestureSpec(
    val longPressMs: Long = 600,
    val repeatMs: Long = 150,
)

/** How a [GroupSpec] arranges its members. */
enum class GroupLayout {
    /** Members flow left to right at a fixed column count. */
    GRID,

    /** The five positional slots of a directional pad: up / left / center /
     *  right / down, placed in a 3x3 with empty corners. */
    DPAD,
    ;

    companion object {
        fun from(name: String): GroupLayout =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: GRID
    }
}

/**
 * A visual grouping of widgets, drawn as one bordered block.
 *
 * The flat `ui:` list is reading order; a group is a shape. A D-pad is not five
 * buttons in a row — it is a cross, and recognising it at a glance is the whole
 * point of a steering-wheel panel. Widgets not named by any group keep flowing
 * in the adaptive grid exactly as before, so this is additive: a profile that
 * declares no groups renders as it always did.
 */
data class GroupSpec(
    val id: String,
    val title: String?,
    val layout: GroupLayout,
    /** GRID only: how many keys per row. */
    val columns: Int,
    /** GRID: members in order. DPAD: empty (see [slots]). */
    val members: List<String>,
    /** DPAD: slot name ("up", "left", "center", "right", "down") -> widget id. */
    val slots: Map<String, String>,
) {
    /** Every widget id this group claims, whatever the layout. */
    val widgetIds: List<String> get() = if (layout == GroupLayout.DPAD) slots.values.toList() else members
}

/** The whole YAML config: what to show, how the ECU behaves, what it transmits. */
data class SimConfig(
    val ecuName: String,
    val defaults: Map<String, Double>,
    val widgets: List<WidgetSpec>,
    val groups: List<GroupSpec>,
    val rules: List<RuleSpec>,
    val tx: List<TxSpec>,
    val gesture: GestureSpec = GestureSpec(),
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
                    icon = w["icon"] as? String,
                    accent = w["accent"] as? String,
                    event = w["event"] as? String,
                    phase = w["phase"] as? String,
                    // Left as written: a VAL_ label needs the DBC to resolve,
                    // which the config loader deliberately does not have.
                    set = (w["set"] as? Map<Any?, Any?> ?: emptyMap())
                        .entries.associate { it.key.toString() to it.value.toString() },
                )
            }

            val groups = (root["groups"] as? List<Map<String, Any?>> ?: emptyList()).map { g ->
                val layout = GroupLayout.from(g["layout"].str("grid"))
                GroupSpec(
                    id = g["id"].str(),
                    title = g["title"] as? String,
                    layout = layout,
                    columns = (g["columns"] as? Number)?.toInt() ?: 3,
                    members = when (layout) {
                        GroupLayout.GRID -> (g["members"] as? List<*>).orEmpty().map { it.toString() }
                        GroupLayout.DPAD -> emptyList()
                    },
                    slots = when (layout) {
                        GroupLayout.DPAD -> (g["members"] as? Map<Any?, Any?> ?: emptyMap())
                            .entries.associate { it.key.toString() to it.value.toString() }
                        GroupLayout.GRID -> emptyMap()
                    },
                )
            }
            // A group naming a widget that does not exist would silently drop
            // the key from the panel, which is the same class of mistake a
            // mistyped VAL_ label makes — so it fails at load, like that one.
            val widgetIds = widgets.map { it.id }.toSet()
            val claimed = HashSet<String>()
            groups.forEach { group ->
                group.widgetIds.forEach { id ->
                    require(id in widgetIds) { "group '${group.id}' names unknown widget '$id' in $path" }
                    require(claimed.add(id)) { "widget '$id' is in more than one group in $path" }
                }
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

            val gestureNode = root["gesture"] as? Map<Any?, Any?> ?: emptyMap()
            val gesture = GestureSpec(
                longPressMs = (gestureNode["long_press_ms"] as? Number)?.toLong() ?: 600,
                repeatMs = (gestureNode["repeat_ms"] as? Number)?.toLong() ?: 150,
            )

            return SimConfig(
                ecuName = ecu["name"].str("ECU"),
                defaults = defaults,
                widgets = widgets,
                groups = groups,
                rules = rules,
                tx = tx,
                gesture = gesture,
            )
        }

        private fun Any?.str(default: String = ""): String = this?.toString() ?: default
        private fun Any?.dbl(): Double? = (this as? Number)?.toDouble()
    }
}
