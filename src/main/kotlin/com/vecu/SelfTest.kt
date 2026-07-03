package com.vecu

import com.vecu.config.AppConfig
import com.vecu.core.config.SimConfig
import com.vecu.core.ecu.VirtualEcu
import com.vecu.core.property.PropertyManager
import com.vecu.core.rule.RuleEngine
import com.vecu.dbc.DbcService

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

    val dbc = DbcService().apply { load(AppConfig.DBC_FILE) }
    val config = SimConfig.load(AppConfig.YAML_FILE)
    val properties = PropertyManager.build(config.widgets, dbc.schema)
    val ecu = VirtualEcu(dbc.schema, RuleEngine(config.rules), config.defaults)

    println("=== Virtual CAN ECU — self test ===")
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

    // --- Power OFF must gate A/C and fan to zero ---
    ecu.setSignal("HvacPowerOnReq", 0.0)
    repeat(2) { ecu.tick() }
    val off = ecu.buildTx("HvacStatus")
    check("Power off gates A/C", off["HvacAcOn"] == 0.0)
    check("Power off gates fan", off["HvacFanSpeed"] == 0.0)
    check("Power off gates RPM", off["HvacActualFanRpm"] == 0.0)

    dbc.close()
    println("=== ${if (failures == 0) "ALL PASSED" else "$failures FAILED"} ===")
    if (failures > 0) kotlin.system.exitProcess(1)
}
