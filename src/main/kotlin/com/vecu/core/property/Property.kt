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
    ;

    companion object {
        fun from(name: String): WidgetType =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: LABEL
    }
}

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
) {
    /** The signal whose live value the widget primarily displays. */
    val displaySignal: String? get() = feedbackSignal ?: requestSignal
}
