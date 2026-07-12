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
        )
    }

    /** Integer-coded signals step by 1; scaled analog signals by their factor. */
    private fun defaultStep(sig: SignalInfo?): Double {
        if (sig == null) return 1.0
        return if (sig.factor != 0.0 && sig.factor < 1.0) sig.factor else 1.0
    }
}
