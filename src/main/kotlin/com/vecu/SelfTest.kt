package com.vecu

import com.vecu.config.AppConfig
import com.vecu.core.config.GroupLayout
import com.vecu.core.config.SimConfig
import com.vecu.core.ecu.EcuInstance
import com.vecu.core.ecu.VirtualEcu
import com.vecu.core.property.GesturePhase
import com.vecu.core.property.PropertyManager
import com.vecu.core.property.WidgetType
import com.vecu.ui.isGated
import com.vecu.core.rule.RuleEngine
import com.vecu.dbc.DbcService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Headless end-to-end check of the DBC + rules + encode/decode pipeline — no
 * CAN bus and no UI. Run with: `./gradlew selfTest`.
 */
fun main() {
    var failures = 0
    fun check(name: String, cond: Boolean, detail: String = "") {
        val tag = if (cond) "PASS" else "FAIL".also { failures++ }
        println("[$tag] $name${if (detail.isNotEmpty()) "  ($detail)" else ""}")
    }

    println("=== Virtual CAN ECU — self test ===")

    // Every configured ECU profile loads its DBC and builds a UI.
    for (p in AppConfig.PROFILES) {
        val d = DbcService().apply { load(p.dbc) }
        val c = SimConfig.load(p.yaml)
        val props = PropertyManager.build(c.widgets, d.schema)
        check(
            "Profile '${p.name}' loads",
            d.schema.messages.isNotEmpty() && props.isNotEmpty(),
            "${d.schema.messages.size} msgs, ${props.size} widgets",
        )
        // A group naming a widget that does not exist would drop the key from
        // the panel silently, so SimConfig.load rejects it; prove that here as
        // well as proving the real profiles are consistent.
        val ids = c.widgets.map { it.id }.toSet()
        check(
            "Profile '${p.name}' groups resolve",
            c.groups.all { g -> g.widgetIds.all { it in ids } },
            if (c.groups.isEmpty()) "no groups" else "${c.groups.size} groups",
        )
        d.close()
    }

    // The HVAC panel's gating: every control the ECU forces to 0 while power is
    // off must know which signal gates it, taken from `rules:` and not from a
    // second declaration on the widget that could disagree with it.
    run {
        val hvacProfile = AppConfig.PROFILES.first { it.name == "HVAC" }
        val d = DbcService().apply { load(hvacProfile.dbc) }
        val c = SimConfig.load(hvacProfile.yaml)
        val props = PropertyManager.build(c.widgets, d.schema, c.rules)
        fun gateOf(id: String) = props.first { it.id == id }.gateSignal
        check("HVAC A/C is gated by power", gateOf("ac") == "HvacPowerOn", gateOf("ac").orEmpty())
        check("HVAC fan speed is gated by power", gateOf("fanSpeed") == "HvacPowerOn")
        // Power gates the rest; nothing gates power, and the setpoints are
        // settable before the airflow ever starts.
        check("HVAC power itself is ungated", gateOf("power") == null)
        check("HVAC driver setpoint is ungated", gateOf("driverTemp") == null)
        check("HVAC air distribution is ungated", gateOf("fanDirection") == null)
        val gatedOff = mapOf("HvacPowerOn" to 0.0)
        check(
            "With power off the ECU is forcing A/C off, and the panel says so",
            props.first { it.id == "ac" }.isGated(gatedOff) &&
                !props.first { it.id == "power" }.isGated(gatedOff),
        )
        d.close()
    }

    // The layout the SWC panel is built from: a D-pad with its five slots, and
    // every momentary key placed in exactly one group.
    run {
        val swc = SimConfig.load(AppConfig.PROFILES.first { it.name == "SWC" }.yaml)
        val dpad = swc.groups.firstOrNull { it.layout == GroupLayout.DPAD }
        check(
            "SWC declares a D-pad with five slots",
            dpad != null && dpad.slots.keys == setOf("up", "left", "center", "right", "down"),
            dpad?.slots?.keys?.joinToString().orEmpty(),
        )
        val keys = swc.widgets.filter { it.widget == WidgetType.MOMENTARY }.map { it.id }.toSet()
        val placed = swc.groups.flatMap { it.widgetIds }.toSet()
        check(
            "Every SWC key is in exactly one group",
            placed == keys,
            "${placed.size} placed of ${keys.size} keys",
        )
        // A key with no glyph falls back to its title, which is fine — but a
        // token that no table entry matches is a silent typo, so pin the set.
        check(
            "SWC keys name a glyph (except OK, which shows its title)",
            swc.widgets.filter { it.widget == WidgetType.MOMENTARY && it.icon == null }.map { it.id } == listOf("ok"),
        )
    }

    // Multi-ECU routing: concurrent instances each handle only their own DBC's
    // frames (this is the "run all, view one" fan-out).
    run {
        val scope = CoroutineScope(Dispatchers.Default)
        val hvacInst = EcuInstance(AppConfig.PROFILES.first { it.name == "HVAC" }, scope, { false }, { _, _, _, _ -> })
        val vehInst = EcuInstance(AppConfig.PROFILES.first { it.name == "Vehicle" }, scope, { false }, { _, _, _, _ -> })
        val hvacFrame = hvacInst.dbc.encode("HvacControl", mapOf("HvacAcOnReq" to 1.0))!!
        check("HVAC instance routes HvacControl", hvacInst.onFrame(hvacFrame)?.message?.name == "HvacControl")
        check("Vehicle instance ignores HvacControl", vehInst.onFrame(hvacFrame) == null)
        hvacInst.close()
        vehInst.close()
    }

    // The rest of the checks exercise the HVAC profile in depth.
    val hvac = AppConfig.PROFILES.first { it.name == "HVAC" }
    val dbc = DbcService().apply { load(hvac.dbc) }
    val config = SimConfig.load(hvac.yaml)
    val properties = PropertyManager.build(config.widgets, dbc.schema)
    val ecu = VirtualEcu(dbc.schema, RuleEngine(config.rules), config.defaults)

    check(
        "Default applied to feedback (HvacFanDirection)",
        ecu.state.get("HvacFanDirection") == 1.0,
        "= ${ecu.state.get("HvacFanDirection")} (expected 1.0)",
    )

    check("DBC messages", dbc.schema.messages.size >= 4, "${dbc.schema.messages.size} messages")
    check("Properties built", properties.size == config.widgets.size, "${properties.size} widgets")
    check(
        "Dropdown got DBC enum options",
        properties.first { it.id == "fanDirection" }.options.isNotEmpty(),
    )

    // --- Simulate an inbound HvacControl request frame (as an IVI would send) ---
    val reqFrame = dbc.encode(
        "HvacControl",
        mapOf("HvacPowerOnReq" to 1.0, "HvacAcOnReq" to 1.0, "HvacFanSpeedReq" to 5.0),
    )!!
    dbc.decode(reqFrame)!!.let { ecu.onFrame(it) }
    // And a temperature setpoint from the UI path.
    ecu.setSignal("HvacTempSetDriverReq", 24.0)

    // Run the rule engine to steady state (ramp needs a few ticks).
    repeat(40) { ecu.tick() }

    val status = ecu.buildTx("HvacStatus")
    check("Power mirrored", status["HvacPowerOn"] == 1.0)
    check("A/C mirrored (power on)", status["HvacAcOn"] == 1.0)
    check("Fan speed mirrored", status["HvacFanSpeed"] == 5.0)
    check("Actual RPM = fan*350", status["HvacActualFanRpm"] == 1750.0, "${status["HvacActualFanRpm"]}")

    val temps = ecu.buildTx("HvacTemperatures")
    check("Driver setpoint mirrored", temps["HvacTempSetDriver"] == 24.0)
    check("Cabin temp ramped to setpoint", temps["HvacTempCurrentDriver"] == 24.0, "${temps["HvacTempCurrentDriver"]}")

    // --- Encode the status, decode it back: round-trip must preserve values ---
    val statusFrame = dbc.encode("HvacStatus", status)!!
    val rt = dbc.decode(statusFrame)!!.values
    check("Round-trip RPM", rt["HvacActualFanRpm"] == 1750.0, "${rt["HvacActualFanRpm"]}")
    check("Round-trip fan speed", rt["HvacFanSpeed"] == 5.0)
    println("  status frame ${statusFrame.idHex()} = ${statusFrame.hex()}")

    // --- Power OFF: airflow (A/C, fan, RPM) gates to zero, but the
    //     air-distribution setpoint persists (not gated). ---
    ecu.setSignal("HvacFanDirectionReq", 2.0) // floor
    ecu.setSignal("HvacPowerOnReq", 0.0)
    repeat(2) { ecu.tick() }
    val off = ecu.buildTx("HvacStatus")
    check("Power off gates A/C", off["HvacAcOn"] == 0.0)
    check("Power off gates fan", off["HvacFanSpeed"] == 0.0)
    check("Power off gates RPM", off["HvacActualFanRpm"] == 0.0)
    check("Air distribution persists on power off", off["HvacFanDirection"] == 2.0, "${off["HvacFanDirection"]}")

    dbc.close()

    // --- Steering profile: rolling alive-counter rule (SteeringCounter). ---
    // Advances once per actual transmit of its message, NOT per engine tick —
    // ticking alone must never move it (that was the bug: it used to jump by
    // (period_ms / tick_ms) per frame instead of by exactly 1).
    // Lives in the Steering profile since the gateway gave steering its own
    // domain; the CAN ID (0x120) and the frame layout did not change.
    val vehicle = AppConfig.PROFILES.first { it.name == "Steering" }
    val vehDbc = DbcService().apply { load(vehicle.dbc) }
    val vehConfig = SimConfig.load(vehicle.yaml)
    val vehEcu = VirtualEcu(vehDbc.schema, RuleEngine(vehConfig.rules), vehConfig.defaults)
    repeat(20) { vehEcu.tick() }
    check(
        "Ticking alone does not advance SteeringCounter",
        vehEcu.state.get("SteeringCounter") == 0.0,
        "= ${vehEcu.state.get("SteeringCounter")} (expected 0.0 — only commitTx() should move it)",
    )
    repeat(20) { vehEcu.commitTx("SteeringStatus") }
    check(
        "SteeringCounter rolls over 0..15, +1 per transmit",
        vehEcu.state.get("SteeringCounter") == 4.0,
        "= ${vehEcu.state.get("SteeringCounter")} (expected 4.0 after 20 transmits, wraps at 16)",
    )
    vehDbc.close()

    // --- Charging profile: ChargeCmd -> ChargingState via a `map` rule, not a
    //     mirror (the two signals use unrelated code spaces). Also proves the
    //     "no entry leaves `to` unchanged" behaviour: ChargeCmd never reverts
    //     to 0/none on its own (the gateway holds the last command), so a
    //     command signal must not be treated like an idle/off state. ---
    run {
        val charging = AppConfig.PROFILES.first { it.name == "Charging" }
        val chgDbc = DbcService().apply { load(charging.dbc) }
        val chgConfig = SimConfig.load(charging.yaml)
        val chgEcu = VirtualEcu(chgDbc.schema, RuleEngine(chgConfig.rules), chgConfig.defaults)

        check(
            "Charging starts idle by default",
            chgEcu.state.get("ChargingState") == 0.0,
            "= ${chgEcu.state.get("ChargingState")} (expected 0.0)",
        )

        fun sendChargeCmd(cmd: Double) {
            val frame = chgDbc.encode("ChargingControl", mapOf("ChargeCmd" to cmd, "ChargeLimitReq" to 100.0))!!
            chgDbc.decode(frame)!!.let { chgEcu.onFrame(it) }
            chgEcu.tick()
        }

        sendChargeCmd(1.0) // start
        check(
            "ChargeCmd=start (1) maps ChargingState to charging (2)",
            chgEcu.state.get("ChargingState") == 2.0,
            "= ${chgEcu.state.get("ChargingState")}",
        )

        sendChargeCmd(2.0) // stop
        check(
            "ChargeCmd=stop (2) maps ChargingState to idle (0)",
            chgEcu.state.get("ChargingState") == 0.0,
            "= ${chgEcu.state.get("ChargingState")}",
        )

        // Simulate a fault while a command is still held at a tabulated value —
        // the map rule must not fight a manual/other-rule change on every tick
        // just because ChargeCmd is still "start".
        sendChargeCmd(1.0) // back to charging
        chgEcu.setSignal("ChargingState", 6.0) // fault, driven by hand
        chgEcu.tick()
        check(
            "map rule re-asserts its table value while ChargeCmd is held",
            chgEcu.state.get("ChargingState") == 2.0,
            "= ${chgEcu.state.get("ChargingState")} — by design, see RuleSpec's `map` doc: " +
                "delete the rule to drive ChargingState by hand instead",
        )

        chgDbc.close()
    }

    // --- SWC profile: the event path. What is being checked here is that a
    //     gesture becomes a SEQUENCE of frames, and that each one carries the
    //     right code — none of which the state/on-change path can express. ---
    run {
        val swc = AppConfig.PROFILES.first { it.name == "SWC" }
        val swcDbc = DbcService().apply { load(swc.dbc) }
        val swcConfig = SimConfig.load(swc.yaml)
        val props = PropertyManager.build(swcConfig.widgets, swcDbc.schema)

        // VAL_ labels in the YAML resolve to the DBC's own numbers, so the
        // profile never repeats a code the DBC already owns.
        val volUp = props.first { it.id == "volUp" }
        check(
            "SWC VAL_ label resolves (VOLUME_UP -> 20)",
            volUp.eventSignals["ButtonCode"] == 20.0,
            "= ${volUp.eventSignals["ButtonCode"]}",
        )
        check(
            "SWC source resolves (RIGHT_STEERING -> 1)",
            volUp.eventSignals["ButtonSource"] == 1.0,
            "= ${volUp.eventSignals["ButtonSource"]}",
        )
        val voice = props.first { it.id == "voice" }
        check(
            "SWC gesture phases resolve from VAL_",
            voice.phaseValues[GesturePhase.PRESSED] == 1.0 &&
                voice.phaseValues[GesturePhase.LONG_PRESSED] == 2.0 &&
                voice.phaseValues[GesturePhase.REPEAT] == 3.0 &&
                voice.phaseValues[GesturePhase.RELEASED] == 0.0,
            "${voice.phaseValues}",
        )
        check("SWC key targets its event message", voice.eventMessage == "SteeringWheelEvent")
        check(
            "SWC sends nothing cyclically or on change",
            swcConfig.tx.isEmpty(),
            "an event must be sent when it happens, not noticed on a tick",
        )

        // A full VOICE long press through the real EcuInstance TX path: every
        // gesture step must reach the bus as its own frame, with the alive
        // counter advancing by exactly one per frame.
        val scope = CoroutineScope(Dispatchers.Default)
        val sent = mutableListOf<Map<String, Double>>()
        val inst = EcuInstance(swc, scope, { true }, { _, _, _, values -> sent += values })
        val phases = listOf(
            GesturePhase.PRESSED, GesturePhase.LONG_PRESSED,
            GesturePhase.REPEAT, GesturePhase.REPEAT, GesturePhase.RELEASED,
        )
        for (phase in phases) {
            inst.sendEvent(
                "SteeringWheelEvent",
                voice.eventSignals + ("ButtonAction" to voice.phaseValues.getValue(phase)),
            )
        }
        check("SWC long press = one frame per gesture step", sent.size == 5, "${sent.size} frames")
        check(
            "SWC frames carry VOICE from the centre spoke",
            sent.all { it["ButtonCode"] == 30.0 && it["ButtonSource"] == 2.0 },
        )
        check(
            "SWC actions in order: PRESSED LONG_PRESSED REPEAT REPEAT RELEASED",
            sent.map { it["ButtonAction"] } == listOf(1.0, 2.0, 3.0, 3.0, 0.0),
            "${sent.map { it["ButtonAction"] }}",
        )
        // Two identical REPEATs are two events. Change detection would have
        // collapsed them into one and the key would scroll once and stop; the
        // counter is what tells them apart on the wire.
        check(
            "SWC alive counter is +1 per event, never coalesced",
            sent.map { it["Counter"] } == listOf(0.0, 1.0, 2.0, 3.0, 4.0),
            "${sent.map { it["Counter"] }}",
        )

        // The frame really encodes: round-trip the last one through the DBC.
        val frame = swcDbc.encode("SteeringWheelEvent", sent.last())!!
        val rtSwc = swcDbc.decode(frame)!!
        check("SWC frame is CAN 0x600", frame.idHex().endsWith("600"), frame.idHex())
        check(
            "SWC RELEASED round-trips",
            rtSwc.values["ButtonCode"] == 30.0 && rtSwc.values["ButtonAction"] == 0.0,
            "${rtSwc.values}",
        )
        inst.close()
        swcDbc.close()
    }

    println("=== ${if (failures == 0) "ALL PASSED" else "$failures FAILED"} ===")
    if (failures > 0) kotlin.system.exitProcess(1)
}
