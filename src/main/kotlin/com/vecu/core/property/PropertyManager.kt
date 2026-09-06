package com.vecu.core.property

import com.vecu.core.config.WidgetSpec
import com.vecu.dbc.DbcSchema
import com.vecu.dbc.SignalInfo

/**
 * Resolves YAML widget specs against DBC signal metadata to produce the
 * [Property] list the UI binds to. Ranges/units/enums come from the DBC unless
 * the YAML overrides them, so the UI stays truthful to the database.
 */
object PropertyManager {
    fun build(widgets: List<WidgetSpec>, schema: DbcSchema): List<Property> =
        widgets.map { spec -> resolve(spec, schema) }

    private fun resolve(spec: WidgetSpec, schema: DbcSchema): Property {
        // Prefer the request signal's metadata (that is what the control drives),
        // falling back to the feedback signal.
        val sig: SignalInfo? = spec.request?.let { schema.signalInfo[it] }
            ?: spec.feedback?.let { schema.signalInfo[it] }

        val min = spec.min ?: sig?.min ?: 0.0
        val max = spec.max ?: sig?.max ?: 1.0
        val step = spec.step ?: defaultStep(sig)
        val options = sig?.values.orEmpty().entries
            .sortedBy { it.key }
            .map { EnumOption(it.key.toDouble(), it.value) }

        return Property(
            id = spec.id,
            title = spec.title,
            widget = spec.widget,
            requestSignal = spec.request,
            feedbackSignal = spec.feedback,
            min = min,
            max = max,
            step = step,
            unit = sig?.unit.orEmpty(),
            options = options,
            snapZero = spec.snapZero,
            icon = spec.icon,
            accent = spec.accent,
            eventMessage = spec.event,
            eventSignals = spec.set.mapValues { (signal, written) ->
                resolveValue(signal, written, schema)
            },
            phaseSignal = spec.phase,
            phaseValues = spec.phase?.let { resolvePhases(it, schema) }.orEmpty(),
        )
    }

    /**
     * Turns a YAML `set:` entry into the number that goes on the wire. A plain
     * number is taken as written; anything else is looked up in the DBC VAL_
     * table of the signal it is being written to, so a profile never repeats a
     * code the DBC already owns and a typo fails here rather than putting a
     * wrong ButtonCode on the bus.
     */
    private fun resolveValue(signal: String, written: String, schema: DbcSchema): Double {
        written.toDoubleOrNull()?.let { return it }
        val info = schema.signalInfo[signal]
            ?: error("widget references unknown DBC signal '$signal'")
        val match = info.values.entries.firstOrNull { it.value.equals(written, ignoreCase = true) }
            ?: error(
                "'$written' is not a VAL_ label of signal '$signal' " +
                    "(known: ${info.values.values.sorted().joinToString(", ")})",
            )
        return match.key.toDouble()
    }

    /**
     * Resolves the four gesture phases against the phase signal's VAL_ table.
     * A profile whose DBC is missing one of them fails at load: a key that can
     * be pressed but never released would leave every consumer holding it down.
     */
    private fun resolvePhases(phaseSignal: String, schema: DbcSchema): Map<GesturePhase, Double> =
        GesturePhase.entries.associateWith { phase ->
            resolveValue(phaseSignal, phase.name, schema)
        }

    /** Integer-coded signals step by 1; scaled analog signals by their factor. */
    private fun defaultStep(sig: SignalInfo?): Double {
        if (sig == null) return 1.0
        return if (sig.factor != 0.0 && sig.factor < 1.0) sig.factor else 1.0
    }
}
