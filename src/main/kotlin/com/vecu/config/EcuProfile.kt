package com.vecu.config

/**
 * One simulated ECU = a DBC (bus layout) + a YAML (widgets, rules, TX). Adding
 * an ECU is adding a profile here — no code change. Selected at runtime from the
 * nav sidebar.
 */
data class EcuProfile(
    val name: String,
    val dbc: String,
    val yaml: String,
    /**
     * Glyph the sidebar draws for this ECU (token, not a class name — see the
     * table in ui/NavSidebar.kt). Unset or unrecognised falls back to a generic
     * ECU icon, so adding a profile still needs no code change.
     */
    val icon: String? = null,
)
