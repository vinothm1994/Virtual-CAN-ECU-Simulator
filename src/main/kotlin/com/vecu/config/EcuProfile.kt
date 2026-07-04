package com.vecu.config

/**
 * One simulated ECU = a DBC (bus layout) + a YAML (widgets, rules, TX). Adding
 * an ECU is adding a profile here — no code change. Selected at runtime from the
 * toolbar.
 */
data class EcuProfile(
    val name: String,
    val dbc: String,
    val yaml: String,
)
