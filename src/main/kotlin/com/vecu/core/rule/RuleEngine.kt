package com.vecu.core.rule

import com.vecu.core.config.RuleSpec
import kotlin.math.max
import kotlin.math.min

/**
 * Simulates ECU behaviour by applying the YAML rules, in order, to the signal
 * state on each tick. Rules are the *only* thing that defines how requests turn
 * into feedback — swapping the YAML changes the ECU with no code change.
 */
class RuleEngine(private val rules: List<RuleSpec>) {
    /** Applies every rule once to [state] (mutated in place). */
    fun apply(state: MutableMap<String, Double>) {
        for (r in rules) {
            when (r.type.lowercase()) {
                "mirror" -> mirror(r, state)
                "scale" -> scale(r, state)
                "ramp" -> ramp(r, state)
                "counter" -> counter(r, state)
            }
        }
    }

    private fun mirror(r: RuleSpec, state: MutableMap<String, Double>) {
        val from = r.from ?: return
        val to = r.to ?: return
        if (r.onlyWhen != null && !on(state, r.onlyWhen)) return
        var v = state[from] ?: 0.0
        if (r.gatedBy != null && !on(state, r.gatedBy)) v = 0.0
        state[to] = v
    }

    private fun scale(r: RuleSpec, state: MutableMap<String, Double>) {
        val from = r.from ?: return
        val to = r.to ?: return
        var v = (state[from] ?: 0.0) * (r.factor ?: 1.0)
        if (r.gatedBy != null && !on(state, r.gatedBy)) v = 0.0
        state[to] = v
    }

    private fun ramp(r: RuleSpec, state: MutableMap<String, Double>) {
        val to = r.to ?: return
        val toward = r.toward ?: return
        val target = state[toward] ?: 0.0
        val cur = state[to] ?: target
        val rate = r.rate ?: 1.0
        state[to] = when {
            cur < target -> min(cur + rate, target)
            cur > target -> max(cur - rate, target)
            else -> cur
        }
    }

    private fun counter(r: RuleSpec, state: MutableMap<String, Double>) {
        val to = r.to ?: return
        val wrap = r.wrap ?: return
        val step = r.rate ?: 1.0
        val cur = state[to] ?: 0.0
        state[to] = (cur + step) % wrap
    }

    private fun on(state: Map<String, Double>, signal: String): Boolean =
        (state[signal] ?: 0.0) >= 0.5
}
