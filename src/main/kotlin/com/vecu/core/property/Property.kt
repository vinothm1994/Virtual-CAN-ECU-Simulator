package com.vecu.core.property

/** Widget kinds the dynamic UI can render. */
enum class WidgetType {
    SWITCH,
    SLIDER,
    TEMPERATURE,
    DROPDOWN,
    GAUGE,
    LABEL,
    BUTTON,

    /**
     * A latching on/off key: the same face as [MOMENTARY], but it holds its
     * state instead of reporting a gesture, and lights from its feedback
     * signal. A climate panel's A/C or RECIRC key, rather than a settings
     * screen's switch — which is what [SWITCH] renders.
     */
    TOGGLE,

    /**
     * A row of mutually exclusive options, all visible at once. Options come
     * from the DBC's VAL_ table when the signal has one (air distribution),
     * or from min/max/step when it does not (fan speed 0..7). A dropdown hides
     * the choices behind a click; on a control panel they are the point.
     */
    SEGMENTED,

    /**
     * A momentary key: it reports a GESTURE rather than holding a value.
     * Pressing it fires one event frame immediately, holding it adds
     * LONG_PRESSED and then REPEAT, and letting go fires RELEASED. Used by the
     * SWC (steering-wheel controls) profile, and by any future ECU whose signals
     * are things that HAPPEN rather than things that ARE.
     *
     * Unlike BUTTON, which just writes 1.0 to a request signal, this one does
     * not go through the tick/on-change path at all — a click shorter than one
     * 100 ms tick would otherwise be seen only as its release.
     */
    MOMENTARY,
    ;

    companion object {
        fun from(name: String): WidgetType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: LABEL
    }
}

/**
 * The four steps of a momentary key's gesture, in the order they occur. The
 * names match the DBC VAL_ labels of the phase signal (ButtonAction on SWC), so
 * a profile never repeats a number the DBC already owns.
 *
 * LONG_PRESSED exists so a consumer never has to infer a long press from
 * timing — and so a long press does not also fire the short-click action, which
 * is the rule every SWC implementation gets wrong at least once.
 */
enum class GesturePhase { PRESSED, LONG_PRESSED, REPEAT, RELEASED }

/** One selectable value for a dropdown, from the DBC's VAL_ descriptions. */
data class EnumOption(val value: Double, val label: String)

/**
 * A UI-facing control, resolved from a YAML widget spec plus DBC signal
 * metadata. Compose observes [Property] objects and current signal values; it
 * never touches the DBC or CAN directly.
 *
 * [requestSignal] is what the control writes (a command, as if from the IVI);
 * [feedbackSignal] is what it reads back (the ECU's reported state).
 */
data class Property(
    val id: String,
    val title: String,
    val widget: WidgetType,
    val requestSignal: String?,
    val feedbackSignal: String?,
    val min: Double,
    val max: Double,
    val step: Double,
    val unit: String,
    val options: List<EnumOption>,
    val snapZero: Boolean = false,
    /** Glyph token for the button face; null renders the title instead. */
    val icon: String? = null,
    /** Colour role for the face: "ok" | "warn" | "error"; null = neutral. */
    val accent: String? = null,
    /**
     * Signal that gates this control, resolved from the `gatedBy:` on the rule
     * that produces [feedbackSignal]. While it reads 0 the ECU forces this
     * widget's output to 0 whatever the UI says, so the UI says so too.
     * Derived, never written in the widget: one declaration, in `rules:`.
     */
    val gateSignal: String? = null,

    // --- MOMENTARY only. Empty/null for every other widget kind. ---
    /** DBC message fired once per gesture step (e.g. "SteeringWheelEvent"). */
    val eventMessage: String? = null,
    /** Signal values that identify this key, already resolved from VAL_ labels
     *  to numbers (e.g. ButtonCode = 20, ButtonSource = 1). */
    val eventSignals: Map<String, Double> = emptyMap(),
    /** Signal carrying the gesture phase (e.g. "ButtonAction"). */
    val phaseSignal: String? = null,
    /** Phase -> the value [phaseSignal] takes for it, resolved from VAL_. */
    val phaseValues: Map<GesturePhase, Double> = emptyMap(),
) {
    /** The signal whose live value the widget primarily displays. */
    val displaySignal: String? get() = feedbackSignal ?: requestSignal
}
